/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

/**
 * Typed {@link EventContext} for the {@code resourceGroup} event.
 *
 * <p>Emitted by {@link AICoreService#resourceGroup()} to resolve the AI Core resource group ID for
 * the current tenant. In multi-tenancy mode, the resource group is created on-demand if it does not
 * exist. In single-tenancy mode, the configured default resource group is returned.
 *
 * <p>The current tenant is read from the {@code RequestContext} — no explicit input is required.
 */
@EventName(ResourceGroupContext.EVENT)
public interface ResourceGroupContext extends EventContext {

  /** Event name constant. */
  String EVENT = "resourceGroup";

  /** Returns the resolved resource group ID (set by the ON handler). */
  String getResult();

  /** Sets the resolved resource group ID. */
  void setResult(String resourceGroupId);

  /** Creates a new context instance. */
  static ResourceGroupContext create() {
    return EventContext.create(ResourceGroupContext.class, null);
  }
}
