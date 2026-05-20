/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import static com.sap.cds.reflect.CdsAnnotatable.byAnnotation;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsBaseType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsSimpleType;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.DraftUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
class FioriRecommendationHandler implements EventHandler {

  private final AICoreService aiCoreService;
  private final RecommendationClientResolver clientResolver;
  private final Set<String> entitiesWithoutPredictions = ConcurrentHashMap.newKeySet();
  private static final Logger logger = LoggerFactory.getLogger(FioriRecommendationHandler.class);
  private static final String VALUE_LIST_ANNOTATION = "@Common.ValueList";
  private static final String VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION =
      "@Common.ValueListWithFixedValues";
  private static final int DEFAULT_CONTEXT_ROW_LIMIT = 2000;
  private static final String SYNTHETIC_KEY_COLUMN = "SAP_RECOMMENDATIONS_ID";
  private static final Set<CdsBaseType> SUPPORTED_CONTEXT_TYPES =
      EnumSet.of(
          CdsBaseType.STRING,
          CdsBaseType.LARGE_STRING,
          CdsBaseType.UUID,
          CdsBaseType.BOOLEAN,
          CdsBaseType.INTEGER,
          CdsBaseType.UINT8,
          CdsBaseType.INT16,
          CdsBaseType.INT32,
          CdsBaseType.INT64,
          CdsBaseType.INTEGER64,
          CdsBaseType.DECIMAL,
          CdsBaseType.DOUBLE,
          CdsBaseType.DATE,
          CdsBaseType.TIME,
          CdsBaseType.DATETIME,
          CdsBaseType.TIMESTAMP,
          CdsBaseType.HANA_SMALLINT,
          CdsBaseType.HANA_TINYINT,
          CdsBaseType.HANA_SMALLDECIMAL,
          CdsBaseType.HANA_REAL,
          CdsBaseType.HANA_CHAR,
          CdsBaseType.HANA_NCHAR,
          CdsBaseType.HANA_VARCHAR,
          CdsBaseType.HANA_CLOB);

  FioriRecommendationHandler(
      AICoreService aiCoreService, RecommendationClientResolver clientResolver) {
    this.aiCoreService = aiCoreService;
    this.clientResolver = clientResolver;
  }

  @After(entity = "*")
  public void afterRead(CdsReadEventContext context, List<CdsData> dataList) {
    CdsStructuredType target = context.getTarget();
    if (target == null) {
      return;
    }
    String entityName = target.getQualifiedName();
    if (entitiesWithoutPredictions.contains(entityName)) {
      return;
    }

    if (dataList.size() != 1) {
      return;
    }

    CdsData row = dataList.get(0);

    if (!DraftUtils.isDraftEnabled(target)) {
      return;
    }

    if (!Boolean.FALSE.equals(row.get(Drafts.IS_ACTIVE_ENTITY))) {
      return;
    }

    CdsStructuredType rowType = context.getResult().rowType();
    if (rowType == null) {
      rowType = target;
    }
    List<String> predictionElementNames =
        rowType
            .elements()
            .filter(
                byAnnotation(VALUE_LIST_ANNOTATION)
                    .or(byAnnotation(VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION)))
            .filter(e -> !e.getType().isAssociation())
            .map(CdsElement::getName)
            .toList();
    if (predictionElementNames.isEmpty()) {
      entitiesWithoutPredictions.add(entityName);
      return;
    }

    List<String> contextColumns =
        rowType
            .concreteNonAssociationElements()
            .filter(e -> e.getType().isSimple())
            .filter(
                e ->
                    SUPPORTED_CONTEXT_TYPES.contains(e.getType().as(CdsSimpleType.class).getType()))
            .filter(e -> !Drafts.ELEMENTS.contains(e.getName()))
            .map(CdsElement::getName)
            .toList();
    if (contextColumns.isEmpty()) {
      logger.debug("No suitable context columns found, skipping predictions.");
      return;
    }

    List<String> keyNames = target.keyElements().map(CdsElement::getName).toList();
    boolean syntheticKeyNeeded =
        keyNames.size() > 1 || (keyNames.size() == 1 && !"ID".equals(keyNames.get(0)));
    String indexColumn =
        syntheticKeyNeeded ? SYNTHETIC_KEY_COLUMN : keyNames.stream().findFirst().orElse("ID");

    List<String> selectColumns = new ArrayList<>(contextColumns);
    for (String key : keyNames) {
      if (!selectColumns.contains(key)) {
        selectColumns.add(key);
      }
    }
    int limit =
        context
            .getCdsRuntime()
            .getEnvironment()
            .getProperty(
                "cds.requires.recommendations.contextRowLimit",
                Integer.class,
                DEFAULT_CONTEXT_ROW_LIMIT);
    var select =
        Select.from(target.getQualifiedName())
            .columns(selectColumns.toArray(String[]::new))
            .where(
                predictionElementNames.stream()
                    .map(col -> CQL.get(col).isNotNull())
                    .collect(CQL.withAnd()))
            .limit(limit);
    target
        .concreteNonAssociationElements()
        .filter(byAnnotation("cds.on.update"))
        .map(CdsElement::getName)
        .findFirst()
        .or(() -> target.keyElements().map(CdsElement::getName).findFirst())
        .ifPresent(col -> select.orderBy(CQL.get(col).desc()));

    PersistenceService db =
        context
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    List<CdsData> contextRows = new ArrayList<>(db.run(select).list());
    if (contextRows.size() < 2) {
      logger.debug("Not enough context rows (minimum 2), skipping predictions.");
      return;
    }

    CdsData predictRow = buildPredictRow(row, predictionElementNames);
    if (predictRow == null) {
      return;
    }

    List<CdsData> allRows = new ArrayList<>();
    if (syntheticKeyNeeded) {
      for (CdsData contextRow : contextRows) {
        contextRow.put(SYNTHETIC_KEY_COLUMN, computeSyntheticKey(contextRow, keyNames));
        allRows.add(contextRow);
      }
      predictRow.put(SYNTHETIC_KEY_COLUMN, computeSyntheticKey(row, keyNames));
    } else {
      allRows.addAll(contextRows);
    }
    allRows.add(predictRow);

    String tenantId = context.getUserInfo().getTenant();
    RecommendationClient client = clientResolver.resolve(aiCoreService, tenantId);
    List<CdsData> predictions = client.predict(allRows, predictionElementNames, indexColumn);

    if (predictions.isEmpty()) {
      logger.warn("No predictions returned from AI client.");
      return;
    }
    if (predictions.size() > 1) {
      logger.warn("Multiple predictions returned from AI client, but only one was expected.");
      return;
    }

    Map<String, Object> recommendations =
        buildRecommendations(db, predictions.get(0), predictionElementNames, context, rowType);
    row.put("SAP_Recommendations", recommendations);
  }

  private CdsData buildPredictRow(CdsData row, List<String> predictionElementNames) {
    if (predictionElementNames.stream().noneMatch(c -> row.get(c) == null)) {
      logger.debug("Current row already has values for all prediction columns, skipping.");
      return null;
    }
    Map<String, Object> predictRow = new HashMap<>(row);
    Drafts.ELEMENTS.forEach(predictRow::remove);
    for (String col : predictionElementNames) {
      predictRow.putIfAbsent(col, "[PREDICT]");
    }
    return CdsData.create(predictRow);
  }

  private String computeSyntheticKey(Map<String, Object> row, List<String> keyNames) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keyNames.size(); i++) {
      if (i > 0) {
        sb.append('\0');
      }
      sb.append(keyNames.get(i));
      sb.append('\0');
      Object value = row.get(keyNames.get(i));
      if (value != null) {
        sb.append(value);
      }
    }
    return sb.toString();
  }

  private Map<String, Object> buildRecommendations(
      PersistenceService db,
      CdsData prediction,
      List<String> predictionElementNames,
      CdsReadEventContext context,
      CdsStructuredType rowType) {
    Map<String, String> textPaths = resolveTextPaths(predictionElementNames, context);

    Map<String, Object> parsedValues = new HashMap<>();
    for (String col : predictionElementNames) {
      Object obj = prediction.get(col);
      if (!(obj instanceof List<?> list)
          || list.isEmpty()
          || !(list.get(0) instanceof Map<?, ?> map)) {
        continue;
      }
      CdsBaseType baseType =
          rowType
              .findElement(col)
              .filter(e -> e.getType().isSimple())
              .map(e -> e.getType().as(CdsSimpleType.class).getType())
              .orElse(CdsBaseType.STRING);
      parsedValues.put(col, parseValue(map.get("prediction"), baseType));
    }

    Map<String, String> descriptions =
        resolveDescriptionsBatch(db, parsedValues, textPaths, context);

    Map<String, Object> recommendations = new HashMap<>();
    for (Map.Entry<String, Object> entry : parsedValues.entrySet()) {
      String col = entry.getKey();
      Object recommendedValue = entry.getValue();
      Map<String, Object> values = new HashMap<>();
      values.put("RecommendedFieldValue", recommendedValue);
      values.put("RecommendedFieldDescription", descriptions.getOrDefault(col, ""));
      values.put("RecommendedFieldScoreValue", 0.5);
      values.put("RecommendedFieldIsSuggestion", true);
      recommendations.put(col, List.of(values));
    }
    return recommendations;
  }

  private Map<String, String> resolveTextPaths(
      List<String> predictionElementNames, CdsReadEventContext context) {
    CdsStructuredType target = context.getTarget();
    Map<String, String> fkToAssociation = buildFkToAssociationMap(target);
    Map<String, String> textPaths = new HashMap<>();
    for (String col : predictionElementNames) {
      Optional<String> path;
      String assocName = fkToAssociation.get(col);
      if (assocName != null) {
        path = getTextPath(context, assocName);
        if (path.isEmpty()) {
          path = getTextPath(context, col);
        }
      } else {
        path = getTextPath(context, col);
      }
      path.ifPresent(p -> textPaths.put(col, p));
    }
    return textPaths;
  }

  private Map<String, String> buildFkToAssociationMap(CdsStructuredType target) {
    Map<String, String> map = new HashMap<>();
    target
        .associations()
        .forEach(
            assocElement -> {
              CdsAssociationType assocType = assocElement.getType().as(CdsAssociationType.class);
              String assocName = assocElement.getName();
              assocType
                  .refs()
                  .forEach(ref -> map.put(assocName + "_" + ref.lastSegment(), assocName));
            });
    return map;
  }

  private Object parseValue(Object value, CdsBaseType baseType) {
    if (value == null) {
      return null;
    }
    String s = value.toString();
    try {
      return switch (baseType) {
        case INTEGER, INT16, INT32, UINT8, HANA_SMALLINT, HANA_TINYINT -> Integer.valueOf(s);
        case INT64, INTEGER64 -> Long.valueOf(s);
        case DECIMAL, DECIMAL_FLOAT, HANA_SMALLDECIMAL -> new BigDecimal(s);
        case DOUBLE, HANA_REAL -> Double.valueOf(s);
        case BOOLEAN -> Boolean.valueOf(s);
        default -> s;
      };
    } catch (NumberFormatException e) {
      return s;
    }
  }

  private Map<String, String> resolveDescriptionsBatch(
      PersistenceService db,
      Map<String, Object> parsedValues,
      Map<String, String> textPaths,
      CdsReadEventContext context) {
    Map<String, String> descriptions = new HashMap<>();
    if (textPaths.isEmpty()) {
      return descriptions;
    }
    String entity = context.getTarget().getQualifiedName();
    for (Map.Entry<String, Object> entry : parsedValues.entrySet()) {
      String col = entry.getKey();
      String path = textPaths.get(col);
      if (path == null) {
        continue;
      }
      String[] parts = path.split("\\.");
      if (parts.length != 2) {
        logger.debug(
            "Text path {} for column {} is not in expected format 'association.textField'.",
            path,
            col);
        continue;
      }
      Result r =
          db.run(
              Select.from(entity)
                  .columns(b -> b.to(parts[0]).get(parts[1]).as("desc"))
                  .where(CQL.get(col).eq(entry.getValue()))
                  .limit(1));
      r.first()
          .map(row -> row.get("desc"))
          .filter(Objects::nonNull)
          .ifPresent(d -> descriptions.put(col, d.toString()));
    }
    return descriptions;
  }

  private Optional<String> getTextPath(CdsReadEventContext context, String columnName) {
    return context
        .getTarget()
        .findElement(columnName)
        .flatMap(e -> e.findAnnotation("@Common.Text"))
        .flatMap(
            a -> {
              Object val = a.getValue();
              if (val instanceof String s) return Optional.of(s);
              if (val instanceof Map<?, ?> m && m.get("=") != null)
                return Optional.of(m.get("=").toString());
              return Optional.empty();
            });
  }
}
