/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai;

import com.sap.cds.feature.ai.client.AIClient;
import com.sap.cds.feature.ai.client.AICoreClient;
import com.sap.cds.feature.ai.client.MockAIClient;
import com.sap.cds.feature.ai.client.setup.AICoreSetup;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler that provides Fiori AI recommendations for entities with value help. This implementation
 * adds SAP_Recommendations to draft-enabled entities.
 */
@ServiceName(value = "*", type = ApplicationService.class)
public class FioriRecommendationHandler implements EventHandler {

  private final AIClient aiClient;
  private final PersistenceService db;
  private static final Logger logger = LoggerFactory.getLogger(FioriRecommendationHandler.class);
  private static final String VALUE_LIST_ANNOTATION = "@Common.ValueList";
  private static final String VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION =
      "@Common.ValueListWithFixedValues";
  private static final String ASSOCIATION = "cds.Association";
  private static final String LARGE_BINARY = "cds.LargeBinary";
  private static final String VECTOR = "cds.Vector";
  private static final Set<String> DRAFT_FIELDS =
      Set.of(
          "HasActiveEntity",
          "HasDraftEntity",
          "IsActiveEntity",
          "DraftAdministrativeData_DraftUUID");

  public FioriRecommendationHandler(Optional<AICoreSetup> setupOpt, PersistenceService db) {
    this.db = db;
    if (setupOpt.isPresent()) {
      logger.info("Registered AI Service Handler with AI Core setup.");
      this.aiClient = new AICoreClient(setupOpt.get());
    } else {
      logger.warn(
          "No service binding to AI Service found, using mock implementation for Fiori recommendations!");
      this.aiClient = new MockAIClient();
    }
  }

  /*
   * After read event handler for Fiori AI recommendations:
   * - Checks it's a draft read (IsActiveEntity = false), otherwise it's a read-only view and no
   *   recommendations are needed.
   * - Finds all elements with value help annotations, these are the columns we want to predict
   * - Fetches up to 2018 existing rows from DB as training context
   * - Appends the current row with [PREDICT] as a placeholder
   * - Sends the whole batch to the AI client → gets back predictions per row per column
   * - Resolves human-readable descriptions for predicted IDs (e.g. genre.name for a predicted genre_ID)
   * - Writes predictions into row.put("SAP_Recommendations", {...}) on the live result object,
   */
  @After(event = CqnService.EVENT_READ, entity = "*")
  public void afterRead(CdsReadEventContext context) {
    List<Map> rows = context.getResult().listOf(Map.class);

    if (rows.isEmpty()) {
      logger.debug("No result found, skipping predictions.");
      return;
    }

    if (rows.size() > 1) {
      logger.debug(
          "Multiple entites requested, recommendations are only available for single entity drafts.");
      return;
    }

    Map row = rows.get(0);

    // Only fetch predictions when editing a draft (IsActiveEntity = false)
    Object isActiveEntity = row.get("IsActiveEntity");
    if (!Boolean.FALSE.equals(isActiveEntity)) {
      logger.debug(
          "Not editing a draft (IsActiveEntity={}), skipping predictions.", isActiveEntity);
      return;
    }

    // Get all fields that have value help annotations, if there are none, then there are no
    // predictions to fetch
    // Also Filter out association elements as they are navigation properties and not actual
    // columns.
    // When @Common.ValueListWithFixedValues is set on an association, CDS
    // propagates the annotation to both the association element and its generated scalar
    // foreign-key
    // field. We only want the scalar fields for prediction,
    // as those map to real DB columns and carry the actual values the AI model predicts.
    List<String> predictionColumns =
        context
            .getTarget()
            .elements()
            .filter(
                e -> {
                  return e.findAnnotation(VALUE_LIST_ANNOTATION).isPresent()
                      || e.findAnnotation(VALUE_LIST_WITH_FIXED_VALUES_ANNOTATION).isPresent();
                })
            .filter(e -> !e.getType().getQualifiedName().equals(ASSOCIATION))
            .map(e -> e.getName())
            .collect(Collectors.toList());
    if (predictionColumns.isEmpty()) {
      logger.debug("No prediction columns found, skipping predictions.");
      return;
    }

    logger.info(
        "Will fetch predictions for entity: "
            + context.getTarget().getName()
            + " and columns: "
            + String.join(", ", predictionColumns));

    // Columns to include in the context SELECT: exclude BLOBs, Vectors, and draft-only fields
    // (mirrors the Node.js filter: type !== 'cds.LargeBinary' && type !== 'cds.Vector')
    List<String> contextColumns =
        context
            .getTarget()
            .elements()
            .filter(e -> !e.getType().getQualifiedName().equals(LARGE_BINARY))
            .filter(e -> !e.getType().getQualifiedName().equals(VECTOR))
            .filter(e -> !DRAFT_FIELDS.contains(e.getName()))
            .filter(e -> !e.getType().getQualifiedName().equals(ASSOCIATION))
            .map(e -> e.getName())
            .collect(Collectors.toList());

    // Now fetch up to 2018 context rows where all prediction fields are not null
    CqnSelect contextQuery =
        Select.from(context.getTarget().getQualifiedName())
            .columns(contextColumns.toArray(String[]::new))
            .where(
                entity -> {
                  com.sap.cds.ql.Predicate condition = null;
                  // Iterate over all prediction columns, add condition that they must be not null
                  // and concatenate with AND
                  for (String col : predictionColumns) {
                    com.sap.cds.ql.Predicate notNull = entity.get(col).isNotNull();
                    // concatenate: all columns we want predictions for must not be null
                    condition = condition == null ? notNull : condition.and(notNull);
                  }
                  return condition;
                })
            .limit(2018);
    List<Map> contextRows = new ArrayList<>(db.run(contextQuery).listOf(Map.class));
    if (contextRows.size() < 2) {
      logger.info(
          "Not enough context rows found with non-null values for prediction columns (minimum is 2), skipping predictions.");
      return;
    }

    // Add current row with [PREDICT] placeholders; strip draft-only fields first
    // (mirrors Node.js: delete predictionRow.DraftAdministrativeData_DraftUUID etc.)
    Map<String, Object> predictRow = new HashMap<>(row);
    DRAFT_FIELDS.forEach(predictRow::remove);
    for (String col : predictionColumns) {
      predictRow.putIfAbsent(col, "[PREDICT]");
    }
    if (!predictRow.values().contains("[PREDICT]")) {
      logger.info(
          "Current row already has values for all prediction columns, skipping predictions.");
      return;
    }
    contextRows.add(predictRow);

    // Tenant ID is only needed for multi-tenant applications with AI Core, for single-tenant apps
    // it is null,
    // yet we need to get it here from the context to pass it to the AI client.
    String tenantId = context.getUserInfo().getTenant();
    List<Map<String, Object>> predictions =
        aiClient.fetchPredictions(contextRows, predictionColumns, tenantId);

    // For now, we have requested predictions for exactly one row with [PREDICT] placeholders,
    // which is why we expect a list of size 1 back from the aiClient.
    // In the future, we could also support multiple rows with [PREDICT] placeholders.
    if (predictions.isEmpty()) {
      logger.warn("No predictions returned from AI client.");
      return;
    }
    if (predictions.size() > 1) {
      logger.warn("Multiple predictions returned from AI client, but only one was expected.");
      return;
    }
    Map<String, Object> prediction = predictions.get(0);

    // With the call "fetchPredictions", we get the raw prediction of the format
    // { ID : entity_ID, columnName: { prediction: predictionValue }, columnName2: { prediction:
    // predictionValue } }.
    //
    // Left to do: bring the prediction into the below format and put that into the
    // "SAP_Recommendations"
    // map for the row, which Fiori expects.
    // SAP_Recommendations: {
    //   columnName: {
    //     RecommendedFieldValue       : predictionValue;
    //     RecommendedFieldIsSuggestion: true;
    //     RecommendedFieldDescription : "human readable description for the predicted value";
    //     RecommendedFieldScoreValue  : 0.5; // This number here to rank several predictions per
    // column does not matter, since we only have one prediction.
    //   },
    //   columnName2: { ... }
    // }

    // To get a human readable recommendation for values where the ID is stored in the main table,
    // we build a map for this, i.e. genre_ID -> genre.name, country_code -> country.name, etc.
    // The text path is retrieved by stripping off the "_ID" suffix (if there is one)
    // and looking for a @Common.Text annotation on the association element (e.g. genre)
    // that points to the text field (e.g. genre.name).
    Map<String, String> textPaths = new HashMap<>();
    for (String col : predictionColumns) {
      Optional<String> path;
      if (col.endsWith("_ID")) {
        path = getTextPath(context, col.substring(0, col.length() - 3));
        if (path.isEmpty()) {
          path =
              getTextPath(
                  context,
                  col); // fallback: maybe the ID field itself has the @Common.Text annotation
        }
      } else {
        path = getTextPath(context, col);
      }
      path.ifPresent(p -> textPaths.put(col, p));
    }

    Map<String, Object> recommendations = new HashMap<>();
    for (String col : predictionColumns) {
      Map<String, Object> values = new HashMap<>();
      // Get the recommended value
      Object obj = prediction.get(col);
      if (obj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
        Object recommendedValue = map.get("prediction");
        // AI always returns strings; try to parse as number
        if (recommendedValue instanceof String s) {
          try {
            recommendedValue = Integer.valueOf(s);
          } catch (NumberFormatException ex) {
            try {
              recommendedValue = Double.valueOf(s);
            } catch (NumberFormatException ex2) {
              recommendedValue = s; // keep as string
            }
          }
        }
        final Object finalValue = recommendedValue;
        values.put("RecommendedFieldValue", finalValue);
        // Possibly also get the human readable description for that RecommendedFieldValue
        values.put(
            "RecommendedFieldDescription",
            ""); // default to empty string if we cannot find a description
        if (textPaths.containsKey(
            col)) { // col might be e.g. genre_ID, then textPaths.get(col) could be "genre.name"
          String[] parts = textPaths.get(col).split("\\."); // "genre.name" -> ["genre", "name"]
          if (parts.length
              != 2) { // The expected format for @Common.Text is "association.textField", if it's
            // not in this format, we do not resolve the description.
            logger.warn(
                "Text path {} for column {} is not in expected format 'association.textField', skipping description resolution.",
                textPaths.get(col),
                col);
            continue;
          }
          CqnSelect descQuery =
              Select.from(context.getTarget().getQualifiedName())
                  .columns(b -> b.get(col), b -> b.to(parts[0]).get(parts[1]))
                  .where(b -> b.get(col).eq(finalValue));
          db.run(descQuery)
              .forEach(
                  descRow -> { // this would then return a row with genre_ID and name
                    Object text = descRow.get(parts[1]);
                    if (text != null) values.put("RecommendedFieldDescription", text.toString());
                  });
        }
        values.put(
            "RecommendedFieldScoreValue",
            0.5); // If we had multiple predicions, we could use this field to rank them, since we
        // only have one prediction, it does not matter what is put here.
        values.put(
            "RecommendedFieldIsSuggestion",
            true); // If we had multiple predicions, we could use this field to select one
        // suggestion, since we only have one prediction, it does not matter what is put
        // here.
        recommendations.put(col, List.of(values));
      }
    }
    row.put("SAP_Recommendations", recommendations);
  }

  private Optional<String> getTextPath(CdsReadEventContext context, String columnName) {
    // Get all elements of entity we are looking at
    var elements = context.getTarget().elements();
    return elements
        .filter(e -> e.getName().equals(columnName)) // Find the element matching the column name
        .findFirst() // .flatMap will only unpack the Optional<element>, if we find an element with
        // the given column name
        .flatMap(
            e ->
                e.findAnnotation(
                    "@Common.Text")) // Check if that element has an annotation @Common.Text and get
        // its value, e.g. "=": "genre.name"
        .flatMap(
            a -> { // .flatMap will only unpack the Optional<String>, if we find an annotation
              // @Common.Text
              Object val = a.getValue();
              // Path annotations can be strings, i.e., "genre.name" or Maps, i.e., {"=":
              // "genre.name"}
              if (val instanceof String s) return Optional.of(s);
              if (val instanceof Map<?, ?> m) {
                Object eq = m.get("=");
                return eq != null ? Optional.of(eq.toString()) : Optional.empty();
              }
              return Optional.empty();
            });
  }
}
