/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static com.sap.cds.feature.aicore.core.AICoreElements.Deployment;

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

    String id = (String) keys.get(Deployment.ID);
    if (id != null) {
      AiDeploymentResponseWithDetails deployment = deploymentApi.get(resourceGroupId, id);
      context.setResult(List.of(toCdsData(extractFields(deployment), resourceGroupId)));
    } else {
      AiDeploymentList result =
          deploymentApi.query(resourceGroupId, null, null, null, null, null, null, null);
      context.setResult(
          mapResources(result.getResources(), d -> toCdsData(extractFields(d), resourceGroupId)));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.DEPLOYMENTS)
  public void onCreate(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> entries = insert.entries();
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entry : entries) {
      String resourceGroupId = resolveResourceGroup(entry);
      String configurationId = (String) entry.get(Deployment.CONFIGURATION_ID);

      AiDeploymentCreationRequest request =
          AiDeploymentCreationRequest.create().configurationId(configurationId);

      if (entry.containsKey(Deployment.TTL)) {
        request.ttl((String) entry.get(Deployment.TTL));
      }

      var response = deploymentApi.create(resourceGroupId, request);
      CdsData result = CdsData.create(entry);
      result.put(Deployment.ID, response.getId());
      result.put(Deployment.STATUS, response.getStatus().getValue());
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
    if (!data.containsKey(Deployment.TARGET_STATUS)
        && !data.containsKey(Deployment.CONFIGURATION_ID)) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Update payload must contain 'targetStatus' or 'configurationId'");
    }

    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();

    String deploymentId = (String) keys.get(Deployment.ID);
    String resourceGroupId = resolveResourceGroup(merge(keys, data));

    AiDeploymentModificationRequest modRequest = AiDeploymentModificationRequest.create();

    if (data.containsKey(Deployment.TARGET_STATUS)) {
      String targetStatus = (String) data.get(Deployment.TARGET_STATUS);
      modRequest.targetStatus(AiDeploymentTargetStatus.fromValue(targetStatus));
    }
    if (data.containsKey(Deployment.CONFIGURATION_ID)) {
      modRequest.configurationId((String) data.get(Deployment.CONFIGURATION_ID));
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

    String deploymentId = (String) keys.get(Deployment.ID);
    String resourceGroupId = resolveResourceGroup(keys);

    deploymentApi.delete(resourceGroupId, deploymentId);
    logger.debug("Deleted deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of());
  }

  private record DeploymentFields(
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
      Object completionTime) {}

  // CPD-OFF - SDK types AiDeploymentResponseWithDetails and AiDeployment share no common interface
  private static DeploymentFields extractFields(AiDeploymentResponseWithDetails d) {
    return new DeploymentFields(
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
        d.getCompletionTime());
  }

  private static DeploymentFields extractFields(AiDeployment d) {
    return new DeploymentFields(
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
        d.getCompletionTime());
  }

  // CPD-ON

  private static CdsData toCdsData(DeploymentFields f, String resourceGroupId) {
    CdsData data = CdsData.create();
    data.put(Deployment.ID, f.id());
    data.put(Deployment.DEPLOYMENT_URL, f.deploymentUrl());
    data.put(Deployment.CONFIGURATION_ID, f.configurationId());
    data.put(Deployment.CONFIGURATION_NAME, f.configurationName());
    data.put(Deployment.EXECUTABLE_ID, f.executableId());
    data.put(Deployment.SCENARIO_ID, f.scenarioId());
    data.put(Deployment.STATUS, f.status());
    data.put(Deployment.STATUS_MESSAGE, f.statusMessage());
    data.put(Deployment.TARGET_STATUS, f.targetStatus());
    data.put(Deployment.LAST_OPERATION, f.lastOperation());
    data.put(Deployment.LATEST_RUNNING_CONFIGURATION_ID, f.latestRunningConfigurationId());
    data.put(Deployment.TTL, f.ttl());
    data.put(Deployment.CREATED_AT, f.createdAt());
    data.put(Deployment.MODIFIED_AT, f.modifiedAt());
    data.put(Deployment.SUBMISSION_TIME, f.submissionTime());
    data.put(Deployment.START_TIME, f.startTime());
    data.put(Deployment.COMPLETION_TIME, f.completionTime());
    data.putPath("resourceGroup.resourceGroupId", resourceGroupId);
    return data;
  }
}
