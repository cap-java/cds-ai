/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.request.UserInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

abstract class AbstractCrudHandler implements EventHandler {

  protected final AICoreConfig config;
  protected final AICoreClients clients;

  protected AbstractCrudHandler(AICoreConfig config, AICoreClients clients) {
    this.config = config;
    this.clients = clients;
  }

  /**
   * Resolves the resource group ID from CQN keys. Checks for an explicit resource-group reference
   * in the keys before falling back to the current tenant's default resource group via the service.
   */
  protected String resolveResourceGroup(EventContext context, Map<String, Object> keys) {
    if (keys.containsKey("resourceGroup_resourceGroupId")) {
      return (String) keys.get("resourceGroup_resourceGroupId");
    }
    Object rgObj = keys.get("resourceGroup");
    if (rgObj instanceof Map<?, ?> rgMap && rgMap.containsKey("resourceGroupId")) {
      return (String) rgMap.get("resourceGroupId");
    }
    // Fall back to the service's resource-group resolution for the current tenant
    return ((AICoreService) context.getService()).resourceGroup();
  }

  /**
   * Validates that the given resource group is accessible by the current tenant. Provider/system
   * users may access any resource group. In single-tenancy mode, no restriction is applied. Throws
   * 404 if the resource group does not belong to the current tenant.
   */
  protected void ensureResourceGroupAccessible(EventContext context, String resourceGroupId) {
    if (isProviderUser(context) || !config.multiTenancyEnabled()) {
      return;
    }
    String currentTenant = context.getUserInfo().getTenant();
    if (currentTenant == null) {
      return;
    }
    BckndResourceGroup rg = clients.resourceGroupApi().get(resourceGroupId);
    if (rg.getLabels() != null
        && rg.getLabels().stream()
            .anyMatch(
                l ->
                    AICoreConfig.TENANT_LABEL_KEY.equals(l.getKey())
                        && currentTenant.equals(l.getValue()))) {
      return;
    }
    throw new ServiceException(ErrorStatuses.NOT_FOUND, "Resource not found");
  }

  /**
   * Returns whether the current request user is a system/provider user (bypasses tenant checks).
   */
  protected static boolean isProviderUser(EventContext context) {
    UserInfo userInfo = context.getUserInfo();
    return userInfo.isSystemUser() || userInfo.isInternalUser();
  }

  protected static Map<String, Object> merge(Map<String, Object> keys, Map<String, Object> values) {
    Map<String, Object> merged = new HashMap<>(values);
    keys.forEach(
        (k, v) -> {
          if (v != null) merged.put(k, v);
        });
    return merged;
  }

  protected static <T, R> List<R> mapResources(List<T> resources, Function<T, R> mapper) {
    if (resources == null) return List.of();
    return resources.stream().map(mapper).toList();
  }
}
