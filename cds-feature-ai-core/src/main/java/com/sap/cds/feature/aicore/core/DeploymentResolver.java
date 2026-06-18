/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful component that manages tenant-to-resource-group and resource-group-to-deployment caches,
 * per-key locks, and retry policies for AI Core operations.
 *
 * <p>Handlers interact with this class through intention-revealing operations ({@link
 * #resolveResourceGroup}, {@link #resolveDeployment}, {@link #invalidateTenant}) instead of
 * manipulating caches and locks directly.
 */
public class DeploymentResolver {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentResolver.class);

  private static final Duration DEFAULT_CACHE_EXPIRY = Duration.ofHours(1);
  private static final int DEFAULT_CACHE_MAX_SIZE = 10_000;
  private static final long MAX_INTERVAL_MS = 30_000L;

  private final Cache<String, String> tenantResourceGroupCache;
  private final Cache<String, String> deploymentCache;

  /**
   * Per-cache-key monitors guarding deployment lookup/creation. Stored in a {@link
   * ConcurrentHashMap} (not a Caffeine cache) so that two threads asking for the same key are
   * guaranteed to obtain the <em>same</em> monitor instance — locks must never live in a
   * size/time-evicting cache, otherwise concurrent callers can synchronize on different objects and
   * race to create duplicate AI Core deployments.
   */
  private final ConcurrentHashMap<String, Object> deploymentLocks = new ConcurrentHashMap<>();

  private final AICoreConfig config;
  private final DeploymentApi deploymentApi;
  private final ResourceGroupApi resourceGroupApi;
  private final Retry retry;

  public DeploymentResolver(
      AICoreConfig config, DeploymentApi deploymentApi, ResourceGroupApi resourceGroupApi) {
    this.config = config;
    this.deploymentApi = deploymentApi;
    this.resourceGroupApi = resourceGroupApi;
    this.retry = buildRetry(config.maxRetries(), config.initialDelayMs());
    this.tenantResourceGroupCache = newCache();
    this.deploymentCache = newCache();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Resource group resolution
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Resolves the resource group for a tenant. Returns the configured default resource group if
   * multi-tenancy is disabled or tenant is {@code null}. Otherwise looks up (or creates) the
   * tenant's resource group via the AI Core API, caching the result. Thread-safe.
   *
   * @param tenantId the CDS tenant identifier (may be {@code null})
   * @return the AI Core resource group ID
   */
  public String resolveResourceGroup(String tenantId) {
    if (!config.multiTenancyEnabled() || tenantId == null) {
      return config.defaultResourceGroup();
    }
    return tenantResourceGroupCache.get(tenantId, this::findOrCreateResourceGroup);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Deployment resolution
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Resolves a deployment ID for the given spec within a resource group. On cache hit, validates
   * via {@link DeploymentApi#get} that the deployment is still RUNNING or PENDING. On cache miss or
   * stale entry, acquires a per-key lock and calls the {@code loader} to find or create the
   * deployment. The result is cached.
   *
   * @param resourceGroupId the AI Core resource group
   * @param spec the deployment specification
   * @param loader supplier that finds an existing or creates a new deployment — called under lock
   *     on cache miss
   * @return the deployment ID
   */
  public String resolveDeployment(
      String resourceGroupId, ModelDeploymentSpec spec, Supplier<String> loader) {
    String cacheKey = deploymentCacheKey(resourceGroupId, spec);
    Object lock = deploymentLocks.computeIfAbsent(cacheKey, k -> new Object());

    synchronized (lock) {
      String cached = deploymentCache.getIfPresent(cacheKey);
      if (cached != null) {
        if (validateCachedDeployment(resourceGroupId, cached)) {
          return cached;
        }
        deploymentCache.invalidate(cacheKey);
      }

      String deploymentId = loader.get();
      deploymentCache.put(cacheKey, deploymentId);
      return deploymentId;
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Cache management
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Evicts all cache entries associated with the given tenant: the resource-group mapping, all
   * deployments in that resource group, and their lock entries.
   */
  public void invalidateTenant(String tenantId) {
    String resourceGroupId = tenantResourceGroupCache.asMap().remove(tenantId);
    if (resourceGroupId != null) {
      String prefix = resourceGroupId + "::";
      deploymentCache
          .asMap()
          .keySet()
          .removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
      deploymentLocks.keySet().removeIf(k -> k.equals(resourceGroupId) || k.startsWith(prefix));
    }
  }

  /** Returns the shared {@link Retry} for wrapping transient AI Core operations. */
  public Retry getRetry() {
    return retry;
  }

  /**
   * Returns an unmodifiable view of the tenant-to-resource-group cache. Primarily for diagnostics
   * and the setup handler's unsubscribe logic.
   */
  public Map<String, String> getTenantResourceGroupCacheView() {
    return Collections.unmodifiableMap(tenantResourceGroupCache.asMap());
  }

  /** Builds the cache key for deployment lookups. */
  static String deploymentCacheKey(String resourceGroupId, ModelDeploymentSpec spec) {
    return resourceGroupId + "::" + spec.configurationName();
  }

  /** Returns whether the given {@link OpenApiRequestException} indicates a transient state. */
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

  // ──────────────────────────────────────────────────────────────────────────
  // Internal
  // ──────────────────────────────────────────────────────────────────────────

  private String findOrCreateResourceGroup(String tenantId) {
    List<String> labelSelector = List.of(AICoreConfig.TENANT_LABEL_KEY + "=" + tenantId);
    BckndResourceGroupList result =
        resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
    List<BckndResourceGroup> resources = result.getResources();
    if (resources != null && !resources.isEmpty()) {
      return resources.get(0).getResourceGroupId();
    }
    String resourceGroupId = config.resourceGroupPrefix() + tenantId;
    BckndResourceGroupLabel label =
        BckndResourceGroupLabel.create().key(AICoreConfig.TENANT_LABEL_KEY).value(tenantId);
    BckndResourceGroupsPostRequest request =
        BckndResourceGroupsPostRequest.create()
            .resourceGroupId(resourceGroupId)
            .labels(List.of(label));
    try {
      resourceGroupApi.create(request);
      logger.debug("Created resource group {} for tenant {}", resourceGroupId, tenantId);
    } catch (OpenApiRequestException e) {
      if (e.statusCode() != null && e.statusCode() == 409) {
        logger.debug("Resource group {} already exists (409 Conflict), reusing", resourceGroupId);
      } else {
        throw e;
      }
    }
    return resourceGroupId;
  }

  /**
   * Validates that a cached deployment ID is still active (RUNNING or PENDING). Returns {@code
   * true} if valid, {@code false} if stale (404). Throws on unexpected errors so the caller's
   * retry/backoff policy can handle them.
   */
  private boolean validateCachedDeployment(String resourceGroupId, String deploymentId) {
    try {
      var current = deploymentApi.get(resourceGroupId, deploymentId);
      return AiDeploymentStatus.RUNNING.equals(current.getStatus())
          || AiDeploymentStatus.PENDING.equals(current.getStatus());
    } catch (OpenApiRequestException e) {
      Integer status = e.statusCode();
      if (status != null && status == 404) {
        logger.debug(
            "Cached deployment {} in resource group {} no longer exists (404), invalidating",
            deploymentId,
            resourceGroupId);
        return false;
      }
      throw e;
    }
  }

  private static <V> Cache<String, V> newCache() {
    return Caffeine.newBuilder()
        .maximumSize(DEFAULT_CACHE_MAX_SIZE)
        .expireAfterAccess(DEFAULT_CACHE_EXPIRY)
        .build();
  }

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
