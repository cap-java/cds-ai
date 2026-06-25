/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.service.exceptions.DocumentAiException;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDocumentAiClientTest {

  private static final String BASE_URL = "https://example.com/";
  private static final String JOB_ID = "job-abc-123";

  @Mock HttpDestination destination;
  @Mock HttpClient httpClient;
  @Mock CloseableHttpResponse response;
  @Mock StatusLine statusLine;
  @Mock HttpEntity entity;

  DefaultDocumentAiClient client;
  DocumentInput documentInput;

  @BeforeEach
  void setUp() {
    client = new DefaultDocumentAiClient(destination, httpClient);
    documentInput =
        new DocumentInput(
            "invoice.pdf", "application/pdf", new ByteArrayInputStream("pdf-bytes".getBytes()));
    when(destination.getUri()).thenReturn(URI.create(BASE_URL));
  }

  @Test
  void submitDocumentReturnsJobIdOnSuccess() throws IOException {
    mockHttpResponse(200, "{\"id\":\"" + JOB_ID + "\"}");

    String result = client.submitDocument(documentInput);

    assertThat(result).isEqualTo(JOB_ID);
  }

  @Test
  void submitDocumentThrowsRequestExceptionOnNon2xxResponse() throws IOException {
    mockHttpResponse(400, "Bad Request");

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(DocumentAiException.Request.class)
        .hasMessageContaining("400");
  }

  @Test
  void submitDocumentThrowsConnectivityExceptionOnIoFailure() throws IOException {
    when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("timeout"));

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(DocumentAiException.Connectivity.class)
        .hasMessageContaining(BASE_URL);
  }

  @Test
  void submitDocumentThrowsWhenResponseHasNoIdField() throws IOException {
    mockHttpResponse(200, "{\"status\":\"ok\"}");

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected DIE response");
  }

  @Test
  void submitDocumentThrowsWhenResponseIsNotValidJson() throws IOException {
    mockHttpResponse(200, "not-json{{{{");

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to parse DIE response");
  }

  private void mockHttpResponse(int statusCode, String body) throws IOException {
    when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine);
    when(response.getEntity()).thenReturn(entity);
    when(statusLine.getStatusCode()).thenReturn(statusCode);
    when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
    when(entity.getContentLength()).thenReturn(-1L);
  }
}
