/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
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
