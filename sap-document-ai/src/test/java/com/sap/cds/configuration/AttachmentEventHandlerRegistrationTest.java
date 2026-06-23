/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.handlers.DocumentSubmissionHandler;
import com.sap.cds.service.DefaultDocumentAiProcessingService;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.service.documentai.client.DocumentAiClient;
import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationLoader;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerRegistrationTest {

  @Mock CdsRuntimeConfigurer configurer;
  @Mock CdsRuntime cdsRuntime;
  @Mock ServiceCatalog serviceCatalog;
  @Mock PersistenceService persistenceService;
  @Mock OutboxService outboxService;
  @Mock DocumentAiService documentAiService;
  @Mock CdsEnvironment environment;

  AttachmentEventHandlerRegistration registration;

  @BeforeEach
  void setUp() {
    registration = new AttachmentEventHandlerRegistration();
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
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);
    when(serviceCatalog.getService(OutboxService.class, OutboxService.PERSISTENT_UNORDERED_NAME))
        .thenReturn(outboxService);
    when(serviceCatalog.getService(DocumentAiService.class, DocumentAiService_.CDS_NAME))
        .thenReturn(documentAiService);
    ExtractionService outboxed = mock(ExtractionService.class);
    doReturn(outboxed).when(outboxService).outboxed(any(ExtractionService.class));

    registration.services(configurer);
    registration.eventHandlers(configurer);

    ArgumentCaptor<EventHandler> captor = ArgumentCaptor.forClass(EventHandler.class);
    verify(configurer, atLeast(2)).eventHandler(captor.capture());

    List<EventHandler> handlers = captor.getAllValues();
    assertThat(handlers.stream().anyMatch(h -> h.getClass() == DocumentSubmissionHandler.class))
        .isTrue();
  }

  @Test
  void buildDocumentAi_noBindingFound_returnsNull() {
    when(environment.getServiceBindings()).thenReturn(Stream.empty());

    DocumentAiClient result = AttachmentEventHandlerRegistration.buildDocumentAi(environment);

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

      DocumentAiClient result = AttachmentEventHandlerRegistration.buildDocumentAi(environment);

      assertThat(result).isNull();
    }
  }
}
