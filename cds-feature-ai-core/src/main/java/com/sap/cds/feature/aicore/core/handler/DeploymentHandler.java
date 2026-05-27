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
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
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

    String id = (String) keys.get(Deployments.ID);
    if (id != null) {
      AiDeploymentResponseWithDetails deployment = deploymentApi.get(resourceGroupId, id);
      context.setResult(List.of(toDeployments(deployment, resourceGroupId)));
    } else {
      AiDeploymentList result =
          deploymentApi.query(resourceGroupId, null, null, null, null, null, null, null);
      context.setResult(
          mapResources(result.getResources(), d -> toDeployments(d, resourceGroupId)));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.DEPLOYMENTS)
  public void onCreate(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> entries = insert.entries();
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entry : entries) {
      String resourceGroupId = resolveResourceGroup(entry);
      String configurationId = (String) entry.get(Deployments.CONFIGURATION_ID);

      AiDeploymentCreationRequest request =
          AiDeploymentCreationRequest.create().configurationId(configurationId);

      if (entry.containsKey(Deployments.TTL)) {
        request.ttl((String) entry.get(Deployments.TTL));
      }

      var response = deploymentApi.create(resourceGroupId, request);
      CdsData result = CdsData.create(entry);
      result.put(Deployments.ID, response.getId());
      result.put(Deployments.STATUS, response.getStatus().getValue());
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
    if (!data.containsKey(Deployments.TARGET_STATUS)
        && !data.containsKey(Deployments.CONFIGURATION_ID)) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Update payload must contain 'targetStatus' or 'configurationId'");
    }

    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();

    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(merge(keys, data));

    AiDeploymentModificationRequest modRequest = AiDeploymentModificationRequest.create();

    if (data.containsKey(Deployments.TARGET_STATUS)) {
      String targetStatus = (String) data.get(Deployments.TARGET_STATUS);
      modRequest.targetStatus(AiDeploymentTargetStatus.fromValue(targetStatus));
    }
    if (data.containsKey(Deployments.CONFIGURATION_ID)) {
      modRequest.configurationId((String) data.get(Deployments.CONFIGURATION_ID));
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

    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(keys);

    deploymentApi.delete(resourceGroupId, deploymentId);
    logger.debug("Deleted deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of());
  }

  // CPD-OFF - SDK types AiDeploymentResponseWithDetails and AiDeployment share no common interface
  private static Deployments toDeployments(
      AiDeploymentResponseWithDetails d, String resourceGroupId) {
    Deployments data = Deployments.create();
    data.setId(d.getId());
    data.setDeploymentUrl(d.getDeploymentUrl());
    data.setConfigurationId(d.getConfigurationId());
    data.setConfigurationName(d.getConfigurationName());
    data.setExecutableId(d.getExecutableId());
    data.setScenarioId(d.getScenarioId());
    data.setStatus(d.getStatus().getValue());
    data.setStatusMessage(d.getStatusMessage());
    data.setTargetStatus(d.getTargetStatus().getValue());
    data.setLastOperation(d.getLastOperation() != null ? d.getLastOperation().getValue() : null);
    data.setLatestRunningConfigurationId(d.getLatestRunningConfigurationId());
    data.setTtl(d.getTtl());
    data.put(Deployments.CREATED_AT, d.getCreatedAt());
    data.put(Deployments.MODIFIED_AT, d.getModifiedAt());
    data.put(Deployments.SUBMISSION_TIME, d.getSubmissionTime());
    data.put(Deployments.START_TIME, d.getStartTime());
    data.put(Deployments.COMPLETION_TIME, d.getCompletionTime());
    data.setResourceGroup(ResourceGroups.create(resourceGroupId));
    return data;
  }

  private static Deployments toDeployments(AiDeployment d, String resourceGroupId) {
    Deployments data = Deployments.create();
    data.setId(d.getId());
    data.setDeploymentUrl(d.getDeploymentUrl());
    data.setConfigurationId(d.getConfigurationId());
    data.setConfigurationName(d.getConfigurationName());
    data.setExecutableId(d.getExecutableId());
    data.setScenarioId(d.getScenarioId());
    data.setStatus(d.getStatus().getValue());
    data.setStatusMessage(d.getStatusMessage());
    data.setTargetStatus(d.getTargetStatus().getValue());
    data.setLastOperation(d.getLastOperation() != null ? d.getLastOperation().getValue() : null);
    data.setLatestRunningConfigurationId(d.getLatestRunningConfigurationId());
    data.setTtl(d.getTtl());
    data.put(Deployments.CREATED_AT, d.getCreatedAt());
    data.put(Deployments.MODIFIED_AT, d.getModifiedAt());
    data.put(Deployments.SUBMISSION_TIME, d.getSubmissionTime());
    data.put(Deployments.START_TIME, d.getStartTime());
    data.put(Deployments.COMPLETION_TIME, d.getCompletionTime());
    data.setResourceGroup(ResourceGroups.create(resourceGroupId));
    return data;
  }
  // CPD-ON
}
