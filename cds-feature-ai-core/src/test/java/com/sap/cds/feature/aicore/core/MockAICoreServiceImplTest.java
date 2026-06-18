/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.handler.MockAICoreApiHandler;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.AICore_;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MockAICoreApiHandler} verifying the mock behavior when no AI Core binding is
 * present. These tests boot a real CDS runtime with mock handlers to validate end-to-end flow.
 */
class MockAICoreServiceImplTest {

  private RemoteService createMockService(boolean multiTenancy) {
    CdsProperties props = new CdsProperties();
    if (multiTenancy) {
      CdsProperties.MultiTenancy mt = new CdsProperties.MultiTenancy();
      CdsProperties.MultiTenancy.Sidecar sidecar = new CdsProperties.MultiTenancy.Sidecar();
      sidecar.setUrl("http://localhost:4004");
      mt.setSidecar(sidecar);
      props.setMultiTenancy(mt);
    }

    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(props))
            .environmentConfigurations()
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    return runtime.getServiceCatalog().getService(RemoteService.class, AICore_.CDS_NAME);
  }

  @Test
  void noMultiTenancy_resourceGroupReturnsDefault() {
    RemoteService service = createMockService(false);
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    assertThat(rgCtx.getResult()).isEqualTo("default");
  }

  @Test
  void noMultiTenancy_resourceGroupForTenant_returnsDefault() {
    RemoteService service = createMockService(false);
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    rgCtx.setTenantId("any-tenant");
    service.emit(rgCtx);
    assertThat(rgCtx.getResult()).isEqualTo("default");
  }

  @Test
  void multiTenancy_resourceGroupForTenant_returnsPrefixed() {
    RemoteService service = createMockService(true);
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    rgCtx.setTenantId("my-tenant");
    service.emit(rgCtx);
    assertThat(rgCtx.getResult()).isEqualTo("cds-my-tenant");
  }

  @Test
  void multiTenancy_resourceGroupForTenant_cachesResult() {
    RemoteService service = createMockService(true);
    ResourceGroupContext rgCtx1 = ResourceGroupContext.create();
    rgCtx1.setTenantId("t1");
    service.emit(rgCtx1);
    String first = rgCtx1.getResult();

    ResourceGroupContext rgCtx2 = ResourceGroupContext.create();
    rgCtx2.setTenantId("t1");
    service.emit(rgCtx2);
    String second = rgCtx2.getResult();
    assertThat(first).isEqualTo(second);
  }

  @Test
  void deploymentId_returnsMockId() {
    RemoteService service = createMockService(false);
    var spec = new ModelDeploymentSpec("scenario", "exec", "cfg1", List.of(), d -> true);
    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId("default");
    depCtx.setSpec(spec);
    service.emit(depCtx);
    String id = depCtx.getResult();
    assertThat(id).startsWith("mock-deployment-");
  }

  @Test
  void deploymentId_cachesSameResult() {
    RemoteService service = createMockService(false);
    var spec = new ModelDeploymentSpec("scenario", "exec", "cfg1", List.of(), d -> true);

    DeploymentIdContext depCtx1 = DeploymentIdContext.create();
    depCtx1.setResourceGroupId("default");
    depCtx1.setSpec(spec);
    service.emit(depCtx1);
    String first = depCtx1.getResult();

    DeploymentIdContext depCtx2 = DeploymentIdContext.create();
    depCtx2.setResourceGroupId("default");
    depCtx2.setSpec(spec);
    service.emit(depCtx2);
    String second = depCtx2.getResult();
    assertThat(first).isEqualTo(second);
  }
}
