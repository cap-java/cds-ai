/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

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
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ON handler for the {@link AICoreService} API events ({@code resourceGroup}, {@code deploymentId},
 * {@code inferenceClient}).
 *
 * <p>Contains the implementation logic previously housed directly in {@link AICoreServiceImpl}. The
 * handler accesses shared state (caches, API clients, configuration) via the service instance
 * obtained from the {@link com.sap.cds.services.EventContext}.
 */
@ServiceName(AICoreService.DEFAULT_NAME)
public class AICoreApiHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AICoreApiHandler.class);

  // ──────────────────────────────────────────────────────────────────────────
  // ON handlers
  // ──────────────────────────────────────────────────────────────────────────

  @On
  public void onResourceGroup(ResourceGroupContext context) {
    AICoreServiceImpl service = (AICoreServiceImpl) context.getService();
    String tenantId = context.getTenantId();
    if (tenantId == null) {
      tenantId = service.currentTenantId();
    }
    if (!service.isMultiTenancyEnabled() || tenantId == null) {
      logger.debug("Using default resource group {}", service.getDefaultResourceGroup());
      context.setResult(service.getDefaultResourceGroup());
      return;
    }
    String result = getOrCreateResourceGroupForTenant(service, tenantId);
    context.setResult(result);
  }

  @On
  public void onDeploymentId(DeploymentIdContext context) {
    AICoreServiceImpl service = (AICoreServiceImpl) context.getService();
    String resourceGroupId = context.getResourceGroupId();
    ModelDeploymentSpec spec = context.getSpec();

    String cacheKey = AICoreServiceImpl.deploymentCacheKey(resourceGroupId, spec);
    Object lock = service.getDeploymentLocks().computeIfAbsent(cacheKey, k -> new Object());
    synchronized (lock) {
      String cached =
          service.getResourceGroupDeploymentCaffeineCache().getIfPresent(cacheKey);
      if (cached != null) {
        try {
          var current = service.getDeploymentApi().get(resourceGroupId, cached);
          if (AiDeploymentStatus.RUNNING.equals(current.getStatus())
              || AiDeploymentStatus.PENDING.equals(current.getStatus())) {
            context.setResult(cached);
            return;
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
        service.getResourceGroupDeploymentCaffeineCache().invalidate(cacheKey);
      }
      AiDeploymentList deploymentList = queryDeploymentsUntilReady(service, resourceGroupId, spec);
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
        service.getResourceGroupDeploymentCaffeineCache().put(cacheKey, deploymentId);
        context.setResult(deploymentId);
        return;
      }
      String deploymentId = createDeployment(service, resourceGroupId, spec, cacheKey);
      context.setResult(deploymentId);
    }
  }

  @On
  public void onInferenceClient(InferenceClientContext context) {
    AICoreServiceImpl service = (AICoreServiceImpl) context.getService();
    var destination =
        service
            .getSdkService()
            .getInferenceDestination(context.getResourceGroupId())
            .usingDeploymentId(context.getDeploymentId());
    logger.debug("Inference destination URI: {}", destination.getUri());
    context.setResult(ApiClient.create(destination));
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Private implementation helpers (moved from AICoreServiceImpl)
  // ──────────────────────────────────────────────────────────────────────────

  private String getOrCreateResourceGroupForTenant(AICoreServiceImpl service, String tenantId) {
    return service
        .getTenantResourceGroupCaffeineCache()
        .get(
            tenantId,
            key -> {
              ResourceGroupApi resourceGroupApi = service.getResourceGroupApi();
              List<String> labelSelector =
                  List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + key);
              BckndResourceGroupList result =
                  resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
              List<BckndResourceGroup> resources = result.getResources();
              if (resources != null && !resources.isEmpty()) {
                return resources.get(0).getResourceGroupId();
              }
              String resourceGroupId = service.getResourceGroupPrefix() + key;
              BckndResourceGroupLabel label =
                  BckndResourceGroupLabel.create()
                      .key(AICoreServiceImpl.TENANT_LABEL_KEY)
                      .value(key);
              BckndResourceGroupsPostRequest request =
                  BckndResourceGroupsPostRequest.create()
                      .resourceGroupId(resourceGroupId)
                      .labels(List.of(label));
              try {
                resourceGroupApi.create(request);
                logger.debug(
                    "Created resource group {} for tenant {}", resourceGroupId, key);
              } catch (OpenApiRequestException e) {
                if (e.statusCode() != null && e.statusCode() == 409) {
                  logger.debug(
                      "Resource group {} already exists (409 Conflict), reusing",
                      resourceGroupId);
                } else {
                  throw e;
                }
              }
              return resourceGroupId;
            });
  }

  private String createDeployment(
      AICoreServiceImpl service,
      String resourceGroupId,
      ModelDeploymentSpec spec,
      String cacheKey) {
    DeploymentApi deploymentApi = service.getDeploymentApi();
    ConfigurationApi configurationApi = service.getConfigurationApi();

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
            .orElseGet(() -> createConfiguration(service, resourceGroupId, spec));

    Retry retry = service.getRetry();
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
              return pollUntilRunning(service, resourceGroupId, deploymentId, cacheKey);
            })
        .get();
  }

  private String createConfiguration(
      AICoreServiceImpl service, String resourceGroupId, ModelDeploymentSpec spec) {
    ConfigurationApi configurationApi = service.getConfigurationApi();
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

  private String pollUntilRunning(
      AICoreServiceImpl service,
      String resourceGroupId,
      String deploymentId,
      String cacheKey) {
    DeploymentApi deploymentApi = service.getDeploymentApi();
    int maxRetries = service.getMaxRetries();
    long initialDelayMs = service.getInitialDelayMs();

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
      service.getResourceGroupDeploymentCaffeineCache().put(cacheKey, deploymentId);
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
      AICoreServiceImpl service, String resourceGroupId, ModelDeploymentSpec spec) {
    DeploymentApi deploymentApi = service.getDeploymentApi();
    Retry retry = service.getRetry();
    return Retry.decorateSupplier(
            retry,
            () ->
                deploymentApi.query(
                    resourceGroupId, null, null, spec.scenarioId(), null, null, null, null))
        .get();
  }
}
