/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AICoreServiceConfiguration} using a real {@link CdsRuntime} booted with the AICore
 * CDS model. This verifies the full service registration and handler wiring lifecycle without heavy
 * Mockito mocks.
 *
 * <p>Tests that exercise service registration are skipped when {@code AICORE_SERVICE_KEY} is set,
 * because the configuration would then register the real {@link AICoreServiceImpl} (which requires
 * actual AI Core credentials).
 */
class AICoreServiceConfigurationTest {

  private static void assumeNoAICoreBinding() {
    String envKey = System.getenv("AICORE_SERVICE_KEY");
    Assumptions.assumeTrue(envKey == null || envKey.isBlank(), "Skipped: AICORE_SERVICE_KEY is set");
  }

  @Test
  void noBinding_noMultiTenancy_registersMockService() {
    assumeNoAICoreBinding();

    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()))
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    AICoreService service =
        runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);

    assertThat(service).isNotNull().isInstanceOf(MockAICoreServiceImpl.class);
    assertThat(((MockAICoreServiceImpl) service).isMultiTenancyEnabled()).isFalse();
  }

  @Test
  void noBinding_withSidecarUrl_registersMultiTenantMockService() {
    assumeNoAICoreBinding();

    CdsProperties props = new CdsProperties();
    CdsProperties.MultiTenancy mt = new CdsProperties.MultiTenancy();
    CdsProperties.MultiTenancy.Sidecar sidecar = new CdsProperties.MultiTenancy.Sidecar();
    sidecar.setUrl("http://localhost:4004");
    mt.setSidecar(sidecar);
    props.setMultiTenancy(mt);

    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(props))
            .cdsModel("edmx/csn.json")
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    AICoreService service =
        runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);

    assertThat(service).isNotNull().isInstanceOf(MockAICoreServiceImpl.class);
    assertThat(((MockAICoreServiceImpl) service).isMultiTenancyEnabled()).isTrue();
  }

  @Test
  void noModel_skipsServiceRegistration() {
    CdsRuntime runtime =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()))
            .serviceConfigurations()
            .eventHandlerConfigurations()
            .complete();

    AICoreService service =
        runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);

    assertThat(service).isNull();
  }
}
