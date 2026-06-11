/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.services.impl.cds.AbstractCdsDefinedService;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import io.github.resilience4j.retry.Retry;
import java.util.Map;

/**
 * Abstract base class for AICore service implementations, providing shared internal methods for
 * cache access, configuration, and resource group resolution. These methods are not part of the
 * public {@link AICoreService} contract but are shared between the real and mock implementations.
 */
public abstract class AbstractAICoreService extends AbstractCdsDefinedService
    implements AICoreService {

  /** The qualified CDS service definition name. */
  private static final String CDS_DEFINITION_NAME = "AICore";

  protected AbstractAICoreService(String name, CdsRuntime runtime) {
    super(name, CDS_DEFINITION_NAME, runtime);
  }

  /** Returns the {@link CdsRuntime} that this service was created with. */
  public CdsRuntime getRuntime() {
    return runtime;
  }

  /**
   * Returns the tenant ID from the current {@link RequestContext}. May return {@code null} if no
   * tenant is set (e.g. in single-tenant mode).
   */
  public String currentTenantId() {
    return RequestContext.getCurrent(runtime).getUserInfo().getTenant();
  }

  /**
   * Returns whether the current request is running as a system/provider user. Provider users are
   * allowed to see all tenants' resources.
   */
  public boolean isProviderUser() {
    UserInfo userInfo = RequestContext.getCurrent(runtime).getUserInfo();
    return userInfo.isSystemUser() || userInfo.isInternalUser();
  }

  /**
   * Returns whether multi-tenancy is enabled. Not part of the public {@link AICoreService}
   * interface — callers should not need to be aware of multi-tenancy.
   */
  public abstract boolean isMultiTenancyEnabled();

  /**
   * Returns the shared {@link Retry} used internally for transient AI Core errors. Not part of the
   * public {@link AICoreService} interface but accessible to internal callers (e.g. the
   * recommendations module) that need consistent backoff behaviour.
   */
  public abstract Retry getRetry();

  /**
   * Returns the resource group for the given tenant ID. This is an internal method used by setup
   * handlers where the tenant ID is explicitly available from the subscribe/unsubscribe context.
   *
   * @param tenantId the CDS tenant identifier
   * @return the AI Core resource group ID
   */
  public abstract String resourceGroupForTenant(String tenantId);

  @Override
  public String resourceGroup() {
    return resourceGroupForTenant(currentTenantId());
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
