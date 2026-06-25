/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.service.exceptions.DocumentAiException;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDocumentAiClient implements DocumentAiClient {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDocumentAiClient.class);
  private static final String DOCUMENT_AI_API_PATH = "document-information-extraction/v1";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpDestination destination;
  private final HttpClient httpClient;

  public DefaultDocumentAiClient(HttpDestination destination, HttpClient httpClient) {
    this.destination = destination;
    this.httpClient = httpClient;
  }

  @Override
  public String submitDocument(DocumentInput documentInput) {
    URI submitUri = buildSubmitUri();
    HttpPost request = buildSubmitRequest(documentInput, submitUri);
    String body = executeRequest(request, submitUri);
    return extractJobId(body);
  }

  private URI buildSubmitUri() {
    String base = destination.getUri().toString();
    String path = base.endsWith("/") ? base : base + "/";
    return URI.create(path).resolve(DOCUMENT_AI_API_PATH + "/document/jobs");
  }

  private HttpPost buildSubmitRequest(DocumentInput documentInput, URI submitUri) {
    logger.info(
        "[sap-document-ai] Submitting document to DIE at url={}, fileName={}, mimeType={}",
        submitUri,
        documentInput.fileName(),
        documentInput.mimeType());

    String optionsJson = buildOptionsJson();

    ContentType contentType =
        documentInput.mimeType() != null
            ? ContentType.create(documentInput.mimeType())
            : ContentType.APPLICATION_OCTET_STREAM;
    HttpPost request = new HttpPost(submitUri);
    request.setEntity(
        MultipartEntityBuilder.create()
            .addBinaryBody("file", documentInput.content(), contentType, documentInput.fileName())
            .addTextBody("options", optionsJson, ContentType.APPLICATION_JSON)
            .build());

    logger.info("[sap-document-ai] POST {} | Headers: {}", submitUri, request.getAllHeaders());
    return request;
  }

  private String executeRequest(HttpPost request, URI submitUri) {

    try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(request)) {

      String body = EntityUtils.toString(response.getEntity());

      int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode < 200 || statusCode >= 300) {
        throw new DocumentAiException.Request(statusCode, body);
      }

      return body;

    } catch (IOException e) {
      throw new DocumentAiException.Connectivity(submitUri.toString(), e);
    }
  }

  private String extractJobId(String body) {
    try {
      JsonNode json = objectMapper.readTree(body);

      if (!json.has("id")) {
        throw new RuntimeException("Unexpected DIE response. body=" + body);
      }

      String jobId = json.get("id").asText();
      logger.info("[sap-document-ai] Document submitted successfully, DIE jobId={}", jobId);
      return jobId;

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to parse DIE response", e);
    }
  }

  private String buildOptionsJson() {
    // TODO: Currently options are hard-coded. Make these dynamic
    Map<String, Object> options =
        Map.of(
            "clientId", "default",
            "documentType", "invoice",
            "receivedDate", "2020-02-17",
            "schemaId", "cf8cc8a9-1eee-42d9-9a3e-507a61baac23",
            "templateId", "detect",
            "candidateTemplateIds", List.of(),
            "enrichment", Map.of());
    return objectMapper.valueToTree(options).toString();
  }
}
