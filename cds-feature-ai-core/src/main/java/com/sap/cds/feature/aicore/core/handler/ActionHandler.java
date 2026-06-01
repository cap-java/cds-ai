/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentTargetStatus;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.request.RequestContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICoreService.DEFAULT_NAME)
public class ActionHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(ActionHandler.class);

  public ActionHandler(AICoreServiceImpl service) {
    super(service);
  }

  @On(event = "resourceGroupForTenant")
  public void onResourceGroupForTenant(EventContext context) {
    Map<String, Object> params = asMap(context.get("data"));
    String tenant = (String) params.get("tenant");
    if (tenant == null) {
      tenant = RequestContext.getCurrent(service.getRuntime()).getUserInfo().getTenant();
    }
    String result = service.resourceGroupForTenant(tenant);
    context.put("result", result);
    context.setCompleted();
  }

  @On(event = "stop", entity = AICoreService.DEPLOYMENTS)
  public void onStop(EventContext context) {
    Map<String, Object> keys = asMap(context.get("keys"));
    String deploymentId = (String) keys.get(Deployments.ID);
    String resourceGroupId = resolveResourceGroup(keys);

    DeploymentApi api = service.getDeploymentApi();
    AiDeploymentModificationRequest modRequest =
        AiDeploymentModificationRequest.create().targetStatus(AiDeploymentTargetStatus.STOPPED);
    api.modify(resourceGroupId, deploymentId, modRequest);
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
