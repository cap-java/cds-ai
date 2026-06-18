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
import com.sap.cds.feature.recommendation.RptIndexColumns;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * RemoteService service = runtime.getServiceCatalog().getService(RemoteService.class, AICore_.CDS_NAME);
 * ResourceGroupContext rgCtx = ResourceGroupContext.create();
 * service.emit(rgCtx);
 * String rg = rgCtx.getResult();
 * DeploymentIdContext depCtx = DeploymentIdContext.create();
 * depCtx.setResourceGroupId(rg);
 * depCtx.setSpec(RptModelSpec.rpt1());
 * service.emit(depCtx);
 * InferenceClientContext infCtx = InferenceClientContext.create();
 * infCtx.setResourceGroupId(rg);
 * infCtx.setDeploymentId(depCtx.getResult());
 * service.emit(infCtx);
 * RptInferenceClient client = new RptInferenceClient(infCtx.getResult(), keyNames);
 * List<CdsData> predictions = client.predict(predictionRow, contextRows, List.of("targetColumn"));
 * }</pre>
 */
public class RptInferenceClient implements RecommendationClient {

  private static final Logger logger = LoggerFactory.getLogger(RptInferenceClient.class);

  // RPT-1 specific: the placeholder value that marks a column as a prediction target in the request
  public static final String PREDICT = "[PREDICT]";

  private static final Retry INFERENCE_RETRY = buildInferenceRetry();

  private final DefaultApi rpt;
  private final List<String> keyNames;

  public RptInferenceClient(ApiClient apiClient, List<String> keyNames) {
    this.rpt =
        new DefaultApi(apiClient.withObjectMapper(JacksonConfiguration.getDefaultObjectMapper()));
    this.keyNames = keyNames;
  }

  @Override
  public List<CdsData> predict(
      CdsData predictionRow, List<CdsData> contextRows, List<String> predictionColumns) {
    String indexColumn = RptIndexColumns.resolveIndexColumn(keyNames, predictionRow);
    CdsData preparedPredictRow = preparePredictRow(predictionRow, predictionColumns);
    List<CdsData> allRows = new ArrayList<>(contextRows);
    allRows.add(preparedPredictRow);

    PredictRequestPayload request = buildRequest(allRows, predictionColumns, indexColumn, keyNames);
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

  // '\0' is used as separator because it cannot appear in database string values
  // (VARCHAR/NVARCHAR), so concatenation of any composite key values is guaranteed collision-free.
  static String computeSyntheticKey(Map<String, Object> row, List<String> keyNames) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keyNames.size(); i++) {
      if (i > 0) sb.append('\0');
      sb.append(keyNames.get(i)).append('\0');
      Object value = row.get(keyNames.get(i));
      if (value != null) sb.append(value);
    }
    return sb.toString();
  }

  // Returns a copy of the predictRow with a prediction placeholder replacing empty values
  // in the predictionColumns - these will get filled by the predict method.
  private static CdsData preparePredictRow(CdsData predictRow, List<String> predictionColumns) {
    Map<String, Object> preparedPredictRowMap = new HashMap<>(predictRow);
    for (String col : predictionColumns) {
      preparedPredictRowMap.putIfAbsent(col, PREDICT);
    }
    return CdsData.create(preparedPredictRowMap);
  }

  private static PredictRequestPayload buildRequest(
      List<CdsData> rows,
      List<String> predictionColumns,
      String indexColumn,
      List<String> keyNames) {
    var targetColumns =
        predictionColumns.stream()
            .map(
                col ->
                    TargetColumnConfig.create()
                        .name(col)
                        .predictionPlaceholder(PredictionPlaceholder.create(PREDICT))
                        .taskType(TargetColumnConfig.TaskTypeEnum.CLASSIFICATION))
            .toList();

    // RPT-1 requires exactly one string-typed index column per row to identify predictions.
    // When the entity key is composite or non-string, then the index column is
    // RptIndexColumns.SYNTHETIC_INDEX_COLUMN and we need to compute the syntheticKey for all rows
    // before sending them to RPT-1.
    boolean syntheticKeyNeeded = RptIndexColumns.SYNTHETIC_INDEX_COLUMN.equals(indexColumn);
    var sdkRows =
        rows.stream()
            .map(
                row -> {
                  Map<String, RowsInnerValue> sdkRow = toSdkRow(row);
                  if (syntheticKeyNeeded) {
                    sdkRow.put(
                        RptIndexColumns.SYNTHETIC_INDEX_COLUMN,
                        RowsInnerValue.create(computeSyntheticKey(row, keyNames)));
                  }
                  return sdkRow;
                })
            .toList();

    return PredictRequestPayload.create()
        .predictionConfig(PredictionConfig.create().targetColumns(targetColumns))
        .rows(sdkRows)
        .indexColumn(indexColumn);
  }

  // Converts a CdsData row to the RPT SDK row format, i.e., into Map<String, RowsInnerValue>
  private static Map<String, RowsInnerValue> toSdkRow(CdsData row) {
    Map<String, RowsInnerValue> sdkRow = new HashMap<>();
    row.forEach(
        (k, v) -> {
          if (v != null) {
            sdkRow.put(k, RowsInnerValue.create(v.toString()));
          }
        });
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
