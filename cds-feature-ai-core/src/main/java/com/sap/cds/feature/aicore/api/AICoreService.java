/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.cds.services.cds.CqnService;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;

/**
 * CAP service contract for SAP AI Core integration.
 *
 * <p>The service exposes resource-group, configuration and deployment lifecycle as CDS entities
 * (see {@link #RESOURCE_GROUPS}, {@link #DEPLOYMENTS}, {@link #CONFIGURATIONS}) and additionally
 * provides programmatic helpers to:
 *
 * <ul>
 *   <li>Resolve the resource group ID for the current tenant ({@link #resourceGroup()}), creating
 *       it on-demand when multi-tenancy is enabled.
 *   <li>Resolve (or create) a deployment matching a {@link ModelDeploymentSpec} ({@link
 *       #deploymentId(String, ModelDeploymentSpec)}).
 *   <li>Build an {@link ApiClient} preconfigured for inference against a specific deployment
 *       ({@link #inferenceClient(String, String)}).
 * </ul>
 *
 * <p>The implementation is tenant-aware: it reads the current tenant from the {@code
 * RequestContext}. Callers do not need to pass tenant identifiers explicitly.
 */
public interface AICoreService extends CqnService {

  /** Default service name under which an instance is registered in the service catalog. */
  String DEFAULT_NAME = "AICore";

  /** Qualified name of the {@code resourceGroups} entity exposed by this service. */
  String RESOURCE_GROUPS = "AICore.resourceGroups";

  /** Qualified name of the {@code deployments} entity exposed by this service. */
  String DEPLOYMENTS = "AICore.deployments";

  /** Qualified name of the {@code configurations} entity exposed by this service. */
  String CONFIGURATIONS = "AICore.configurations";

  /**
   * Returns the AI Core resource group ID associated with the current tenant.
   *
   * <p>When multi-tenancy is disabled the configured default resource group is returned. When
   * enabled, the resource group is looked up by the {@code ext.ai.sap.com/CDS_TENANT_ID} label and
   * created on first call if it does not exist.
   *
   * @return the AI Core resource group ID for the current tenant
   */
  String resourceGroup();

  /**
   * Returns the deployment ID for the given model spec inside the given resource group.
   *
   * <p>Looks up an existing RUNNING/PENDING deployment that matches the spec, otherwise creates a
   * configuration (if missing) and a new deployment, then polls until the deployment reaches
   * RUNNING. Results are cached per {@code (resourceGroupId, configurationName)} pair.
   *
   * @param resourceGroupId the AI Core resource group to operate in
   * @param spec the deployment specification (scenario, executable, configuration name and
   *     existing-match predicate)
   * @return the deployment ID
   */
  String deploymentId(String resourceGroupId, ModelDeploymentSpec spec);

  /**
   * Returns an {@link ApiClient} preconfigured with the inference destination for the given
   * deployment, suitable for constructing foundation-model SDK clients.
   *
   * @param resourceGroupId the AI Core resource group containing the deployment
   * @param deploymentId the deployment ID returned by {@link #deploymentId(String,
   *     ModelDeploymentSpec)}
   * @return a configured {@link ApiClient} pointing at the deployment's inference endpoint
   */
  ApiClient inferenceClient(String resourceGroupId, String deploymentId);
}
