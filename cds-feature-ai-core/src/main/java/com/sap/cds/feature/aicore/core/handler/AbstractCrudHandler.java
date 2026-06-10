/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

abstract class AbstractCrudHandler implements EventHandler {

  protected final AICoreServiceImpl service;

  protected AbstractCrudHandler(AICoreServiceImpl service) {
    this.service = service;
  }

  protected String resolveResourceGroup(Map<String, Object> keys) {
    return service.resolveResourceGroupFromKeys(keys);
  }

  /**
   * Validates that the given resource group is accessible by the current tenant. Provider/system
   * users may access any resource group. In single-tenancy mode, no restriction is applied. Throws
   * 404 if the resource group does not belong to the current tenant.
   */
  protected void ensureResourceGroupAccessible(String resourceGroupId) {
    if (service.isProviderUser() || !service.isMultiTenancyEnabled()) {
      return;
    }
    String currentTenant = service.currentTenantId();
    if (currentTenant == null) {
      return;
    }
    BckndResourceGroup rg = service.getResourceGroupApi().get(resourceGroupId);
    if (rg.getLabels() != null
        && rg.getLabels().stream()
            .anyMatch(
                l ->
                    AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.getKey())
                        && currentTenant.equals(l.getValue()))) {
      return;
    }
    throw new ServiceException(ErrorStatuses.NOT_FOUND, "Resource not found");
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
