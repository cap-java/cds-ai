/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;

/**
 * Typed {@link EventContext} for the {@code inferenceClient} event.
 *
 * <p>Emitted on the AI Core service to build an {@link ApiClient} preconfigured with the inference
 * destination for the given deployment.
 */
@EventName(InferenceClientContext.EVENT)
public interface InferenceClientContext extends EventContext {

  /** Event name constant. */
  String EVENT = "inferenceClient";

  /** Returns the resource group ID containing the deployment. */
  String getResourceGroupId();

  /** Sets the resource group ID containing the deployment. */
  void setResourceGroupId(String resourceGroupId);

  /** Returns the deployment ID. */
  String getDeploymentId();

  /** Sets the deployment ID. */
  void setDeploymentId(String deploymentId);

  /** Returns the configured {@link ApiClient} (set by the ON handler). */
  ApiClient getResult();

  /** Sets the configured {@link ApiClient}. */
  void setResult(ApiClient client);

  /** Creates a new context instance. */
  static InferenceClientContext create() {
    return EventContext.create(InferenceClientContext.class, null);
  }
}
