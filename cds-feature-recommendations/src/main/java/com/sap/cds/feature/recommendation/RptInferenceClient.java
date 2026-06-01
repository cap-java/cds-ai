/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

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
import io.github.resilience4j.retry.Retry;
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
 * String rg = service.resourceGroupForTenant(tenantId);
 * String deploymentId = service.deploymentId(rg, RptModelSpec.rpt1());
 * RptInferenceClient client =
 *     new RptInferenceClient(service.inferenceClient(rg, deploymentId), service.getRetry());
 * List<CdsData> predictions = client.predict(rows, List.of("targetColumn"), "ID");
 * }</pre>
 */
public class RptInferenceClient implements RecommendationClient {

  private static final Logger logger = LoggerFactory.getLogger(RptInferenceClient.class);

  private static final Set<String> MANAGED_FIELDS =
      Set.of("createdBy", "modifiedBy", "createdAt", "modifiedAt");

  private final DefaultApi api;
  private final Retry retry;

  public RptInferenceClient(ApiClient apiClient, Retry retry) {
    this.api =
        new DefaultApi(apiClient.withObjectMapper(JacksonConfiguration.getDefaultObjectMapper()));
    this.retry = retry;
  }

  @Override
  public List<CdsData> predict(
      List<CdsData> rows, List<String> predictionColumns, String indexColumn) {
    PredictRequestPayload request = buildRequest(rows, predictionColumns, indexColumn);
    logger.debug(
        "Sending prediction request for {} rows, {} target columns",
        rows.size(),
        predictionColumns.size());
    return Retry.decorateSupplier(
            retry,
            () -> {
              var response = api.predict(request);
              logger.debug("Prediction response id: {}", response.getId());
              List<Map<String, Object>> raw =
                  JacksonConfiguration.getDefaultObjectMapper()
                      .convertValue(response.getPredictions(), new TypeReference<>() {});
              return raw.stream().map(CdsData::create).toList();
            })
        .get();
  }

  private static PredictRequestPayload buildRequest(
      List<CdsData> rows, List<String> predictionColumns, String indexColumn) {
    var targetColumns =
        predictionColumns.stream()
            .map(
                col ->
                    TargetColumnConfig.create()
                        .name(col)
                        .predictionPlaceholder(PredictionPlaceholder.create("[PREDICT]"))
                        .taskType(TargetColumnConfig.TaskTypeEnum.CLASSIFICATION))
            .toList();

    var sdkRows =
        rows.stream()
            .map(
                row -> {
                  Map<String, RowsInnerValue> sdkRow = new HashMap<>();
                  row.forEach(
                      (k, v) -> {
                        if (v != null
                            && !Drafts.ELEMENTS.contains(k)
                            && !MANAGED_FIELDS.contains(k)) {
                          sdkRow.put(k, RowsInnerValue.create(v.toString()));
                        }
                      });
                  for (String target : predictionColumns) {
                    if (!row.containsKey(target) || row.get(target) == null) {
                      sdkRow.put(target, RowsInnerValue.create("[PREDICT]"));
                    }
                  }
                  return sdkRow;
                })
            .toList();

    return PredictRequestPayload.create()
        .predictionConfig(PredictionConfig.create().targetColumns(targetColumns))
        .rows(sdkRows)
        .indexColumn(indexColumn);
  }
}
