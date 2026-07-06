/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service;

import static org.mockito.ArgumentMatchers.any;

import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.feature.documentai.service.exceptions.DocumentAiException;
import com.sap.cds.feature.documentai.service.model.DocumentInput;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/*
 * Temporary tests until the real implementation is done
 */
class DefaultDocumentAiProcessingServiceTest {

  public static final String TEST_PDF = "test.pdf";
  public static final String CONTENT_TYPE = "application/pdf";
  public static final String TEST_CONTENT = "test";
  public static final String JOB_1 = "job-1";
  public static final String MOCK_RESULT = "mock-result";
  DocumentAiClient documentAiClient;
  DefaultDocumentAiProcessingService service;
  DocumentInput documentInput;

  @BeforeEach
  void setUp() {
    documentAiClient = Mockito.mock(DocumentAiClient.class);
    Mockito.when(documentAiClient.submitDocument(any())).thenReturn(MOCK_RESULT);
    service = new DefaultDocumentAiProcessingService(documentAiClient);
    documentInput =
        new DocumentInput(
            TEST_PDF,
            CONTENT_TYPE,
            new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)),
            null);
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
    Assertions.assertThatCode(() -> service.processDocument(JOB_1, documentInput))
        .doesNotThrowAnyException();
  }

  @Test
  void processDocumentThrowsWhenSubmitDocumentFails() {
    Mockito.when(documentAiClient.submitDocument(any()))
        .thenThrow(new RuntimeException("submit failed"));
    Assertions.assertThatThrownBy(() -> service.processDocument(JOB_1, documentInput))
        .isInstanceOf(DocumentAiException.Processing.class);
  }
}
