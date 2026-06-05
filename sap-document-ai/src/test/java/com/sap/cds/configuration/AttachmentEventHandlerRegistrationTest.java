/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.service.DefaultDocumentAiProcessingService;
import com.sap.cds.service.documentai.client.DocumentAiClient;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpClientAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationLoader;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerRegistrationTest {

  @Mock CdsEnvironment environment;

  @Mock ServiceBinding serviceBinding;

  @Mock HttpDestination httpDestination;

  @Mock ServiceBindingDestinationLoader destinationLoader;

  @Mock HttpClient httpClient;

  @Test
  void buildDocumentAi_noBindingFound_returnsNull() {
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    DocumentAiClient result = AttachmentEventHandlerRegistration.buildDocumentAi(environment);
    assertThat(result).isNull();
  }

  @Test
  void buildDocumentAi_bindingFound_destinationCreated_returnsClient() {
    // Arrange
    when(environment.getServiceBindings()).thenReturn(Stream.of(serviceBinding));

    withStaticMocks(
        mocks -> {
          mocks
              .destinationLoader
              .when(ServiceBindingDestinationLoader::defaultLoaderChain)
              .thenReturn(destinationLoader);
          when(destinationLoader.getDestination(any(ServiceBindingDestinationOptions.class)))
              .thenReturn(httpDestination);
          mocks
              .clientAccessor
              .when(() -> HttpClientAccessor.getHttpClient(httpDestination))
              .thenReturn(httpClient);
          // Act
          DocumentAiClient result = AttachmentEventHandlerRegistration.buildDocumentAi(environment);
          // Assert
          assertThat(result).isNotNull();
        });
  }

  @Test
  void buildDocumentAi_bindingFound_destinationCreationFails_returnsNull() {
    // Arrange
    when(environment.getServiceBindings()).thenReturn(Stream.of(serviceBinding));

    withStaticMocks(
        mocks -> {
          mocks
              .destinationLoader
              .when(ServiceBindingDestinationLoader::defaultLoaderChain)
              .thenReturn(destinationLoader);
          when(destinationLoader.getDestination(any(ServiceBindingDestinationOptions.class)))
              .thenThrow(new RuntimeException("failed to create destination"));
          // Act
          DocumentAiClient result = AttachmentEventHandlerRegistration.buildDocumentAi(environment);
          // Assert
          assertThat(result).isNull();
        });
  }

  private void withStaticMocks(Consumer<StaticMocks> test) {
    try (MockedStatic<ServiceBindingUtils> utilsMockedStatic =
            mockStatic(ServiceBindingUtils.class);
        MockedStatic<ServiceBindingDestinationLoader> destinationLoaderMockedStatic =
            mockStatic(ServiceBindingDestinationLoader.class);
        MockedStatic<HttpClientAccessor> clientAccessorMockedStatic =
            mockStatic(HttpClientAccessor.class)) {
      utilsMockedStatic
          .when(
              () ->
                  ServiceBindingUtils.matches(
                      serviceBinding,
                      DefaultDocumentAiProcessingService.SAP_DOCUMENT_AI_SERVICE_LABEL))
          .thenReturn(true);
      test.accept(new StaticMocks(destinationLoaderMockedStatic, clientAccessorMockedStatic));
    }
  }

  private record StaticMocks(
      MockedStatic<ServiceBindingDestinationLoader> destinationLoader,
      MockedStatic<HttpClientAccessor> clientAccessor) {}
}
