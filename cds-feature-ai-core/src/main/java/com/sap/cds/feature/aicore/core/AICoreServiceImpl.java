/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.services.impl.cds.AbstractCdsDefinedService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.sdk.services.openapi.apache.apiclient.ApiClient;

/**
 * Production implementation of {@link AICoreService}.
 *
 * <p>This class is a pure delegation layer: each API method creates a typed {@link
 * com.sap.cds.services.EventContext EventContext}, emits it via the CAP event mechanism, and
 * returns the handler's result. All business logic (caching, locking, API calls) lives in the
 * registered ON handlers which receive their dependencies via constructor injection.
 *
 * <p><strong>Implementation note:</strong> This class extends {@code AbstractCdsDefinedService}
 * from the CAP Java runtime's internal {@code impl} package. This is necessary because the public
 * API ({@code ServiceDelegator}) does not provide CQN execution capabilities or CDS model binding.
 * The semi-public {@code AbstractCqnService} (from {@code cds-services-utils}) provides CQN but not
 * {@code getDefinition()}. Until a public API alternative exists, this coupling is accepted and
 * version-compatibility is verified through integration tests against the CAP Java runtime.
 */
public class AICoreServiceImpl extends AbstractCdsDefinedService implements AICoreService {

  private static final String CDS_DEFINITION_NAME = "AICore";

  public AICoreServiceImpl(String name, CdsRuntime runtime) {
    super(name, CDS_DEFINITION_NAME, runtime);
  }

  @Override
  public String resourceGroup() {
    ResourceGroupContext ctx = ResourceGroupContext.create();
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public String resourceGroupForTenant(String tenantId) {
    ResourceGroupContext ctx = ResourceGroupContext.create();
    ctx.setTenantId(tenantId);
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public String deploymentId(String resourceGroupId, ModelDeploymentSpec spec) {
    DeploymentIdContext ctx = DeploymentIdContext.create();
    ctx.setResourceGroupId(resourceGroupId);
    ctx.setSpec(spec);
    emit(ctx);
    return ctx.getResult();
  }

  @Override
  public ApiClient inferenceClient(String resourceGroupId, String deploymentId) {
    InferenceClientContext ctx = InferenceClientContext.create();
    ctx.setResourceGroupId(resourceGroupId);
    ctx.setDeploymentId(deploymentId);
    emit(ctx);
    return ctx.getResult();
  }
}
