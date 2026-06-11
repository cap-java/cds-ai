/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.handler.MockEntityHandler;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsService;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AICoreServiceConfigurationTest {

  @Mock private CdsRuntimeConfigurer configurer;
  @Mock private CdsRuntime runtime;
  @Mock private CdsEnvironment environment;
  @Mock private ServiceCatalog serviceCatalog;

  /**
   * Tests the eventHandlers() branch where the registered service is a MockAICoreServiceImpl
   * (lines 116-123) with multi-tenancy disabled.
   */
  @Test
  void eventHandlers_mockService_noMultiTenancy_registersBasicHandlers() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(runtime.getEnvironment()).thenReturn(environment);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    lenient().when(environment.getProperty(any(String.class), any(Class.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    MockAICoreServiceImpl mockService =
        new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime, false);
    when(serviceCatalog.getService(AICoreService.class, AICoreService.DEFAULT_NAME))
        .thenReturn(mockService);

    AICoreServiceConfiguration config = new AICoreServiceConfiguration();
    config.eventHandlers(configurer);

    ArgumentCaptor<EventHandler> captor = ArgumentCaptor.forClass(EventHandler.class);
    verify(configurer, atLeastOnce()).eventHandler(captor.capture());

    var handlers = captor.getAllValues();
    assert handlers.stream().anyMatch(h -> h instanceof MockEntityHandler);
    // No setup handler registered when MT is disabled
    assert handlers.stream().noneMatch(h -> h instanceof MockAICoreSetupHandler);
  }

  /**
   * Tests the eventHandlers() branch where the registered service is a MockAICoreServiceImpl with
   * multi-tenancy enabled — verifies that MockAICoreSetupHandler is registered (lines 119-121).
   */
  @Test
  void eventHandlers_mockService_withMultiTenancy_registersSetupHandler() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(runtime.getEnvironment()).thenReturn(environment);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    lenient().when(environment.getProperty(any(String.class), any(Class.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    MockAICoreServiceImpl mockService =
        new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime, true);
    when(serviceCatalog.getService(AICoreService.class, AICoreService.DEFAULT_NAME))
        .thenReturn(mockService);

    AICoreServiceConfiguration config = new AICoreServiceConfiguration();
    config.eventHandlers(configurer);

    ArgumentCaptor<EventHandler> captor = ArgumentCaptor.forClass(EventHandler.class);
    verify(configurer, atLeastOnce()).eventHandler(captor.capture());

    var handlers = captor.getAllValues();
    assert handlers.stream().anyMatch(h -> h instanceof MockEntityHandler);
    assert handlers.stream().anyMatch(h -> h instanceof MockAICoreSetupHandler);
  }

  /**
   * Tests detectMultiTenancy returning true when sidecar URL is set (the
   * `if (sidecarUrl != null && !sidecarUrl.isBlank()) { return true; }` branch).
   * This is exercised through services() with no AI Core binding present.
   */
  @Test
  void services_noBinding_sidecarUrlSet_createsMultiTenantMockService() {
    String envKey = System.getenv("AICORE_SERVICE_KEY");
    org.junit.jupiter.api.Assumptions.assumeTrue(
        envKey == null || envKey.isBlank(), "Skipped: AICORE_SERVICE_KEY is set");
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getEnvironment()).thenReturn(environment);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.findService("AICore")).thenReturn(Optional.of(mock(CdsService.class)));
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    lenient().when(environment.getProperty(any(String.class), any(Class.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    CdsProperties cdsProperties = new CdsProperties();
    CdsProperties.MultiTenancy mt = new CdsProperties.MultiTenancy();
    CdsProperties.MultiTenancy.Sidecar sidecar = new CdsProperties.MultiTenancy.Sidecar();
    sidecar.setUrl("http://localhost:4004");
    mt.setSidecar(sidecar);
    cdsProperties.setMultiTenancy(mt);
    when(environment.getCdsProperties()).thenReturn(cdsProperties);

    AICoreServiceConfiguration config = new AICoreServiceConfiguration();
    config.services(configurer);

    ArgumentCaptor<MockAICoreServiceImpl> captor =
        ArgumentCaptor.forClass(MockAICoreServiceImpl.class);
    verify(configurer).service(captor.capture());
    assert captor.getValue().isMultiTenancyEnabled();
  }

  /**
   * Tests detectMultiTenancy returning false when no sidecar URL and no DeploymentService.
   */
  @Test
  void services_noBinding_noSidecarUrl_noDeploymentService_singleTenant() {
    String envKey = System.getenv("AICORE_SERVICE_KEY");
    org.junit.jupiter.api.Assumptions.assumeTrue(
        envKey == null || envKey.isBlank(), "Skipped: AICORE_SERVICE_KEY is set");
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getEnvironment()).thenReturn(environment);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.findService("AICore")).thenReturn(Optional.of(mock(CdsService.class)));
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    lenient().when(environment.getProperty(any(String.class), any(Class.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    lenient().when(environment.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    CdsProperties cdsProperties = new CdsProperties();
    CdsProperties.MultiTenancy mt = new CdsProperties.MultiTenancy();
    CdsProperties.MultiTenancy.Sidecar sidecar = new CdsProperties.MultiTenancy.Sidecar();
    // No URL set - defaults to null
    mt.setSidecar(sidecar);
    cdsProperties.setMultiTenancy(mt);
    when(environment.getCdsProperties()).thenReturn(cdsProperties);
    when(serviceCatalog.getService(any(Class.class), any())).thenReturn(null);

    AICoreServiceConfiguration config = new AICoreServiceConfiguration();
    config.services(configurer);

    ArgumentCaptor<MockAICoreServiceImpl> captor =
        ArgumentCaptor.forClass(MockAICoreServiceImpl.class);
    verify(configurer).service(captor.capture());
    assert !captor.getValue().isMultiTenancyEnabled();
  }
}
