/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockAICoreServiceImpl extends AbstractAICoreService {

  private static final Logger logger = LoggerFactory.getLogger(MockAICoreServiceImpl.class);

  private final Map<String, String> tenantResourceGroupCache = new ConcurrentHashMap<>();
  private final Map<String, String> resourceGroupDeploymentCache = new ConcurrentHashMap<>();
  private final Retry retry;
  private final String defaultResourceGroup;
  private final String resourceGroupPrefix;
  private final boolean multiTenancyEnabled;

  public MockAICoreServiceImpl(String name, CdsRuntime runtime) {
    this(name, runtime, false);
  }

  public MockAICoreServiceImpl(String name, CdsRuntime runtime, boolean multiTenancyEnabled) {
    super(name, runtime);
    logger.info("MockAICoreService initialized - all operations use in-memory storage.");
    this.retry = Retry.of("mock-aicore", RetryConfig.custom().maxAttempts(1).build());
    CdsEnvironment env = runtime.getEnvironment();
    this.defaultResourceGroup =
        env.getProperty("cds.ai.core.resourceGroup", String.class, "default");
    this.resourceGroupPrefix =
        env.getProperty("cds.ai.core.resourceGroupPrefix", String.class, "cds-");
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  @Override
  public String resourceGroupForTenant(String tenantId) {
    if (!multiTenancyEnabled) {
      return defaultResourceGroup;
    }
    return tenantResourceGroupCache.computeIfAbsent(tenantId, id -> resourceGroupPrefix + id);
  }

  @Override
  public String deploymentId(String resourceGroupId, ModelDeploymentSpec spec) {
    String key = resourceGroupId + "::" + spec.configurationName();
    return resourceGroupDeploymentCache.computeIfAbsent(key, k -> "mock-deployment-" + k);
  }

  @Override
  public ApiClient inferenceClient(String resourceGroupId, String deploymentId) {
    throw new UnsupportedOperationException(
        "MockAICoreServiceImpl does not provide an inference client; tests should stub inference.");
  }

  @Override
  public boolean isMultiTenancyEnabled() {
    return multiTenancyEnabled;
  }

  @Override
  public Retry getRetry() {
    return retry;
  }

  @Override
  public String getDefaultResourceGroup() {
    return defaultResourceGroup;
  }

  @Override
  public String getResourceGroupPrefix() {
    return resourceGroupPrefix;
  }

  @Override
  public Map<String, String> getTenantResourceGroupCache() {
    return tenantResourceGroupCache;
  }

  @Override
  public Map<String, String> getResourceGroupDeploymentCache() {
    return resourceGroupDeploymentCache;
  }

  @Override
  public void clearTenantCache(String tenantId) {
    String resourceGroupId = tenantResourceGroupCache.remove(tenantId);
    if (resourceGroupId != null) {
      String prefix = resourceGroupId + "::";
      resourceGroupDeploymentCache
          .keySet()
          .removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
    }
  }

  @Override
  public String resolveResourceGroupFromKeys(Map<String, Object> keys) {
    if (keys.containsKey("resourceGroup_resourceGroupId")) {
      return (String) keys.get("resourceGroup_resourceGroupId");
    }
    Object rgObj = keys.get("resourceGroup");
    if (rgObj instanceof Map<?, ?> rgMap && rgMap.containsKey("resourceGroupId")) {
      return (String) rgMap.get("resourceGroupId");
    }
    return defaultResourceGroup;
  }
}
