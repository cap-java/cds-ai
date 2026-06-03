/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsBaseType;
import com.sap.cds.reflect.CdsSimpleType;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses AI prediction responses and assembles them into the SAP_Recommendations structure expected
 * by Fiori UIs. Handles type coercion, text path resolution, and description lookups.
 */
class RecommendationResultParser {

  private static final Logger logger = LoggerFactory.getLogger(RecommendationResultParser.class);

  Map<String, Object> buildRecommendations(
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
      // The RPT-1 prediction response does not currently expose a per-prediction confidence
      // score in a stable form. We emit a constant placeholder so Fiori Elements still renders
      // the suggestion with a non-null score; replace with the real probability once the AI SDK
      // surfaces it (see SAP-RPT-1 model docs for `prediction_proba`).
      values.put("RecommendedFieldScoreValue", 0.5);
      values.put("RecommendedFieldIsSuggestion", true);
      recommendations.put(col, List.of(values));
    }
    return recommendations;
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
