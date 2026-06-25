/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExceptionsTest {

  @Test
  void documentAiConnectivityExceptionContainsUrlAndCause() {
    IOException cause = new IOException("connection refused");
    String url = "https://example.com/die";
    DocumentAiException.Connectivity ex = new DocumentAiException.Connectivity(url, cause);

    assertThat(ex.getMessage()).contains(url);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void documentAiProcessingExceptionContainsMessageAndCause() {
    RuntimeException cause = new RuntimeException("timeout");
    String message = "Failed to process jobId=123";
    DocumentAiException.Processing ex = new DocumentAiException.Processing(message, cause);

    assertThat(ex.getMessage()).isEqualTo(message);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void documentAiRequestExceptionContainsStatusCodeAndBody() {
    String badRequest = "Bad Request";
    DocumentAiException.Request ex = new DocumentAiException.Request(400, badRequest);

    assertThat(ex.getStatusCode()).isEqualTo(400);
    assertThat(ex.getResponseBody()).isEqualTo(badRequest);
    assertThat(ex.getMessage()).contains("400").contains(badRequest);
  }

  @Test
  void illegalStatusTransitionExceptionContainsMessage() {
    String message = "Invalid transition from PENDING to COMPLETED";
    IllegalStatusTransitionException ex = new IllegalStatusTransitionException(message);

    assertThat(ex.getMessage()).isEqualTo(message);
  }

  @Test
  void concurrentJobUpdateExceptionContainsMessage() {
    String message = "Concurrent update detected for jobId=abc";
    ConcurrentJobUpdateException ex = new ConcurrentJobUpdateException(message);

    assertThat(ex.getMessage()).isEqualTo(message);
  }
}
