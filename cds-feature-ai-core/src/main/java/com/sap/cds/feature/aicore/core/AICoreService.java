/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.services.cds.CqnService;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;
import io.github.resilience4j.retry.Retry;

public interface AICoreService extends CqnService {

  String DEFAULT_NAME = "AICore";
  String RESOURCE_GROUPS = "AICore.resourceGroups";
  String DEPLOYMENTS = "AICore.deployments";
  String CONFIGURATIONS = "AICore.configurations";

  String resourceGroupForTenant(String tenantId);

  String deploymentId(String resourceGroupId, ModelDeploymentSpec spec);

  ApiClient inferenceClient(String resourceGroupId, String deploymentId);

  boolean isMultiTenancyEnabled();

  Retry getRetry();
}
