/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.services.cds.CqnService;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import io.github.resilience4j.retry.Retry;

/**
 * CAP service contract for SAP AI Core integration.
 *
 * <p>The service exposes resource-group, configuration and deployment lifecycle as CDS entities
 * (see {@link #RESOURCE_GROUPS}, {@link #DEPLOYMENTS}, {@link #CONFIGURATIONS}) and additionally
 * provides programmatic helpers to:
 *
 * <ul>
 *   <li>Resolve the resource group ID for a CDS tenant ({@link #resourceGroupForTenant(String)}),
 *       creating it on-demand when multi-tenancy is enabled.
 *   <li>Resolve (or create) a deployment matching a {@link ModelDeploymentSpec} ({@link
 *       #deploymentId(String, ModelDeploymentSpec)}).
 *   <li>Build an {@link ApiClient} preconfigured for inference against a specific deployment
 *       ({@link #inferenceClient(String, String)}).
 *   <li>Expose a shared retry/backoff policy ({@link #getRetry()}) for downstream callers that want
 *       consistent transient-error handling.
 * </ul>
 *
 * <p>Two implementations are provided: {@link AICoreServiceImpl} (when an SAP AI Core service
 * binding is detected) and {@link MockAICoreServiceImpl} (in-memory fallback for local
 * development).
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
   * Returns the AI Core resource group ID associated with the given CDS tenant.
   *
   * <p>When multi-tenancy is disabled the configured {@code cds.requires.AICore.resourceGroup} is
   * returned for every tenant. When enabled, the resource group is looked up by the {@code
   * ext.ai.sap.com/CDS_TENANT_ID} label and created on first call if it does not exist.
   *
   * @param tenantId the CDS tenant identifier; may be {@code null} when multi-tenancy is disabled
   * @return the AI Core resource group ID
   */
  String resourceGroupForTenant(String tenantId);

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

  /** Returns whether multi-tenancy is enabled for this service. */
  boolean isMultiTenancyEnabled();

  /**
   * Returns the shared {@link Retry} used internally for transient AI Core errors. Exposed so
   * downstream inference clients can reuse the same backoff policy.
   */
  Retry getRetry();
}
