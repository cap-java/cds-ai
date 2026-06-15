/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.core.handler.MockAICoreApiHandler;
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

  private AICoreService createMockService(boolean multiTenancy) {
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
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  @Test
  void noMultiTenancy_resourceGroupReturnsDefault() {
    AICoreService service = createMockService(false);
    assertThat(service.resourceGroup()).isEqualTo("default");
  }

  @Test
  void noMultiTenancy_resourceGroupForTenant_returnsDefault() {
    AICoreService service = createMockService(false);
    assertThat(service.resourceGroupForTenant("any-tenant")).isEqualTo("default");
  }

  @Test
  void multiTenancy_resourceGroupForTenant_returnsPrefixed() {
    AICoreService service = createMockService(true);
    String rg = service.resourceGroupForTenant("my-tenant");
    assertThat(rg).isEqualTo("cds-my-tenant");
  }

  @Test
  void multiTenancy_resourceGroupForTenant_cachesResult() {
    AICoreService service = createMockService(true);
    String first = service.resourceGroupForTenant("t1");
    String second = service.resourceGroupForTenant("t1");
    assertThat(first).isEqualTo(second);
  }

  @Test
  void deploymentId_returnsMockId() {
    AICoreService service = createMockService(false);
    var spec = new ModelDeploymentSpec("scenario", "exec", "cfg1", List.of(), d -> true);
    String id = service.deploymentId("default", spec);
    assertThat(id).startsWith("mock-deployment-");
  }

  @Test
  void deploymentId_cachesSameResult() {
    AICoreService service = createMockService(false);
    var spec = new ModelDeploymentSpec("scenario", "exec", "cfg1", List.of(), d -> true);
    String first = service.deploymentId("default", spec);
    String second = service.deploymentId("default", spec);
    assertThat(first).isEqualTo(second);
  }
}
