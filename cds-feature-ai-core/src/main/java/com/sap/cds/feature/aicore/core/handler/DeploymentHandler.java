/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentResponseWithDetails;
import com.sap.ai.sdk.core.model.AiDeploymentTargetStatus;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.core.DeploymentResolver;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments_;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICore.SERVICE_NAME)
public class DeploymentHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentHandler.class);

  public DeploymentHandler(
      AICoreConfig config, AICoreClients clients, DeploymentResolver resolver) {
    super(config, clients, resolver);
  }

  @On(entity = Deployments_.CDS_NAME)
  public void onRead(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    AnalysisResult analysis = CqnAnalyzer.create(model).analyze(select);
    Map<String, Object> keys = analysis.targetKeys();
    Map<String, Object> values = analysis.targetValues();

    String resourceGroupId = resolveResourceGroup(context, merge(keys, values));
    ensureResourceGroupAccessible(context, resourceGroupId);

    String id = (String) keys.get(Deployments.ID);
    if (id != null) {
      AiDeploymentResponseWithDetails deployment = clients.deploymentApi().get(resourceGroupId, id);
      context.setResult(List.of(toDeployments(deployment, resourceGroupId)));
    } else {
      AiDeploymentList result =
          clients.deploymentApi().query(resourceGroupId, null, null, null, null, null, null, null);
      context.setResult(
          mapResources(result.getResources(), d -> toDeployments(d, resourceGroupId)));
    }
  }

  @On
  public void onCreate(CdsCreateEventContext context, List<Deployments> entries) {
    List<Map<String, Object>> results = new ArrayList<>();

    for (Deployments entry : entries) {
      String resourceGroupId = resolveResourceGroup(context, entry);
      ensureResourceGroupAccessible(context, resourceGroupId);
      String configurationId = entry.getConfigurationId();

      AiDeploymentCreationRequest request =
          AiDeploymentCreationRequest.create().configurationId(configurationId);

      if (entry.getTtl() != null) {
        request.ttl(entry.getTtl());
      }

      var response = clients.deploymentApi().create(resourceGroupId, request);
      entry.setId(response.getId());
      entry.setStatus(response.getStatus().getValue());
      results.add(entry);
      logger.debug("Created deployment {} in resource group {}", response.getId(), resourceGroupId);
    }
    context.setResult(results);
  }

  @On
  public void onUpdate(CdsUpdateEventContext context, List<Deployments> entries) {
    if (entries.isEmpty()) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "No update payload provided");
    }
    Deployments data = entries.get(0);
    if (data.getTargetStatus() == null && data.getConfigurationId() == null) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Update payload must contain 'targetStatus' or 'configurationId'");
    }

    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(context.getCqn()).targetKeys();

    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(context, merge(keys, data));
    ensureResourceGroupAccessible(context, resourceGroupId);

    AiDeploymentModificationRequest modRequest = AiDeploymentModificationRequest.create();

    if (data.getTargetStatus() != null) {
      modRequest.targetStatus(AiDeploymentTargetStatus.fromValue(data.getTargetStatus()));
    }
    if (data.getConfigurationId() != null) {
      modRequest.configurationId(data.getConfigurationId());
    }

    clients.deploymentApi().modify(resourceGroupId, deploymentId, modRequest);
    logger.debug("Updated deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of(data));
  }

  @On(entity = Deployments_.CDS_NAME)
  public void onDelete(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();

    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(context, keys);
    ensureResourceGroupAccessible(context, resourceGroupId);

    clients.deploymentApi().delete(resourceGroupId, deploymentId);
    logger.debug("Deleted deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setResult(List.of());
  }

  // The two AI SDK input types AiDeploymentResponseWithDetails and AiDeployment have identical
  // accessor signatures but share no common interface, so we extract values into a unified
  // intermediate record (DeploymentValues) before applying them to the generated Deployments
  // target via applyTo(). The set-on-target logic is shared; the extraction below is the
  // unavoidable structural duplicate that CPD picks up. getStatus()/getTargetStatus() are
  // declared @Nonnull by the SDK; getLastOperation() is @Nullable and explicitly null-checked.

  // CPD-OFF
  private static Deployments toDeployments(
      AiDeploymentResponseWithDetails d, String resourceGroupId) {
    return applyTo(
        new DeploymentValues(
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
            d.getCompletionTime()),
        resourceGroupId);
  }

  private static Deployments toDeployments(AiDeployment d, String resourceGroupId) {
    return applyTo(
        new DeploymentValues(
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
            d.getCompletionTime()),
        resourceGroupId);
  }

  // CPD-ON

  private static Deployments applyTo(DeploymentValues v, String resourceGroupId) {
    Deployments data = Deployments.create();
    data.setId(v.id);
    data.setDeploymentUrl(v.deploymentUrl);
    data.setConfigurationId(v.configurationId);
    data.setConfigurationName(v.configurationName);
    data.setExecutableId(v.executableId);
    data.setScenarioId(v.scenarioId);
    data.setStatus(v.status);
    data.setStatusMessage(v.statusMessage);
    data.setTargetStatus(v.targetStatus);
    data.setLastOperation(v.lastOperation);
    data.setLatestRunningConfigurationId(v.latestRunningConfigurationId);
    data.setTtl(v.ttl);
    data.put(Deployments.CREATED_AT, v.createdAt);
    data.put(Deployments.MODIFIED_AT, v.modifiedAt);
    data.put(Deployments.SUBMISSION_TIME, v.submissionTime);
    data.put(Deployments.START_TIME, v.startTime);
    data.put(Deployments.COMPLETION_TIME, v.completionTime);
    data.setResourceGroup(ResourceGroups.create(resourceGroupId));
    return data;
  }

  private record DeploymentValues(
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
      OffsetDateTime createdAt,
      OffsetDateTime modifiedAt,
      OffsetDateTime submissionTime,
      OffsetDateTime startTime,
      OffsetDateTime completionTime) {}
}
