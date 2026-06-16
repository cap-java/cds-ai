/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import static com.sap.cds.reflect.CdsAnnotatable.byAnnotation;

import com.sap.cds.CdsData;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsBaseType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsSimpleType;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.draft.Drafts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the context data needed for prediction: determines which elements to predict, which
 * columns provide context and builds the context query. This class is cds-model aware, but does not
 * know about which client will be used for the predictions.
 */
class RecommendationContextBuilder {

  private static final String VALUE_LIST_ANNOTATION = "@Common.ValueList";
  private static final String VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION =
      "@Common.ValueListWithFixedValues";
  private static final String COMPUTED_ANNOTATION = "@Core.Computed";
  private static final String READONLY_ANNOTATION = "@readonly";
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

  private final CdsStructuredType target;
  private final CdsStructuredType rowType;
  private final int contextRowLimit;
  private final List<String> predictionElementNames;
  private final List<String> contextColumns;
  private final List<String> keyNames;

  RecommendationContextBuilder(CdsStructuredType target, CdsStructuredType rowType, int limit) {
    this.target = target;
    this.rowType = rowType;
    this.contextRowLimit = limit;
    this.predictionElementNames = computePredictionElements();
    this.contextColumns = computeContextColumns();
    this.keyNames = target.keyElements().map(CdsElement::getName).toList();
  }

  List<String> predictionElementNames() {
    return predictionElementNames;
  }

  List<String> contextColumns() {
    return contextColumns;
  }

  List<String> keyNames() {
    return keyNames;
  }

  CqnSelect buildContextQuery() {
    List<String> selectColumns = new ArrayList<>(contextColumns);
    for (String key : keyNames) {
      if (!selectColumns.contains(key)) {
        selectColumns.add(key);
      }
    }
    var select =
        Select.from(target.getQualifiedName())
            .columns(selectColumns.toArray(String[]::new))
            .where(
                predictionElementNames.stream()
                    .map(col -> CQL.get(col).isNotNull())
                    .collect(CQL.withAnd()))
            .limit(contextRowLimit);
    target
        .concreteNonAssociationElements()
        .filter(byAnnotation("cds.on.update"))
        .map(CdsElement::getName)
        .findFirst()
        .or(() -> target.keyElements().map(CdsElement::getName).findFirst())
        .ifPresent(col -> select.orderBy(CQL.get(col).desc()));
    return select;
  }

  // Builds the predict row from only the allowed columns (same set used in buildContextQuery),
  // so draft, computed, and readonly fields are excluded by construction rather than explicit
  // removal.
  CdsData buildPredictRow(CdsData row) {
    if (predictionElementNames.stream().noneMatch(c -> row.get(c) == null)) {
      return null;
    }
    Set<String> allowed = new HashSet<>(contextColumns);
    allowed.addAll(keyNames);
    Map<String, Object> predictRow = new HashMap<>();
    allowed.forEach(
        col -> {
          if (row.containsKey(col)) predictRow.put(col, row.get(col));
        });
    return CdsData.create(predictRow);
  }

  private List<String> computePredictionElements() {
    return rowType
        .elements()
        .filter(
            byAnnotation(VALUE_LIST_ANNOTATION)
                .or(byAnnotation(VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION)))
        .filter(e -> !e.getType().isAssociation())
        .map(CdsElement::getName)
        .toList();
  }

  private List<String> computeContextColumns() {
    return rowType
        .concreteNonAssociationElements()
        .filter(e -> e.getType().isSimple())
        .filter(
            e -> SUPPORTED_CONTEXT_TYPES.contains(e.getType().as(CdsSimpleType.class).getType()))
        .filter(e -> !Drafts.ELEMENTS.contains(e.getName()))
        .filter(byAnnotation(COMPUTED_ANNOTATION).negate())
        .filter(byAnnotation(READONLY_ANNOTATION).negate())
        .map(CdsElement::getName)
        .toList();
  }
}
