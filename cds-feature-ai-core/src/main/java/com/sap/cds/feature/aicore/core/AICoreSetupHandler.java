/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(DeploymentService.DEFAULT_NAME)
public class AICoreSetupHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AICoreSetupHandler.class);

  private final AICoreServiceImpl service;

  public AICoreSetupHandler(AICoreServiceImpl service) {
    this.service = service;
  }

  @After(event = DeploymentService.EVENT_SUBSCRIBE)
  public void afterSubscribe(SubscribeEventContext context) {
    String tenantId = context.getTenant();
    logger.debug("Creating AI Core resources for tenant {}", tenantId);
    try {
      String resourceGroupId = service.resourceGroupForTenant(tenantId);
      logger.info("Created AI Core resource group {} for tenant {}", resourceGroupId, tenantId);
    } catch (Exception e) {
      throw new ServiceException(
          ErrorStatuses.SERVER_ERROR,
          "Failed to create AI Core resources for tenant: {}",
          tenantId,
          e);
    }
  }

  @Before(event = DeploymentService.EVENT_UNSUBSCRIBE)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    logger.debug("Deleting AI Core resources for tenant {}", tenantId);
    try {
      deleteResourceGroupForTenant(tenantId);
    } finally {
      // Always evict cache entries so a retry won't reuse stale state.
      service.clearTenantCache(tenantId);
    }
  }

  private void deleteResourceGroupForTenant(String tenantId) {
    String resourceGroupId = resolveResourceGroupId(tenantId);
    if (resourceGroupId == null) {
      logger.info(
          "No AI Core resource group found for tenant {} (already deleted), nothing to do",
          tenantId);
      return;
    }
    try {
      service.getResourceGroupApi().delete(resourceGroupId);
      logger.info("Deleted AI Core resource group {} for tenant {}", resourceGroupId, tenantId);
    } catch (OpenApiRequestException e) {
      if (e.statusCode() != null && e.statusCode() == 404) {
        logger.info(
            "AI Core resource group {} for tenant {} already deleted (404), treating as success",
            resourceGroupId,
            tenantId);
        return;
      }
      throw new ServiceException(
          ErrorStatuses.SERVER_ERROR,
          "Failed to delete AI Core resource group {} for tenant {}",
          resourceGroupId,
          tenantId,
          e);
    }
  }

  /**
   * Resolves the resource-group ID for the tenant, first via the in-memory cache, then via the AI
   * Core API filtered by the tenant label. Returns {@code null} if no resource group is found.
   */
  private String resolveResourceGroupId(String tenantId) {
    String cached = service.getTenantResourceGroupCache().get(tenantId);
    if (cached != null) {
      return cached;
    }
    logger.debug(
        "No cached resource group for tenant {}, falling back to AI Core lookup", tenantId);
    ResourceGroupApi api = service.getResourceGroupApi();
    List<String> labelSelector = List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + tenantId);
    BckndResourceGroupList result;
    try {
      result = api.getAll(null, null, null, null, null, null, labelSelector);
    } catch (OpenApiRequestException e) {
      throw new ServiceException(
          ErrorStatuses.SERVER_ERROR,
          "Failed to look up AI Core resource group for tenant {}",
          tenantId,
          e);
    }
    List<BckndResourceGroup> resources = result.getResources();
    if (resources == null || resources.isEmpty()) {
      return null;
    }
    return resources.get(0).getResourceGroupId();
  }
}
