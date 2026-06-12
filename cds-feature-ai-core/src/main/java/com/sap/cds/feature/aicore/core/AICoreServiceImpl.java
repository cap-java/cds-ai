/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of {@link AICoreService} backed by an SAP AI Core service binding.
 *
 * <p>Provides resource-group, configuration and deployment lifecycle management together with a
 * factory for inference {@link ApiClient}s scoped to a specific deployment. Resource group lookup
 * results, deployment IDs and per-cache-key locks are cached in bounded {@link Caffeine} caches so
 * repeated calls within a tenant or resource group avoid round-trips to AI Core.
 *
 * <p>The API methods ({@link #resourceGroup()}, {@link #deploymentId(String, ModelDeploymentSpec)},
 * {@link #inferenceClient(String, String)}) are thin emitters that delegate to registered ON
 * handlers via the CAP event mechanism.
 */
public class AICoreServiceImpl extends AbstractAICoreService {

  public static final String TENANT_LABEL_KEY = "ext.ai.sap.com/CDS_TENANT_ID";

  private static final String DEFAULT_RESOURCE_GROUP = "default";
  private static final String DEFAULT_RESOURCE_GROUP_PREFIX = "cds-";
  private static final int DEFAULT_MAX_RETRIES = 10;
  private static final long DEFAULT_INITIAL_DELAY_MS = 300;
  private static final Duration DEFAULT_CACHE_EXPIRY = Duration.ofHours(1);
  private static final int DEFAULT_CACHE_MAX_SIZE = 10_000;

  private final Cache<String, String> tenantResourceGroupCache;
  private final Cache<String, String> resourceGroupDeploymentCache;

  /**
   * Per-cache-key monitors guarding deployment lookup/creation. Stored in a {@link
   * ConcurrentHashMap} (not a Caffeine cache) so that two threads asking for the same key are
   * guaranteed to obtain the <em>same</em> monitor instance — locks must never live in a
   * size/time-evicting cache, otherwise concurrent callers can synchronize on different objects and
   * race to create duplicate AI Core deployments.
   */
  private final ConcurrentHashMap<String, Object> deploymentLocks = new ConcurrentHashMap<>();

  private final int maxRetries;
  private final long initialDelayMs;
  private final String defaultResourceGroup;
  private final String resourceGroupPrefix;
  private final boolean multiTenancyEnabled;
  private final Retry retry;
  private final DeploymentApi deploymentApi;
  private final ConfigurationApi configurationApi;
  private final ResourceGroupApi resourceGroupApi;
  private final AiCoreService sdkService;

  public AICoreServiceImpl(
      String name,
      CdsRuntime runtime,
      boolean multiTenancyEnabled,
      DeploymentApi deploymentApi,
      ConfigurationApi configurationApi,
      ResourceGroupApi resourceGroupApi,
      AiCoreService sdkService) {
    super(name, runtime);
    this.multiTenancyEnabled = multiTenancyEnabled;
    CdsEnvironment env = runtime.getEnvironment();
    this.maxRetries =
        env.getProperty("cds.ai.core.maxRetries", Integer.class, DEFAULT_MAX_RETRIES);
    this.initialDelayMs =
        env.getProperty("cds.ai.core.initialDelayMs", Long.class, DEFAULT_INITIAL_DELAY_MS);
    this.defaultResourceGroup =
        env.getProperty("cds.ai.core.resourceGroup", String.class, DEFAULT_RESOURCE_GROUP);
    this.resourceGroupPrefix =
        env.getProperty(
            "cds.ai.core.resourceGroupPrefix", String.class, DEFAULT_RESOURCE_GROUP_PREFIX);
    this.retry = buildRetry(maxRetries, initialDelayMs);
    this.tenantResourceGroupCache = newCache();
    this.resourceGroupDeploymentCache = newCache();
    this.deploymentApi = deploymentApi;
    this.configurationApi = configurationApi;
    this.resourceGroupApi = resourceGroupApi;
    this.sdkService = sdkService;
  }

  private static <V> Cache<String, V> newCache() {
    return Caffeine.newBuilder()
        .maximumSize(DEFAULT_CACHE_MAX_SIZE)
        .expireAfterAccess(DEFAULT_CACHE_EXPIRY)
        .build();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Thin API methods — emit EventContext and return the handler's result
  // ──────────────────────────────────────────────────────────────────────────

  @Override
  public String resourceGroup() {
    ResourceGroupContext ctx = ResourceGroupContext.create();
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public String resourceGroupForTenant(String tenantId) {
    ResourceGroupContext ctx = ResourceGroupContext.create();
    ctx.setTenantId(tenantId);
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public String deploymentId(String resourceGroupId, ModelDeploymentSpec spec) {
    DeploymentIdContext ctx = DeploymentIdContext.create();
    ctx.setResourceGroupId(resourceGroupId);
    ctx.setSpec(spec);
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public ApiClient inferenceClient(String resourceGroupId, String deploymentId) {
    InferenceClientContext ctx = InferenceClientContext.create();
    ctx.setResourceGroupId(resourceGroupId);
    ctx.setDeploymentId(deploymentId);
    emit(ctx);
    return ctx.getResult();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Shared state accessors (used by handlers)
  // ──────────────────────────────────────────────────────────────────────────

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
    return tenantResourceGroupCache.asMap();
  }

  @Override
  public Map<String, String> getResourceGroupDeploymentCache() {
    return resourceGroupDeploymentCache.asMap();
  }

  public DeploymentApi getDeploymentApi() {
    return deploymentApi;
  }

  public ConfigurationApi getConfigurationApi() {
    return configurationApi;
  }

  public ResourceGroupApi getResourceGroupApi() {
    return resourceGroupApi;
  }

  public AiCoreService getSdkService() {
    return sdkService;
  }

  public ConcurrentHashMap<String, Object> getDeploymentLocks() {
    return deploymentLocks;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public long getInitialDelayMs() {
    return initialDelayMs;
  }

  /**
   * Returns the underlying Caffeine cache for tenant-to-resource-group mappings. Exposed for use by
   * the {@code AICoreApiHandler} which needs the atomic {@code get(key, loader)} method.
   */
  public Cache<String, String> getTenantResourceGroupCaffeineCache() {
    return tenantResourceGroupCache;
  }

  /**
   * Returns the underlying Caffeine cache for resource-group-to-deployment mappings. Exposed for
   * use by the {@code AICoreApiHandler} which needs direct cache operations.
   */
  public Cache<String, String> getResourceGroupDeploymentCaffeineCache() {
    return resourceGroupDeploymentCache;
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
    return resourceGroup();
  }

  @Override
  public void clearTenantCache(String tenantId) {
    String resourceGroupId = tenantResourceGroupCache.asMap().remove(tenantId);
    if (resourceGroupId != null) {
      String prefix = resourceGroupId + "::";
      resourceGroupDeploymentCache
          .asMap()
          .keySet()
          .removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
      deploymentLocks.keySet().removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Static helpers
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Builds the cache key for the {@code resourceGroupDeploymentCache} and {@code deploymentLocks}
   * maps. Public so that handlers and tests can derive the same key the production code uses,
   * instead of duplicating the format inline.
   */
  public static String deploymentCacheKey(String resourceGroupId, ModelDeploymentSpec spec) {
    return resourceGroupId + "::" + spec.configurationName();
  }

  public static boolean notReadyYet(OpenApiRequestException e) {
    Throwable t = e;
    while (t != null) {
      if (t instanceof OpenApiRequestException oae) {
        Integer code = oae.statusCode();
        if (code != null && (code == 403 || code == 404 || code == 412)) {
          return true;
        }
      }
      t = t.getCause();
    }
    return false;
  }

  private static final long MAX_INTERVAL_MS = 30_000L;

  private static Retry buildRetry(int maxAttempts, long initialDelayMs) {
    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(initialDelayMs, 2.0, MAX_INTERVAL_MS))
            .retryOnException(e -> e instanceof OpenApiRequestException oae && notReadyYet(oae))
            .build();
    return Retry.of("aicore", config);
  }
}
