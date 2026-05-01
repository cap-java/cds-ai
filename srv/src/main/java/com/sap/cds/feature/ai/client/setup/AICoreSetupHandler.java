/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client.setup;

import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.AiConfiguration;
import com.sap.ai.sdk.core.model.AiConfigurationBaseData;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.ai.sdk.core.model.AiParameterArgumentBinding;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(DeploymentService.DEFAULT_NAME)
public class AICoreSetupHandler implements EventHandler {

  private static final String DEFAULT_RESOURCE_GROUP = "default";
  private static final String TENANT_LABEL_KEY = "ext.ai.sap.com/CDS_TENANT_ID";
  private static final String RPT_SCENARIO_ID = "foundation-models";
  private static final String RPT_EXECUTABLE_ID = "aicore-sap";
  private static final String RPT_CONFIG_NAME = "sap-rpt-1-small";
  private static final String RPT_MODEL_NAME = "sap-rpt-1-small";
  private static final String RPT_MODEL_VERSION = "latest";
  // Max retries and initial delay (ms) when polling for RUNNING of deployment or readiness of
  // resource group
  private static final int AICORE_OPS_MAX_RETRIES = 10;
  private static final long AICORE_OPS_INITIAL_DELAY_MS = 300;
  private static final Logger logger = LoggerFactory.getLogger(AICoreSetupHandler.class);

  // In-memory cache: tenantId -> resourceGroupId
  private final Map<String, String> tenantResourceGroupCache = new ConcurrentHashMap<>();
  // In-memory cache: resourceGroupId -> RPT-1 deploymentId
  private final Map<String, String> resourceGroupDeploymentCache = new ConcurrentHashMap<>();

  private final CdsEnvironment environment;

  // For testing
  Map<String, String> getTenantResourceGroupCache() {
    return tenantResourceGroupCache;
  }

  Map<String, String> getResourceGroupDeploymentCache() {
    return resourceGroupDeploymentCache;
  }

  public AICoreSetupHandler(CdsEnvironment environment) {
    this.environment = environment;
  }

  /**
   * Called automatically after a tenant subscribes: Creates an AI Core resource group for the
   * tenant.
   */
  @After(event = DeploymentService.EVENT_SUBSCRIBE)
  public void afterSubscribe(SubscribeEventContext context) {
    String tenantId = context.getTenant();
    logger.debug("Creating AI Core resources for tenant {}", tenantId);
    try {
      String resourceGroupId = getResourceGroupForTenant(tenantId);
      logger.info("Created AI Core resource group {} for tenant {}", resourceGroupId, tenantId);
    } catch (Exception e) {
      // Don't throw - let subscription succeed
      logger.error(
          "Failed to create AI Core resources for tenant {} (retrying on demand)", tenantId, e);
    }
  }

  /**
   * Called automatically before a tenant unsubscribes: Deletes the AI Core resource group for the
   * tenant.
   */
  @Before(event = DeploymentService.EVENT_UNSUBSCRIBE)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    logger.debug("Deleting AI Core resources for tenant {}", tenantId);
    try {
      deleteResourceGroupForTenant(tenantId);
      logger.info("Deleted AI Core resources for tenant {}", tenantId);
    } catch (Exception e) {
      // Don't throw - let unsubscription succeed
      logger.warn("Failed to delete AI Core resources for tenant {}: {}", tenantId, e.getMessage());
    }
  }

  public static boolean isMultitenancyEnabled() {
    return Boolean.parseBoolean(
        System.getProperty(
            "cds.multitenancy.enabled",
            System.getenv().getOrDefault("CDS_MULTITENANCY_ENABLED", "false")));
    // this.environment.getProperty("cds.requires.multitenancy", Boolean.class, false));
  }

  /**
   * Resolves the resource group for the given tenant. In multi-tenant mode, checks for an existing
   * resource group or creates one. In single-tenant mode, returns the default resource group.
   */
  public String resolveResourceGroup(String tenantId) {
    if (isMultitenancyEnabled()) {
      return getResourceGroupForTenant(tenantId);
    }
    String group =
        this.environment.getProperty(
            "cds.requires.AICore.resourceGroup", String.class, DEFAULT_RESOURCE_GROUP);
    group = group != null ? group : DEFAULT_RESOURCE_GROUP;
    logger.info("Multitenancy disabled, using resource group {}", group);
    return group;
  }

  /**
   * Returns the resource group for a tenant, creating one in AI Core if it doesn't exist yet.
   * Caches the result in memory.
   */
  private String getResourceGroupForTenant(String tenantId) {
    String cached = tenantResourceGroupCache.get(tenantId);
    if (cached != null) {
      return cached;
    }
    ResourceGroupApi api = new ResourceGroupApi();
    List<String> labelSelector = List.of(TENANT_LABEL_KEY + "=" + tenantId);
    BckndResourceGroupList result = api.getAll(null, null, null, null, null, null, labelSelector);
    List<BckndResourceGroup> resources = result.getResources();
    if (resources != null && !resources.isEmpty()) {
      String resourceGroupId = resources.get(0).getResourceGroupId();
      tenantResourceGroupCache.put(tenantId, resourceGroupId);
      return resourceGroupId;
    }
    String createdId = createResourceGroupForTenant(tenantId, api);
    tenantResourceGroupCache.put(tenantId, createdId);
    return createdId;
  }

  // See
  // https://javadoc.io/doc/com.sap.ai.sdk/core/latest/com/sap/ai/sdk/core/model/BckndResourceGroupsPostRequest.html
  private String createResourceGroupForTenant(String tenantId, ResourceGroupApi api) {
    // This resourceGroupId is needed for the request, will fail otherwise
    String resourceGroupId = UUID.randomUUID().toString();
    BckndResourceGroupLabel label =
        BckndResourceGroupLabel.create().key(TENANT_LABEL_KEY).value(tenantId);
    BckndResourceGroupsPostRequest request =
        BckndResourceGroupsPostRequest.create()
            .resourceGroupId(resourceGroupId)
            .labels(List.of(label));
    api.create(request);
    logger.debug("Created resource group {} for tenant {}", resourceGroupId, tenantId);
    return resourceGroupId;
  }

  /**
   * Returns the RPT-1 deployment ID for the given resource group, creating configuration and
   * deployment if none exists. Polls until the deployment reaches RUNNING status, which might take
   * a while. Caches the result in memory.
   */
  public String getDeploymentForResourceGroup(String resourceGroup) {
    String cached = resourceGroupDeploymentCache.get(resourceGroup);
    if (cached != null) {
      return cached;
    }
    DeploymentApi deploymentApi = new DeploymentApi();
    // Look for an existing running or pending RPT-1 deployment in this resource group.
    AiDeploymentList deploymentList =
        queryDeploymentsFromResourceGroupUntilReady(deploymentApi, resourceGroup);
    Optional<AiDeployment> aiDeployment =
        deploymentList.getResources().stream()
            .filter(
                d ->
                    RPT_CONFIG_NAME.equals(d.getConfigurationName())
                        && (AiDeploymentStatus.RUNNING.equals(d.getStatus())
                            || AiDeploymentStatus.PENDING.equals(d.getStatus())))
            .findFirst();
    if (aiDeployment.isPresent()) {
      String deploymentId = aiDeployment.get().getId();
      resourceGroupDeploymentCache.put(resourceGroup, deploymentId);
      return deploymentId;
    }

    // No deployment found: we check if there is a configuration, if not create one and then create
    // a deployment.
    // The resource group should be ready for this call, since
    // queryDeploymentsFromResourceGroupUntilReady made sure it is.
    ConfigurationApi configApi = new ConfigurationApi();
    AiConfigurationList configList =
        configApi.query(resourceGroup, RPT_SCENARIO_ID, null, null, null, null, null, null);
    Optional<AiConfiguration> existingConfig =
        configList.getResources().stream()
            .filter(c -> RPT_CONFIG_NAME.equals(c.getName()))
            .findFirst();

    String configId;
    if (existingConfig.isPresent()) {
      configId = existingConfig.get().getId();
      logger.debug(
          "Reusing existing RPT-1 configuration {} in resource group {}", configId, resourceGroup);
    } else {
      // Configuration creation is synchronous and should be fast, so we don't implement a retry
      // loop here.
      AiConfigurationBaseData configRequest =
          AiConfigurationBaseData.create()
              .name(RPT_CONFIG_NAME)
              .executableId(RPT_EXECUTABLE_ID)
              .scenarioId(RPT_SCENARIO_ID);
      configRequest.parameterBindings(
          List.of(
              AiParameterArgumentBinding.create().key("modelName").value(RPT_MODEL_NAME),
              AiParameterArgumentBinding.create().key("modelVersion").value(RPT_MODEL_VERSION)));
      configId = configApi.create(resourceGroup, configRequest).getId();
      logger.debug("Created RPT-1 configuration {} in resource group {}", configId, resourceGroup);
    }

    // Now create a deployment for the configuration and poll until it's running and usable.
    long delay = AICORE_OPS_INITIAL_DELAY_MS;
    for (int i = 0; i < AICORE_OPS_MAX_RETRIES; i++) {
      try {
        var deployRequest = AiDeploymentCreationRequest.create().configurationId(configId);
        var deployResponse = deploymentApi.create(resourceGroup, deployRequest);
        String deploymentId = deployResponse.getId();
        logger.debug(
            "Created RPT-1 deployment {} in resource group {}, polling for RUNNING",
            deploymentId,
            resourceGroup);

        return pollUntilRunning(deploymentApi, resourceGroup, deploymentId);
      } catch (OpenApiRequestException e) {
        if (notReadyYet(e) && i < AICORE_OPS_MAX_RETRIES - 1) {
          logger.debug(
              "Deployment of resource group {} not ready yet ({}), retrying in {} ms ({}/{})",
              resourceGroup,
              e.getMessage(),
              delay,
              i + 1,
              AICORE_OPS_MAX_RETRIES);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Interrupted while waiting for resource group " + resourceGroup, ie);
          }
          delay *= 2;
        } else {
          throw e;
        }
      }
    }
    throw new IllegalStateException(
        "Resource group " + resourceGroup + " never became ready for deployment");
  }

  private String pollUntilRunning(
      DeploymentApi deploymentApi, String resourceGroup, String deploymentId) {
    long delay = AICORE_OPS_INITIAL_DELAY_MS;
    for (int i = 0; i < AICORE_OPS_MAX_RETRIES; i++) {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for deployment " + deploymentId, e);
      }
      var current = deploymentApi.get(resourceGroup, deploymentId);
      logger.debug(
          "Deployment {} status: {} (retry {}/{})",
          deploymentId,
          current.getStatus(),
          i + 1,
          AICORE_OPS_MAX_RETRIES);
      if (AiDeploymentStatus.RUNNING.equals(current.getStatus())) {
        resourceGroupDeploymentCache.put(resourceGroup, deploymentId);
        return deploymentId;
      }
      delay *= 2;
    }
    throw new RuntimeException(
        "RPT-1 deployment "
            + deploymentId
            + " did not reach RUNNING status after "
            + AICORE_OPS_MAX_RETRIES
            + " retries");
  }

  /*
   * Queries deployments in the given resource group.
   * In case the resource group isn't ready yet, i.e., the request returns a 403 or 412 status code,
   * we query until the resource group becomes ready; this is neccessary for further interaction with
   * the resource group.
   */
  private AiDeploymentList queryDeploymentsFromResourceGroupUntilReady(
      DeploymentApi deploymentApi, String resourceGroup) {
    long delay = AICORE_OPS_INITIAL_DELAY_MS;
    for (int i = 0; i < AICORE_OPS_MAX_RETRIES; i++) {
      try {
        return deploymentApi.query(
            resourceGroup, null, null, RPT_SCENARIO_ID, null, null, null, null);
      } catch (OpenApiRequestException e) {
        if (notReadyYet(e) && i < AICORE_OPS_MAX_RETRIES - 1) {
          logger.debug(
              "Resource group {} not ready yet ({}), retrying in {} ms ({}/{})",
              resourceGroup,
              e.getMessage(),
              delay,
              i + 1,
              AICORE_OPS_MAX_RETRIES);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Interrupted while waiting for resource group " + resourceGroup, ie);
          }
          delay *= 2;
        } else {
          throw e;
        }
      }
    }
    // unreachable — loop always returns or throws
    throw new IllegalStateException("queryDeploymentsWithRetry exited unexpectedly");
  }

  /**
   * Returns true if the exception (or its cause chain) represents an HTTP 403 or 412. AI Core
   * returns 403 or 412 when a deployment is not yet provisioned or ready. In this case, we retry
   * the operation that caused the exception after a delay. The SDK sometimes wraps the actual
   * OpenApiRequestException in an IOException which is why we check the nested causes.
   */
  public static boolean notReadyYet(OpenApiRequestException e) {
    Throwable t = e;
    while (t != null) {
      if (t instanceof OpenApiRequestException oae) {
        Integer code = oae.statusCode();
        if (Integer.valueOf(403).equals(code) || Integer.valueOf(412).equals(code)) {
          return true;
        }
      }
      t = t.getCause();
    }
    return false;
  }

  private void deleteResourceGroupForTenant(String tenantId) {
    String resourceGroupId = tenantResourceGroupCache.remove(tenantId);
    if (resourceGroupId == null) {
      logger.debug("No cached resource group for tenant {}, nothing to delete", tenantId);
      return;
    }
    resourceGroupDeploymentCache.remove(resourceGroupId);
    new ResourceGroupApi().delete(resourceGroupId);
    logger.info("Deleted resource group {} for tenant {}", resourceGroupId, tenantId);
  }
}
