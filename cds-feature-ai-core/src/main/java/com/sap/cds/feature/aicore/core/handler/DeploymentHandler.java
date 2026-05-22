/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentResponseWithDetails;
import com.sap.ai.sdk.core.model.AiDeploymentTargetStatus;
import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICoreService.DEFAULT_NAME)
public class DeploymentHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentHandler.class);

  private final DeploymentApi deploymentApi;

  public DeploymentHandler(AICoreServiceImpl service) {
    super(service);
    this.deploymentApi = service.getDeploymentApi();
  }

  @On(event = CqnService.EVENT_READ, entity = AICoreService.DEPLOYMENTS)
  public void onRead(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    AnalysisResult analysis = CqnAnalyzer.create(model).analyze(select);
    Map<String, Object> keys = analysis.targetKeys();
    Map<String, Object> values = analysis.targetValues();

    String resourceGroupId = resolveResourceGroup(merge(keys, values));

    String id = (String) keys.get("id");
    if (id != null) {
      AiDeploymentResponseWithDetails deployment = deploymentApi.get(resourceGroupId, id);
      context.setResult(List.of(toMap(deployment, resourceGroupId)));
    } else {
      AiDeploymentList result =
          deploymentApi.query(resourceGroupId, null, null, null, null, null, null, null);
      context.setResult(mapResources(result.getResources(), d -> toMap(d, resourceGroupId)));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.DEPLOYMENTS)
  public void onCreate(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> entries = insert.entries();
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entry : entries) {
      String resourceGroupId = resolveResourceGroup(entry);
      String configurationId = (String) entry.get("configurationId");

      AiDeploymentCreationRequest request =
          AiDeploymentCreationRequest.create().configurationId(configurationId);

      if (entry.containsKey("ttl")) {
        request.ttl((String) entry.get("ttl"));
      }

      var response = deploymentApi.create(resourceGroupId, request);
      CdsData result = CdsData.create(entry);
      result.put("id", response.getId());
      result.put("status", response.getStatus().getValue());
      results.add(result);
      logger.debug("Created deployment {} in resource group {}", response.getId(), resourceGroupId);
    }
    context.setResult(results);
  }

  @On(event = CqnService.EVENT_UPDATE, entity = AICoreService.DEPLOYMENTS)
  public void onUpdate(CdsUpdateEventContext context) {
    CqnUpdate update = context.getCqn();
    List<Map<String, Object>> entries = update.entries();
    if (entries.isEmpty()) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "No update payload provided");
    }
    Map<String, Object> data = entries.get(0);
    if (!data.containsKey("targetStatus") && !data.containsKey("configurationId")) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Update payload must contain 'targetStatus' or 'configurationId'");
    }

    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();

    String deploymentId = (String) keys.get("id");
    String resourceGroupId = resolveResourceGroup(merge(keys, data));

    AiDeploymentModificationRequest modRequest = AiDeploymentModificationRequest.create();

    if (data.containsKey("targetStatus")) {
      String targetStatus = (String) data.get("targetStatus");
      modRequest.targetStatus(AiDeploymentTargetStatus.fromValue(targetStatus));
    }
    if (data.containsKey("configurationId")) {
      modRequest.configurationId((String) data.get("configurationId"));
    }

    deploymentApi.modify(resourceGroupId, deploymentId, modRequest);
    logger.debug("Updated deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of(CdsData.create(data)));
  }

  @On(event = CqnService.EVENT_DELETE, entity = AICoreService.DEPLOYMENTS)
  public void onDelete(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();

    String deploymentId = (String) keys.get("id");
    String resourceGroupId = resolveResourceGroup(keys);

    deploymentApi.delete(resourceGroupId, deploymentId);
    logger.debug("Deleted deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of());
  }

  // CPD-OFF - SDK types AiDeploymentResponseWithDetails and AiDeployment share no common interface
  private CdsData toMap(AiDeploymentResponseWithDetails d, String resourceGroupId) {
    return buildDeploymentData(
        d.getId(),
        d.getDeploymentUrl(),
        d.getConfigurationId(),
        d.getConfigurationName(),
        d.getExecutableId(),
        d.getScenarioId(),
        d.getStatus().getValue(),
        d.getStatusMessage(),
        d.getTargetStatus().getValue(),
        d.getLastOperation() != null ? d.getLastOperation().getValue() : null,
        d.getLatestRunningConfigurationId(),
        d.getTtl(),
        d.getCreatedAt(),
        d.getModifiedAt(),
        d.getSubmissionTime(),
        d.getStartTime(),
        d.getCompletionTime(),
        resourceGroupId);
  }

  private CdsData toMap(AiDeployment d, String resourceGroupId) {
    return buildDeploymentData(
        d.getId(),
        d.getDeploymentUrl(),
        d.getConfigurationId(),
        d.getConfigurationName(),
        d.getExecutableId(),
        d.getScenarioId(),
        d.getStatus().getValue(),
        d.getStatusMessage(),
        d.getTargetStatus().getValue(),
        d.getLastOperation() != null ? d.getLastOperation().getValue() : null,
        d.getLatestRunningConfigurationId(),
        d.getTtl(),
        d.getCreatedAt(),
        d.getModifiedAt(),
        d.getSubmissionTime(),
        d.getStartTime(),
        d.getCompletionTime(),
        resourceGroupId);
  }

  // CPD-ON

  private static CdsData buildDeploymentData(
      String id,
      String deploymentUrl,
      String configurationId,
      String configurationName,
      String executableId,
      String scenarioId,
      String status,
      String statusMessage,
      String targetStatus,
      String lastOperation,
      String latestRunningConfigurationId,
      String ttl,
      Object createdAt,
      Object modifiedAt,
      Object submissionTime,
      Object startTime,
      Object completionTime,
      String resourceGroupId) {
    CdsData data = CdsData.create();
    data.put("id", id);
    data.put("deploymentUrl", deploymentUrl);
    data.put("configurationId", configurationId);
    data.put("configurationName", configurationName);
    data.put("executableId", executableId);
    data.put("scenarioId", scenarioId);
    data.put("status", status);
    data.put("statusMessage", statusMessage);
    data.put("targetStatus", targetStatus);
    data.put("lastOperation", lastOperation);
    data.put("latestRunningConfigurationId", latestRunningConfigurationId);
    data.put("ttl", ttl);
    data.put("createdAt", createdAt);
    data.put("modifiedAt", modifiedAt);
    data.put("submissionTime", submissionTime);
    data.put("startTime", startTime);
    data.put("completionTime", completionTime);
    data.putPath("resourceGroup.resourceGroupId", resourceGroupId);
    return data;
  }
}
