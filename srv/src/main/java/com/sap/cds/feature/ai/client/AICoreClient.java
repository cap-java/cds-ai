/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.JacksonConfiguration;
import com.sap.ai.sdk.foundationmodels.rpt.RptModel;
import com.sap.ai.sdk.foundationmodels.rpt.generated.client.DefaultApi;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictRequestPayload;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictionConfig;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.PredictionPlaceholder;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.RowsInnerValue;
import com.sap.ai.sdk.foundationmodels.rpt.generated.model.TargetColumnConfig;
import com.sap.cds.feature.ai.client.setup.AICoreSetupHandler;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AICoreClient implements AIClient {

  private final AICoreSetupHandler setup;
  private static final Logger logger = LoggerFactory.getLogger(AICoreClient.class);

  // Retry inference call when resource group inference endpoint is not yet ready (403)
  private static final int INFERENCE_READY_MAX_RETRIES = 8;
  private static final long INFERENCE_READY_INITIAL_DELAY_MS = 500;

  // Safety-net: exclude draft/admin fields from the prediction request body.
  // BLOB/Vector fields are already filtered upstream in FioriRecommendationHandler.
  private static final Set<String> EXCLUDED_FIELDS =
      Set.of(
          "HasActiveEntity",
          "HasDraftEntity",
          "IsActiveEntity",
          "DraftAdministrativeData_DraftUUID",
          "createdBy",
          "modifiedBy",
          "createdAt",
          "modifiedAt");

  public AICoreClient(AICoreSetupHandler setup) {
    this.setup = setup;
  }

  @Override
  /*
   * Prediction flow:
   *  1. resolveResourceGroup(tenantId) to get the AI Core resource group for the tenant
   *  2. call predict() with the resource group, input rows and prediction columns
   */
  public List<Map<String, Object>> fetchPredictions(
      List<Map> rows, List<String> predictionColumns, String tenantId) {
    try {
      String resourceGroup = setup.resolveResourceGroup(tenantId);
      return predict(resourceGroup, rows, predictionColumns);
    } catch (Exception e) {
      logger.error("Failed to fetch predictions from AI Core", e);
      throw new RuntimeException("Failed to fetch predictions from AI Core", e);
    }
  }

  /*
   * As described in https://sap.github.io/ai-sdk/docs/java/foundation-models/sap-rpt/table-completion#simple-table-completion
   *  1. Build a PredictRequestPayload with the input rows and prediction column config
   *  2. Call the AI Core API to get predictions
   *  3. Parse and return the predictions as List<Map<String, Object>>
   */
  private List<Map<String, Object>> predict(
      String resourceGroup, List<Map> rows, List<String> predictionColumns) {
    var targetColumns =
        predictionColumns.stream()
            .map(
                col ->
                    TargetColumnConfig.create()
                        .name(col)
                        .predictionPlaceholder(PredictionPlaceholder.create("[PREDICT]"))
                        .taskType(TargetColumnConfig.TaskTypeEnum.CLASSIFICATION))
            .collect(Collectors.toList());

    var sdkRows =
        rows.stream()
            .map(
                row -> {
                  Map<String, RowsInnerValue> sdkRow = new LinkedHashMap<>();
                  row.forEach(
                      (k, v) -> {
                        if (!EXCLUDED_FIELDS.contains(k)
                            && v != null
                            && (v instanceof String
                                || v instanceof Number
                                || v instanceof Boolean)) {
                          sdkRow.put(k.toString(), RowsInnerValue.create(v.toString()));
                        }
                      });
                  return sdkRow;
                })
            .collect(Collectors.toList());

    var request =
        PredictRequestPayload.create()
            .predictionConfig(PredictionConfig.create().targetColumns(targetColumns))
            .rows(sdkRows)
            .indexColumn("ID");

    logger.debug(
        "Sending prediction request for {} rows, {} target columns",
        sdkRows.size(),
        targetColumns.size());

    // In multi-tenant mode, we manage the RPT-1 deployment lifecycle per resource group ourselves
    // (create on subscribe, delete on unsubscribe), so we resolve the deployment ID explicitly.
    // In single-tenant mode, we let the SDK resolve the deployment via forModel(), which queries
    // AI Core for any running RPT-1 deployment in the resource group.

    // In multi-tenant mode, we cannot use RptClient.forModel(), because it calls
    // AiCoreService().getInferenceDestination() with no arguments,
    // which always resolves to the "default" resource group. Instead, we replicate
    // the logic from RptClient using our per-tenant resource group, with the same arguments, i.e.,
    // JacksonConfiguration.getDefaultObjectMapper() and the default header "Content-Encoding:
    // gzip".
    System.out.println("Resolving inference destination for resource group: " + resourceGroup);
    System.out.println(
        "AICoreSetup.isMultitenancyEnabled() = " + AICoreSetupHandler.isMultitenancyEnabled());
    var inferenceBuilder = new AiCoreService().getInferenceDestination(resourceGroup);
    var model = RptModel.SAP_RPT_1_SMALL;
    System.out.println("Using model: " + model.name() + ", deployment: " + inferenceBuilder);
    var destination =
        AICoreSetupHandler.isMultitenancyEnabled()
            ? inferenceBuilder.usingDeploymentId(setup.getDeploymentForResourceGroup(resourceGroup))
            : inferenceBuilder.forModel(RptModel.SAP_RPT_1_SMALL);
    var apiClient =
        ApiClient.create(destination)
            .withObjectMapper(JacksonConfiguration.getDefaultObjectMapper());
    var api = new DefaultApi(apiClient).withDefaultHeaders(Map.of("Content-Encoding", "gzip"));

    // AI Core inference endpoints for freshly created resource groups may return 403
    // until the endpoint is fully provisioned — retry with exponential backoff.
    long delay = INFERENCE_READY_INITIAL_DELAY_MS;
    for (int i = 0; i < INFERENCE_READY_MAX_RETRIES; i++) {
      try {
        var response = api.predict(request);
        logger.debug("Prediction response id: {}", response.getId());
        try {
          return JacksonConfiguration.getDefaultObjectMapper()
              .convertValue(response.getPredictions(), new TypeReference<>() {});
        } catch (Exception e) {
          throw new RuntimeException("Failed to parse prediction response", e);
        }
      } catch (OpenApiRequestException e) {
        if (AICoreSetupHandler.notReadyYet(e) && i < INFERENCE_READY_MAX_RETRIES - 1) {
          logger.debug(
              "Inference endpoint for resource group {} not ready yet (403), retrying in {} ms ({}/{})",
              resourceGroup,
              delay,
              i + 1,
              INFERENCE_READY_MAX_RETRIES);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for inference endpoint", ie);
          }
          delay *= 2;
        } else {
          throw e;
        }
      }
    }
    throw new IllegalStateException("predict() exited retry loop unexpectedly");
  }
}
