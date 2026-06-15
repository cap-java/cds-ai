/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentTargetStatus;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.DeploymentsStopContext;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments_;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICoreService.DEFAULT_NAME)
public class ActionHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(ActionHandler.class);

  public ActionHandler(AICoreConfig config, AICoreClients clients) {
    super(config, clients);
  }

  @On(entity = Deployments_.CDS_NAME)
  public void onStop(DeploymentsStopContext context) {
    Map<String, Object> keys = asMap(context.get("keys"));
    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(context, keys);

    AiDeploymentModificationRequest modRequest =
        AiDeploymentModificationRequest.create().targetStatus(AiDeploymentTargetStatus.STOPPED);
    clients.deploymentApi().modify(resourceGroupId, deploymentId, modRequest);
    logger.debug("Stopped deployment {} in resource group {}", deploymentId, resourceGroupId);
    context.setCompleted();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object obj) {
    if (obj instanceof Map) {
      return (Map<String, Object>) obj;
    }
    return Map.of();
  }
}
