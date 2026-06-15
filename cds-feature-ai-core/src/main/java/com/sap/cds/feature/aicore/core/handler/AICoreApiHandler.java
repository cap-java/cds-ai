/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.model.AiConfigurationBaseData;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentResponseWithDetails;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.core.DeploymentResolver;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ON handler for the {@link AICoreService} API events ({@code resourceGroup}, {@code deploymentId},
 * {@code inferenceClient}).
 *
 * <p>Contains the business logic for deployment discovery/creation and inference client
 * construction. Resource-group resolution is delegated to {@link DeploymentResolver}.
 */
@ServiceName(AICoreService.DEFAULT_NAME)
public class AICoreApiHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AICoreApiHandler.class);

  private final AICoreConfig config;
  private final AICoreClients clients;
  private final DeploymentResolver resolver;

  public AICoreApiHandler(AICoreConfig config, AICoreClients clients, DeploymentResolver resolver) {
    this.config = config;
    this.clients = clients;
    this.resolver = resolver;
  }

  @On
  public void onResourceGroup(ResourceGroupContext context) {
    String tenantId = context.getTenantId();
    if (tenantId == null) {
      tenantId = context.getUserInfo().getTenant();
    }
    context.setResult(resolver.resolveResourceGroup(tenantId));
  }

  @On
  public void onDeploymentId(DeploymentIdContext context) {
    String resourceGroupId = context.getResourceGroupId();
    ModelDeploymentSpec spec = context.getSpec();

    String deploymentId =
        resolver.resolveDeployment(
            resourceGroupId, spec, () -> findOrCreateDeployment(resourceGroupId, spec));
    context.setResult(deploymentId);
  }

  @On
  public void onInferenceClient(InferenceClientContext context) {
    var destination =
        clients
            .sdkService()
            .getInferenceDestination(context.getResourceGroupId())
            .usingDeploymentId(context.getDeploymentId());
    logger.debug("Inference destination URI: {}", destination.getUri());
    context.setResult(ApiClient.create(destination));
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Deployment business logic
  // ──────────────────────────────────────────────────────────────────────────

  private String findOrCreateDeployment(String resourceGroupId, ModelDeploymentSpec spec) {
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
      return existing.get();
    }
    return createDeployment(resourceGroupId, spec);
  }

  private String createDeployment(String resourceGroupId, ModelDeploymentSpec spec) {
    String configId = findOrCreateConfiguration(resourceGroupId, spec);

    // Retry only the creation call — transient 403/412 on fresh resource groups.
    // Once we have a deployment ID, polling is handled separately to avoid
    // creating orphaned deployments on poll timeout.
    String deploymentId =
        Retry.decorateSupplier(
                resolver.getRetry(),
                () -> {
                  var deployRequest =
                      AiDeploymentCreationRequest.create().configurationId(configId);
                  var response = clients.deploymentApi().create(resourceGroupId, deployRequest);
                  logger.debug(
                      "Created deployment {} ({}) in resource group {}",
                      response.getId(),
                      spec.configurationName(),
                      resourceGroupId);
                  return response.getId();
                })
            .get();

    return pollUntilRunning(resourceGroupId, deploymentId);
  }

  private String findOrCreateConfiguration(String resourceGroupId, ModelDeploymentSpec spec) {
    AiConfigurationList configList =
        clients
            .configurationApi()
            .query(resourceGroupId, spec.scenarioId(), null, null, null, null, null, null);
    return configList.getResources().stream()
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
  }

  private String createConfiguration(String resourceGroupId, ModelDeploymentSpec spec) {
    AiConfigurationBaseData configRequest =
        AiConfigurationBaseData.create()
            .name(spec.configurationName())
            .executableId(spec.executableId())
            .scenarioId(spec.scenarioId())
            .parameterBindings(spec.parameterBindings());
    String configId = clients.configurationApi().create(resourceGroupId, configRequest).getId();
    logger.debug(
        "Created configuration {} ({}) in resource group {}",
        configId,
        spec.configurationName(),
        resourceGroupId);
    return configId;
  }

  private String pollUntilRunning(String resourceGroupId, String deploymentId) {
    Retry pollRetry =
        Retry.of(
            "pollDeployment-" + deploymentId,
            RetryConfig.<AiDeploymentResponseWithDetails>custom()
                .maxAttempts(config.maxRetries())
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(config.initialDelayMs(), 2.0))
                .retryOnResult(
                    deployment -> !AiDeploymentStatus.RUNNING.equals(deployment.getStatus()))
                .retryOnException(e -> false)
                .build());

    AiDeploymentResponseWithDetails result =
        Retry.decorateSupplier(
                pollRetry,
                () -> {
                  var current = clients.deploymentApi().get(resourceGroupId, deploymentId);
                  logger.debug("Deployment {} status: {}", deploymentId, current.getStatus());
                  return current;
                })
            .get();

    if (AiDeploymentStatus.RUNNING.equals(result.getStatus())) {
      return deploymentId;
    }
    logger.error(
        "Deployment {} in resource group {} did not reach RUNNING status after {} retries",
        deploymentId,
        resourceGroupId,
        config.maxRetries());
    throw new ServiceException(
        ErrorStatuses.GATEWAY_TIMEOUT, "AI model deployment is not available");
  }

  private AiDeploymentList queryDeploymentsUntilReady(
      String resourceGroupId, ModelDeploymentSpec spec) {
    Retry retry = resolver.getRetry();
    return Retry.decorateSupplier(
            retry,
            () ->
                clients
                    .deploymentApi()
                    .query(resourceGroupId, null, null, spec.scenarioId(), null, null, null, null))
        .get();
  }
}
