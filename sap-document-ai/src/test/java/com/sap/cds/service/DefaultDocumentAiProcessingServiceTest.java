/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.cds.service.documentai.client.DocumentAiClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/*
 * Temporary tests until the real implementation is done
 * */
class DefaultDocumentAiProcessingServiceTest {

  public static final String TEST = "test";
  DocumentAiClient documentAiClient;
  DefaultDocumentAiProcessingService service;

  @BeforeEach
  void setUp() throws Exception {
    documentAiClient = mock(DocumentAiClient.class);
    when(documentAiClient.submitDocument(any())).thenReturn("mock-result");
    service = new DefaultDocumentAiProcessingService(documentAiClient);
  }

  @Test
  void processDocumentCompletesWithoutException() {
    InputStream content = new ByteArrayInputStream(TEST.getBytes());
    assertThatCode(() -> service.processDocument("job-1", content)).doesNotThrowAnyException();
  }

  @Test
  void processDocumentHandlesInterruption() throws InterruptedException {
    InputStream content = new ByteArrayInputStream(TEST.getBytes());
    Thread thread =
        new Thread(
            () -> {
              assertThatCode(() -> service.processDocument("job-2", content))
                  .doesNotThrowAnyException();
            });
    thread.start();
    thread.interrupt();
    thread.join();
  }
}
