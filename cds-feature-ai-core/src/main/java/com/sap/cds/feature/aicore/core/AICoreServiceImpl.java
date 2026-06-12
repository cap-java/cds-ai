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
import com.sap.ai.sdk.core.model.AiConfigurationBaseData;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentResponseWithDetails;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production implementation of {@link AICoreService} backed by an SAP AI Core service binding.
 *
 * <p>Provides resource-group, configuration and deployment lifecycle management together with a
 * factory for inference {@link ApiClient}s scoped to a specific deployment. Resource group lookup
 * results, deployment IDs and per-cache-key locks are cached in bounded {@link Caffeine} caches so
 * repeated calls within a tenant or resource group avoid round-trips to AI Core.
 *
 * <p>Most state-changing AI Core calls are wrapped in a Resilience4j {@link Retry} that retries
 * known transient errors (HTTP 403/404/412, see {@link #notReadyYet(OpenApiRequestException)}) with
 * exponential backoff capped at 30 seconds.
 */
public class AICoreServiceImpl extends AbstractAICoreService {

  private static final Logger logger = LoggerFactory.getLogger(AICoreServiceImpl.class);

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

  @Override
  public String resourceGroupForTenant(String tenantId) {
    if (!multiTenancyEnabled || tenantId == null) {
      logger.debug("Using default resource group {}", defaultResourceGroup);
      return defaultResourceGroup;
    }
    return getOrCreateResourceGroupForTenant(tenantId);
  }

  @Override
  public String deploymentId(String resourceGroupId, ModelDeploymentSpec spec) {
    String cacheKey = deploymentCacheKey(resourceGroupId, spec);
    Object lock = deploymentLocks.computeIfAbsent(cacheKey, k -> new Object());
    synchronized (lock) {
      String cached = resourceGroupDeploymentCache.getIfPresent(cacheKey);
      if (cached != null) {
        try {
          var current = deploymentApi.get(resourceGroupId, cached);
          if (AiDeploymentStatus.RUNNING.equals(current.getStatus())
              || AiDeploymentStatus.PENDING.equals(current.getStatus())) {
            return cached;
          }
        } catch (OpenApiRequestException e) {
          // Only 404 means the cached deployment was deleted out-of-band — drop the stale entry
          // and fall through to discover or create a new one. Any other status (5xx, 401, 412,
          // network errors, …) is propagated so the caller's retry/backoff policy can handle it
          // rather than silently invalidating a potentially valid cache entry and triggering a
          // duplicate deployment.
          Integer status = e.statusCode();
          if (status == null || status != 404) {
            throw e;
          }
          logger.debug(
              "Cached deployment {} in resource group {} no longer exists (404), "
                  + "invalidating cache entry",
              cached,
              resourceGroupId);
        }
        resourceGroupDeploymentCache.invalidate(cacheKey);
      }
      AiDeploymentList deploymentList = queryDeploymentsUntilReady(resourceGroupId, spec);
      Optional<String> existing =
          deploymentList.getResources().stream()
              .filter(
                  d ->
                      spec.configurationName().equals(d.getConfigurationName())
                          && spec.matchesExisting().test(d)
                          && (AiDeploymentStatus.RUNNING.equals(d.getStatus())
                              || AiDeploymentStatus.PENDING.equals(d.getStatus())))
              .findFirst()
              .map(AiDeployment::getId);
      if (existing.isPresent()) {
        String deploymentId = existing.get();
        resourceGroupDeploymentCache.put(cacheKey, deploymentId);
        return deploymentId;
      }
      return createDeployment(resourceGroupId, spec, cacheKey);
    }
  }

  @Override
  public ApiClient inferenceClient(String resourceGroupId, String deploymentId) {
    var destination =
        sdkService.getInferenceDestination(resourceGroupId).usingDeploymentId(deploymentId);
    logger.debug("Inference destination URI: {}", destination.getUri());
    return ApiClient.create(destination);
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

  /**
   * Builds the cache key for the {@code resourceGroupDeploymentCache} and {@code deploymentLocks}
   * maps. Package-private so tests can derive the same key the production code uses, instead of
   * duplicating the format inline.
   */
  static String deploymentCacheKey(String resourceGroupId, ModelDeploymentSpec spec) {
    return resourceGroupId + "::" + spec.configurationName();
  }

  private String getOrCreateResourceGroupForTenant(String tenantId) {
    return tenantResourceGroupCache.get(
        tenantId,
        key -> {
          List<String> labelSelector = List.of(TENANT_LABEL_KEY + "=" + key);
          BckndResourceGroupList result =
              resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
          List<BckndResourceGroup> resources = result.getResources();
          if (resources != null && !resources.isEmpty()) {
            return resources.get(0).getResourceGroupId();
          }
          String resourceGroupId = resourceGroupPrefix + key;
          BckndResourceGroupLabel label =
              BckndResourceGroupLabel.create().key(TENANT_LABEL_KEY).value(key);
          BckndResourceGroupsPostRequest request =
              BckndResourceGroupsPostRequest.create()
                  .resourceGroupId(resourceGroupId)
                  .labels(List.of(label));
          try {
            resourceGroupApi.create(request);
            logger.debug("Created resource group {} for tenant {}", resourceGroupId, key);
          } catch (OpenApiRequestException e) {
            if (e.statusCode() != null && e.statusCode() == 409) {
              logger.debug(
                  "Resource group {} already exists (409 Conflict), reusing", resourceGroupId);
            } else {
              throw e;
            }
          }
          return resourceGroupId;
        });
  }

  private String createDeployment(
      String resourceGroupId, ModelDeploymentSpec spec, String cacheKey) {
    AiConfigurationList configList =
        configurationApi.query(
            resourceGroupId, spec.scenarioId(), null, null, null, null, null, null);
    String configId =
        configList.getResources().stream()
            .filter(c -> spec.configurationName().equals(c.getName()))
            .findFirst()
            .map(
                c -> {
                  logger.debug(
                      "Reusing existing configuration {} ({}) in resource group {}",
                      c.getId(),
                      spec.configurationName(),
                      resourceGroupId);
                  return c.getId();
                })
            .orElseGet(() -> createConfiguration(resourceGroupId, spec));

    return Retry.decorateSupplier(
            retry,
            () -> {
              var deployRequest = AiDeploymentCreationRequest.create().configurationId(configId);
              var deployResponse = deploymentApi.create(resourceGroupId, deployRequest);
              String deploymentId = deployResponse.getId();
              logger.debug(
                  "Created deployment {} ({}) in resource group {}, polling for RUNNING",
                  deploymentId,
                  spec.configurationName(),
                  resourceGroupId);
              return pollUntilRunning(resourceGroupId, deploymentId, cacheKey);
            })
        .get();
  }

  private String createConfiguration(String resourceGroupId, ModelDeploymentSpec spec) {
    AiConfigurationBaseData configRequest =
        AiConfigurationBaseData.create()
            .name(spec.configurationName())
            .executableId(spec.executableId())
            .scenarioId(spec.scenarioId())
            .parameterBindings(spec.parameterBindings());
    String configId = configurationApi.create(resourceGroupId, configRequest).getId();
    logger.debug(
        "Created configuration {} ({}) in resource group {}",
        configId,
        spec.configurationName(),
        resourceGroupId);
    return configId;
  }

  private String pollUntilRunning(String resourceGroupId, String deploymentId, String cacheKey) {
    Retry pollRetry =
        Retry.of(
            "pollDeployment",
            RetryConfig.<AiDeploymentResponseWithDetails>custom()
                .maxAttempts(maxRetries)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(initialDelayMs, 2.0))
                .retryOnResult(
                    deployment -> !AiDeploymentStatus.RUNNING.equals(deployment.getStatus()))
                .retryOnException(e -> false)
                .build());

    AiDeploymentResponseWithDetails result =
        Retry.decorateSupplier(
                pollRetry,
                () -> {
                  var current = deploymentApi.get(resourceGroupId, deploymentId);
                  logger.debug("Deployment {} status: {}", deploymentId, current.getStatus());
                  return current;
                })
            .get();

    if (AiDeploymentStatus.RUNNING.equals(result.getStatus())) {
      resourceGroupDeploymentCache.put(cacheKey, deploymentId);
      return deploymentId;
    }
    logger.error(
        "Deployment {} in resource group {} did not reach RUNNING status after {} retries",
        deploymentId,
        resourceGroupId,
        maxRetries);
    throw new ServiceException(
        ErrorStatuses.GATEWAY_TIMEOUT, "AI model deployment is not available");
  }

  private AiDeploymentList queryDeploymentsUntilReady(
      String resourceGroupId, ModelDeploymentSpec spec) {
    return Retry.decorateSupplier(
            retry,
            () ->
                deploymentApi.query(
                    resourceGroupId, null, null, spec.scenarioId(), null, null, null, null))
        .get();
  }

  static boolean notReadyYet(OpenApiRequestException e) {
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
