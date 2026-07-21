/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.handlers.DocumentAiSetupHandler;
import com.sap.cds.feature.documentai.handlers.DocumentSubmissionHandler;
import com.sap.cds.feature.documentai.service.DefaultDocumentAiProcessingService;
import com.sap.cds.feature.documentai.service.ExtractionServiceImpl;
import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAiServiceConfigurationTest {

  @Mock CdsRuntimeConfigurer configurer;
  @Mock CdsRuntime cdsRuntime;
  @Mock ServiceCatalog serviceCatalog;
  @Mock PersistenceService persistenceService;
  @Mock CdsEnvironment environment;
  @Mock CdsProperties cdsProperties;
  @Mock CdsProperties.MultiTenancy multiTenancy;
  @Mock CdsProperties.MultiTenancy.Sidecar sidecar;

  DocumentAiServiceConfiguration registration;

  @BeforeEach
  void setUp() {
    registration = new DocumentAiServiceConfiguration();
  }

  @Test
  void servicesRegistersExtractionService() {
    registration.services(configurer);

    ArgumentCaptor<Service> captor = ArgumentCaptor.forClass(Service.class);
    verify(configurer).service(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(ExtractionServiceImpl.class);
  }

  @Test
  void eventHandlersRegistersDocumentSubmissionHandler() {
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    when(environment.getProperty(
            eq("cds.document-ai.polling.interval-seconds"), eq(Integer.class), any()))
        .thenReturn(3);
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    // stub MT detection
    when(environment.getCdsProperties()).thenReturn(cdsProperties);
    when(cdsProperties.getMultiTenancy()).thenReturn(multiTenancy);
    when(multiTenancy.getSidecar()).thenReturn(sidecar);
    when(sidecar.getUrl()).thenReturn(null);
    when(serviceCatalog.getService(DeploymentService.class, DeploymentService.DEFAULT_NAME))
        .thenReturn(null);

    registration.services(configurer);
    registration.eventHandlers(configurer);

    ArgumentCaptor<EventHandler> captor = ArgumentCaptor.forClass(EventHandler.class);
    verify(configurer, times(1)).eventHandler(captor.capture());

    assertThat(captor.getValue()).isInstanceOf(DocumentSubmissionHandler.class);
  }

  @Test
  void buildDocumentAi_noBindingFound_returnsNull() {
    when(environment.getServiceBindings()).thenReturn(Stream.empty());

    DocumentAiClient result = DocumentAiServiceConfiguration.buildDocumentAi(environment);

    assertThat(result).isNull();
  }

  @Test
  void buildDocumentAi_bindingFound_destinationCreationFails_returnsNull() {
    ServiceBinding binding = mock(ServiceBinding.class);
    when(environment.getServiceBindings()).thenReturn(Stream.of(binding));

    try (var utils = mockStatic(ServiceBindingUtils.class);
        var loader = mockStatic(ServiceBindingDestinationLoader.class)) {
      utils
          .when(
              () ->
                  ServiceBindingUtils.matches(
                      any(), eq(DefaultDocumentAiProcessingService.SAP_DOCUMENT_AI_SERVICE_LABEL)))
          .thenReturn(true);

      ServiceBindingDestinationLoader loaderMock = mock(ServiceBindingDestinationLoader.class);
      loader.when(ServiceBindingDestinationLoader::defaultLoaderChain).thenReturn(loaderMock);
      when(loaderMock.getDestination(any())).thenThrow(new RuntimeException("destination fail"));

      DocumentAiClient result = DocumentAiServiceConfiguration.buildDocumentAi(environment);

      assertThat(result).isNull();
    }
  }

  @Test
  void detectMultiTenancy_withSidecarUrl_returnsTrue() {
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    when(environment.getCdsProperties()).thenReturn(cdsProperties);
    when(cdsProperties.getMultiTenancy()).thenReturn(multiTenancy);
    when(multiTenancy.getSidecar()).thenReturn(sidecar);
    when(sidecar.getUrl()).thenReturn("https://sidecar.example.com");

    assertThat(DocumentAiServiceConfiguration.detectMultiTenancy(cdsRuntime)).isTrue();
  }

  @Test
  void detectMultiTenancy_withDeploymentService_returnsTrue() {
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(environment.getCdsProperties()).thenReturn(cdsProperties);
    when(cdsProperties.getMultiTenancy()).thenReturn(multiTenancy);
    when(multiTenancy.getSidecar()).thenReturn(sidecar);
    when(sidecar.getUrl()).thenReturn(null);
    when(serviceCatalog.getService(DeploymentService.class, DeploymentService.DEFAULT_NAME))
        .thenReturn(mock(DeploymentService.class));

    assertThat(DocumentAiServiceConfiguration.detectMultiTenancy(cdsRuntime)).isTrue();
  }

  @Test
  void eventHandlers_multiTenancyEnabled_registersSetupHandler() {
    when(configurer.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(cdsRuntime.getEnvironment()).thenReturn(environment);
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    when(environment.getProperty(
            eq("cds.document-ai.polling.interval-seconds"), eq(Integer.class), any()))
        .thenReturn(3);
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    // MT enabled via sidecar URL
    when(environment.getCdsProperties()).thenReturn(cdsProperties);
    when(cdsProperties.getMultiTenancy()).thenReturn(multiTenancy);
    when(multiTenancy.getSidecar()).thenReturn(sidecar);
    when(sidecar.getUrl()).thenReturn("https://sidecar.example.com");

    registration.services(configurer);
    registration.eventHandlers(configurer);

    ArgumentCaptor<EventHandler> captor = ArgumentCaptor.forClass(EventHandler.class);
    verify(configurer, atLeast(2)).eventHandler(captor.capture());

    assertThat(captor.getAllValues()).anyMatch(h -> h instanceof DocumentAiSetupHandler);
  }
}
