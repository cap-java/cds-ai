/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

/**
 * Typed {@link EventContext} for the {@code deploymentId} event.
 *
 * <p>Emitted by {@link AICoreService#deploymentId(String, ModelDeploymentSpec)} to resolve (or
 * create) a deployment matching the given spec inside the given resource group. The ON handler
 * performs cache lookup, retry, configuration creation, deployment creation and polling.
 */
@EventName(DeploymentIdContext.EVENT)
public interface DeploymentIdContext extends EventContext {

  /** Event name constant. */
  String EVENT = "deploymentId";

  /** Returns the resource group ID to operate in. */
  String getResourceGroupId();

  /** Sets the resource group ID to operate in. */
  void setResourceGroupId(String resourceGroupId);

  /** Returns the deployment specification. */
  ModelDeploymentSpec getSpec();

  /** Sets the deployment specification. */
  void setSpec(ModelDeploymentSpec spec);

  /** Returns the resolved deployment ID (set by the ON handler). */
  String getResult();

  /** Sets the resolved deployment ID. */
  void setResult(String deploymentId);

  /** Creates a new context instance. */
  static DeploymentIdContext create() {
    return EventContext.create(DeploymentIdContext.class, null);
  }
}
