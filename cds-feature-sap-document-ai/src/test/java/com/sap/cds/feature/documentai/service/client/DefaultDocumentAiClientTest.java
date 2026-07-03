/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.service.exceptions.DocumentAiException;
import com.sap.cds.feature.documentai.service.model.DocumentInput;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDocumentAiClientTest {

  private static final String BASE_URL = "https://example.com/";
  private static final String JOB_ID = "job-abc-123";

  @Mock HttpDestination destination;
  @Mock HttpClient httpClient;
  @Mock ClassicHttpResponse response;
  @Mock HttpEntity entity;

  DefaultDocumentAiClient client;
  DocumentInput documentInput;

  @BeforeEach
  void setUp() {
    client = new DefaultDocumentAiClient(destination, httpClient);
    documentInput =
        new DocumentInput(
            "invoice.pdf",
            "application/pdf",
            new ByteArrayInputStream("pdf-bytes".getBytes()),
            null);
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
    when(httpClient.execute(any(HttpUriRequestBase.class), any(HttpClientResponseHandler.class)))
        .thenThrow(new IOException("timeout"));

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(DocumentAiException.Connectivity.class)
        .hasMessageContaining(BASE_URL);
  }

  @Test
  void submitDocumentThrowsWhenResponseHasNoIdField() throws IOException {
    mockHttpResponse(200, "{\"status\":\"ok\"}");

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(DocumentAiException.Processing.class)
        .hasMessageContaining("Unexpected DIE response");
  }

  @Test
  void submitDocumentThrowsWhenResponseIsNotValidJson() throws IOException {
    mockHttpResponse(200, "not-json{{{{");

    assertThatThrownBy(() -> client.submitDocument(documentInput))
        .isInstanceOf(DocumentAiException.Processing.class)
        .hasMessageContaining("Failed to parse DIE response");
  }

  @Test
  void getJobResultReturnsStatusAndRawBody() throws IOException {
    String responseBody = "{\"id\":\"" + JOB_ID + "\",\"status\":\"DONE\",\"extraction\":{}}";
    mockHttpResponse(200, responseBody);

    ExtractionData result = client.getJobResult(JOB_ID);

    assertThat(result.dieJobId()).isEqualTo(JOB_ID);
    assertThat(result.dieStatus()).isEqualTo("DONE");
    assertThat(result.rawResult()).isEqualTo(responseBody);
  }

  @Test
  void getJobResultThrowsWhenStatusFieldMissing() throws IOException {
    mockHttpResponse(200, "{\"id\":\"" + JOB_ID + "\",\"extraction\":{}}");

    assertThatThrownBy(() -> client.getJobResult(JOB_ID))
        .isInstanceOf(DocumentAiException.Processing.class)
        .hasMessageContaining("missing 'status' field");
  }

  @Test
  void getJobResultThrowsRequestExceptionOnNon2xxResponse() throws IOException {
    mockHttpResponse(404, "Not Found");

    assertThatThrownBy(() -> client.getJobResult(JOB_ID))
        .isInstanceOf(DocumentAiException.Request.class)
        .hasMessageContaining("404");
  }

  @Test
  void getJobResultThrowsConnectivityExceptionOnIoFailure() throws IOException {
    when(httpClient.execute(any(HttpUriRequestBase.class), any(HttpClientResponseHandler.class)))
        .thenThrow(new IOException("timeout"));

    assertThatThrownBy(() -> client.getJobResult(JOB_ID))
        .isInstanceOf(DocumentAiException.Connectivity.class);
  }

  @Test
  void getJobResultThrowsWhenResponseIsNotValidJson() throws IOException {
    mockHttpResponse(200, "not-json{{{{");

    assertThatThrownBy(() -> client.getJobResult(JOB_ID))
        .isInstanceOf(DocumentAiException.Processing.class)
        .hasMessageContaining("Failed to parse DIE job result response");
  }

  @Test
  void submitDocumentUsesOctetStreamWhenMimeTypeIsNull() throws IOException {
    documentInput =
        new DocumentInput("invoice.pdf", null, new ByteArrayInputStream("bytes".getBytes()), null);
    mockHttpResponse(200, "{\"id\":\"" + JOB_ID + "\"}");

    String result = client.submitDocument(documentInput);

    assertThat(result).isEqualTo(JOB_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void submitDocumentUsesEmptyJsonWhenOptionsIsNull() throws IOException {
    ArgumentCaptor<HttpPost> requestCaptor = ArgumentCaptor.forClass(HttpPost.class);
    when(httpClient.execute(requestCaptor.capture(), any(HttpClientResponseHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpClientResponseHandler<Object> handler = invocation.getArgument(1);
              when(response.getCode()).thenReturn(200);
              when(response.getEntity()).thenReturn(entity);
              when(entity.getContent())
                  .thenReturn(new ByteArrayInputStream(("{\"id\":\"" + JOB_ID + "\"}").getBytes()));
              when(entity.getContentLength()).thenReturn(-1L);
              return handler.handleResponse(response);
            });

    client.submitDocument(documentInput);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    requestCaptor.getValue().getEntity().writeTo(buffer);
    String requestBody = buffer.toString();
    assertThat(requestBody).contains("{}").contains("options");
  }

  @SuppressWarnings("unchecked")
  private void mockHttpResponse(int statusCode, String body) throws IOException {
    when(response.getCode()).thenReturn(statusCode);
    when(response.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
    when(entity.getContentLength()).thenReturn(-1L);
    when(httpClient.execute(any(HttpUriRequestBase.class), any(HttpClientResponseHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpClientResponseHandler<Object> handler = invocation.getArgument(1);
              return handler.handleResponse(response);
            });
  }
}
