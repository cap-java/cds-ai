/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.utils.services.AbstractCqnService;
import java.util.Map;

/**
 * Abstract base class for AICore service implementations, providing shared internal methods for
 * cache access, configuration, and resource group resolution. These methods are not part of the
 * public {@link AICoreService} contract but are shared between the real and mock implementations.
 */
public abstract class AbstractAICoreService extends AbstractCqnService implements AICoreService {

  protected AbstractAICoreService(String name, CdsRuntime runtime) {
    super(name, runtime);
  }

  /** Returns the configured default resource group identifier. */
  public abstract String getDefaultResourceGroup();

  /** Returns the configured resource group prefix used for tenant-specific groups. */
  public abstract String getResourceGroupPrefix();

  /** Returns the tenant-to-resource-group cache as an unmodifiable view. */
  public abstract Map<String, String> getTenantResourceGroupCache();

  /** Returns the resource-group-to-deployment cache as an unmodifiable view. */
  public abstract Map<String, String> getResourceGroupDeploymentCache();

  /** Evicts all cache entries associated with the given tenant. */
  public abstract void clearTenantCache(String tenantId);

  /**
   * Resolves the resource group ID from CQN keys, checking for explicit resource group references
   * before falling back to tenant-based resolution.
   */
  public abstract String resolveResourceGroupFromKeys(Map<String, Object> keys);
}
