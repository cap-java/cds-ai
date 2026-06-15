/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.services.environment.CdsEnvironment;

/**
 * Immutable configuration for the AI Core plugin, read once from {@link CdsEnvironment} at startup.
 *
 * @param defaultResourceGroup the resource group to use when multi-tenancy is disabled
 * @param resourceGroupPrefix prefix for tenant-specific resource groups (e.g. "cds-")
 * @param maxRetries max retry attempts for transient AI Core errors
 * @param initialDelayMs initial backoff delay in milliseconds
 * @param multiTenancyEnabled whether multi-tenancy is active
 */
public record AICoreConfig(
    String defaultResourceGroup,
    String resourceGroupPrefix,
    int maxRetries,
    long initialDelayMs,
    boolean multiTenancyEnabled) {

  /** The AI Core resource-group label key used to associate groups with CDS tenants. */
  public static final String TENANT_LABEL_KEY = "ext.ai.sap.com/CDS_TENANT_ID";

  private static final String DEFAULT_RESOURCE_GROUP = "default";
  private static final String DEFAULT_RESOURCE_GROUP_PREFIX = "cds-";
  private static final int DEFAULT_MAX_RETRIES = 10;
  private static final long DEFAULT_INITIAL_DELAY_MS = 300;

  /** Creates an {@code AICoreConfig} from the runtime environment properties. */
  public static AICoreConfig from(CdsEnvironment env, boolean multiTenancyEnabled) {
    return new AICoreConfig(
        env.getProperty("cds.ai.core.resourceGroup", String.class, DEFAULT_RESOURCE_GROUP),
        env.getProperty(
            "cds.ai.core.resourceGroupPrefix", String.class, DEFAULT_RESOURCE_GROUP_PREFIX),
        env.getProperty("cds.ai.core.maxRetries", Integer.class, DEFAULT_MAX_RETRIES),
        env.getProperty("cds.ai.core.initialDelayMs", Long.class, DEFAULT_INITIAL_DELAY_MS),
        multiTenancyEnabled);
  }
}
