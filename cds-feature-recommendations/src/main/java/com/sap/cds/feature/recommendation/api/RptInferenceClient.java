/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sap.ai.sdk.core.JacksonConfiguration;
import com.sap.ai.sdk.foundationmodels.rpt.generated.client.DefaultApi;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictRequestPayload;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictionConfig;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictionPlaceholder;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.RowsInnerValue;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.TargetColumnConfig;
import com.sap.cds.CdsData;
import com.sap.cds.services.draft.Drafts;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for invoking the SAP RPT-1 foundation model for tabular predictions. This class is part of
 * the public API and can be used directly by applications that need to perform custom inference
 * outside the automatic Fiori recommendation flow.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * AICoreService service = ...;
 * String rg = service.resourceGroup();
 * String deploymentId = service.deploymentId(rg, RptModelSpec.rpt1());
 * RptInferenceClient client = new RptInferenceClient(service.inferenceClient(rg, deploymentId));
 * List<CdsData> predictions = client.predict(rows, List.of("targetColumn"), "ID");
 * }</pre>
 */
public class RptInferenceClient implements RecommendationClient {

  private static final Logger logger = LoggerFactory.getLogger(RptInferenceClient.class);

  // RPT-1 specific: the placeholder value that marks a column as a prediction target in the request
  public static final String PREDICT = "[PREDICT]";

  private static final Set<String> MANAGED_FIELDS =
      Set.of("createdBy", "modifiedBy", "createdAt", "modifiedAt");

  private static final Retry INFERENCE_RETRY = buildInferenceRetry();

  private final DefaultApi rpt;

  public RptInferenceClient(ApiClient apiClient) {
    this.rpt =
        new DefaultApi(apiClient.withObjectMapper(JacksonConfiguration.getDefaultObjectMapper()));
  }

  @Override
  public List<CdsData> predict(
      CdsData predictionRow,
      List<CdsData> contextRows,
      List<String> predictionColumns,
      List<String> keyNames) {
    String indexColumn = resolveIndexColumn(keyNames);
    CdsData preparedPredictRow = preparePredictRow(predictionRow, predictionColumns);
    List<CdsData> allRows = new java.util.ArrayList<>(contextRows);
    allRows.add(preparedPredictRow);
    addSyntheticKeyIfNeeded(allRows, keyNames, indexColumn);

    PredictRequestPayload request = buildRequest(allRows, predictionColumns, indexColumn);
    logger.debug(
        "Sending prediction request for one row with {} context rows, {} target columns",
        contextRows.size(),
        predictionColumns.size());
    return Retry.decorateSupplier(
            INFERENCE_RETRY,
            () -> {
              var response = rpt.predict(request);
              logger.debug("Prediction response id: {}", response.getId());
              List<Map<String, Object>> raw =
                  JacksonConfiguration.getDefaultObjectMapper()
                      .convertValue(response.getPredictions(), new TypeReference<>() {});
              return raw.stream().map(CdsData::create).toList();
            })
        .get();
  }

  // RPT-1 specific: when the entity has a composite or non-ID key, a synthetic string index column
  // is computed by concatenating all key fields and injected into each row before sending.
  private static final String SYNTHETIC_INDEX_COLUMN = "SAP_RECOMMENDATIONS_ID";

  // If there is one key, use it directly and don't compute a synthetic key
  private static String resolveIndexColumn(List<String> keyNames) {
    return (keyNames.size() == 1 && "ID".equals(keyNames.get(0))) ? "ID" : SYNTHETIC_INDEX_COLUMN;
  }

  // Compute the synthetic key as a concatenated string from the keys listed in keyNames
  private static String computeSyntheticKey(Map<String, Object> row, List<String> keyNames) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keyNames.size(); i++) {
      if (i > 0) sb.append('\0');
      sb.append(keyNames.get(i)).append('\0');
      Object value = row.get(keyNames.get(i));
      if (value != null) sb.append(value);
    }
    return sb.toString();
  }

  private static void addSyntheticKeyIfNeeded(
      List<CdsData> rows, List<String> keyNames, String indexColumn) {
    if (SYNTHETIC_INDEX_COLUMN.equals(indexColumn)) {
      rows.forEach(r -> r.put(SYNTHETIC_INDEX_COLUMN, computeSyntheticKey(r, keyNames)));
    }
  }

  // Returns a copy of the predictRow without the fields in Drafts.ELEMENTS and with a
  // prediction placeholder for empty values in the predictinonColumns
  private static CdsData preparePredictRow(CdsData predictRow, List<String> predictionColumns) {
    Map<String, Object> preparedPredictRowMap = new HashMap<>(predictRow);
    // Drafts.ELEMENTS.forEach(preparedPredictRowMap::remove);
    for (String col : predictionColumns) {
      preparedPredictRowMap.putIfAbsent(col, PREDICT);
    }
    return CdsData.create(preparedPredictRowMap);
  }

  private static PredictRequestPayload buildRequest(
      List<CdsData> rows, List<String> predictionColumns, String indexColumn) {
    var targetColumns =
        predictionColumns.stream()
            .map(
                col ->
                    TargetColumnConfig.create()
                        .name(col)
                        .predictionPlaceholder(PredictionPlaceholder.create(PREDICT))
                        .taskType(TargetColumnConfig.TaskTypeEnum.CLASSIFICATION))
            .toList();

    var sdkRows = rows.stream().map(row -> toSdkRow(row, predictionColumns)).toList();

    return PredictRequestPayload.create()
        .predictionConfig(PredictionConfig.create().targetColumns(targetColumns))
        .rows(sdkRows)
        .indexColumn(indexColumn);
  }

  private static Map<String, RowsInnerValue> toSdkRow(CdsData row, List<String> predictionColumns) {
    Map<String, RowsInnerValue> sdkRow = new HashMap<>();
    row.forEach(
        (k, v) -> {
          if (v != null && !Drafts.ELEMENTS.contains(k) && !MANAGED_FIELDS.contains(k)) {
            sdkRow.put(k, RowsInnerValue.create(v.toString()));
          }
        });
    for (String target : predictionColumns) {
      if (!row.containsKey(target) || row.get(target) == null) {
        sdkRow.put(target, RowsInnerValue.create(PREDICT));
      }
    }
    return sdkRow;
  }

  private static Retry buildInferenceRetry() {
    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2.0, 5000))
            .retryOnException(
                e ->
                    e instanceof OpenApiRequestException oae
                        && oae.statusCode() != null
                        && (oae.statusCode() == 403
                            || oae.statusCode() == 404
                            || oae.statusCode() == 412))
            .build();
    return Retry.of("rpt-inference", config);
  }
}
