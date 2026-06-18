/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.AICore_;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock ON handler for the AI Core service API events when no AI Core binding is available. Uses
 * in-memory maps instead of real API calls.
 */
@ServiceName(AICore_.CDS_NAME)
public class MockAICoreApiHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockAICoreApiHandler.class);

  private final AICoreConfig config;
  private final Map<String, String> tenantResourceGroupCache = new ConcurrentHashMap<>();
  private final Map<String, String> deploymentCache = new ConcurrentHashMap<>();

  public MockAICoreApiHandler(AICoreConfig config) {
    this.config = config;
  }

  @On
  public void onResourceGroup(ResourceGroupContext context) {
    String tenantId = context.getTenantId();
    if (tenantId == null) {
      tenantId = context.getUserInfo().getTenant();
    }
    if (!config.multiTenancyEnabled() || tenantId == null) {
      context.setResult(config.defaultResourceGroup());
      return;
    }
    context.setResult(resolveResourceGroup(tenantId));
  }

  @On
  public void onDeploymentId(DeploymentIdContext context) {
    String resourceGroupId = context.getResourceGroupId();
    ModelDeploymentSpec spec = context.getSpec();
    String key = resourceGroupId + "::" + spec.configurationName();
    String result = deploymentCache.computeIfAbsent(key, k -> "mock-deployment-" + k);
    context.setResult(result);
  }

  @On
  public void onInferenceClient(InferenceClientContext context) {
    throw new ServiceException(
        ErrorStatuses.NOT_IMPLEMENTED,
        "Inference client is not available without an AI Core service binding");
  }

  /** Resolves (or creates) the resource group name for the given tenant using the configured prefix. */
  public String resolveResourceGroup(String tenantId) {
    return tenantResourceGroupCache.computeIfAbsent(tenantId, id -> config.resourceGroupPrefix() + id);
  }

  /** Returns the mock tenant cache for test inspection. */
  public Map<String, String> getTenantResourceGroupCache() {
    return tenantResourceGroupCache;
  }

  /** Returns the mock deployment cache for test inspection. */
  public Map<String, String> getDeploymentCache() {
    return deploymentCache;
  }

  /** Evicts all entries for the given tenant. */
  public void clearTenantCache(String tenantId) {
    String resourceGroupId = tenantResourceGroupCache.remove(tenantId);
    if (resourceGroupId != null) {
      String prefix = resourceGroupId + "::";
      deploymentCache.keySet().removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
    }
  }
}
