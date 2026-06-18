/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.generated.cds4j.aicore.AICore_;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AICoreServiceConfiguration} using a real {@link CdsRuntime} booted with the AICore
 * CDS model. This verifies the full service registration and handler wiring lifecycle without heavy
 * Mockito mocks.
 *
 * <p>Since the test runtime has no service bindings, the configuration always registers mock
 * handlers regardless of environment variables.
 */
class AICoreServiceConfigurationTest {

  @Test
  void noBinding_noMultiTenancy_registersService() {
    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()))
            .environmentConfigurations()
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    RemoteService service =
        runtime.getServiceCatalog().getService(RemoteService.class, AICore_.CDS_NAME);

    assertThat(service).isNotNull();
  }

  @Test
  void noBinding_withSidecarUrl_registersService() {
    CdsProperties props = new CdsProperties();
    CdsProperties.MultiTenancy mt = new CdsProperties.MultiTenancy();
    CdsProperties.MultiTenancy.Sidecar sidecar = new CdsProperties.MultiTenancy.Sidecar();
    sidecar.setUrl("http://localhost:4004");
    mt.setSidecar(sidecar);
    props.setMultiTenancy(mt);

    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(props))
            .environmentConfigurations()
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    RemoteService service =
        runtime.getServiceCatalog().getService(RemoteService.class, AICore_.CDS_NAME);

    assertThat(service).isNotNull();
  }

  @Test
  void noModel_skipsServiceRegistration() {
    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()))
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    RemoteService service =
        runtime.getServiceCatalog().getService(RemoteService.class, AICore_.CDS_NAME);

    assertThat(service).isNull();
  }
}
