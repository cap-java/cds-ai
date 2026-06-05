/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.documentai.client.DocumentAiClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/*
 * Temporary tests until the real implementation is done
 */
class DefaultDocumentAiProcessingServiceTest {

  public static final String TEST = "test";
  DocumentAiClient documentAiClient;
  DefaultDocumentAiProcessingService service;

  @BeforeEach
  void setUp() {
    documentAiClient = Mockito.mock(DocumentAiClient.class);
    Mockito.when(documentAiClient.submitDocument(ArgumentMatchers.any())).thenReturn("mock-result");
    service = new DefaultDocumentAiProcessingService(documentAiClient);
  }

  // ----- isAvailable() -------
  @Test
  void isAvailableReturnsTrueWhenClientPresent() {
    Assertions.assertThat(service.isAvailable()).isTrue();
  }

  @Test
  void isAvailableReturnsFalseWhenClientNull() {
    Assertions.assertThat(new DefaultDocumentAiProcessingService(null).isAvailable()).isFalse();
  }

  // ------- processDocument() -------
  @Test
  void processDocumentCompletesWithoutException() {
    InputStream content = new ByteArrayInputStream(TEST.getBytes());
    Assertions.assertThatCode(() -> service.processDocument("job-1", content))
        .doesNotThrowAnyException();
  }

  @Test
  void processDocumentHandlesSubmitDocumentException() {
    Mockito.when(documentAiClient.submitDocument(ArgumentMatchers.any()))
        .thenThrow(new RuntimeException("submit failed"));
    InputStream content = new ByteArrayInputStream(TEST.getBytes());
    Assertions.assertThatCode(() -> service.processDocument("job-1", content))
        .doesNotThrowAnyException();
  }
}
