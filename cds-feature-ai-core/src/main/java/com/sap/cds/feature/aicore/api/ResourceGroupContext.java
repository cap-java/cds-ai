/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

/**
 * Typed {@link EventContext} for the {@code resourceGroup} event.
 *
 * <p>Emitted on the AI Core service to resolve the AI Core resource group ID for the current
 * tenant. In multi-tenancy mode, the resource group is created on-demand if it does not exist. In
 * single-tenancy mode, the configured default resource group is returned.
 *
 * <p>If {@link #getTenantId()} is non-null, the handler uses the explicit tenant ID. Otherwise, the
 * current tenant is read from the {@code RequestContext}.
 */
@EventName(ResourceGroupContext.EVENT)
public interface ResourceGroupContext extends EventContext {

  /** Event name constant. */
  String EVENT = "resourceGroup";

  /**
   * Returns the explicit tenant ID (optional). If {@code null}, the handler reads the tenant from
   * the current {@code RequestContext}.
   */
  String getTenantId();

  /** Sets an explicit tenant ID. */
  void setTenantId(String tenantId);

  /** Returns the resolved resource group ID (set by the ON handler). */
  String getResult();

  /** Sets the resolved resource group ID. */
  void setResult(String resourceGroupId);

  /** Creates a new context instance. */
  static ResourceGroupContext create() {
    return EventContext.create(ResourceGroupContext.class, null);
  }
}
