/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.service.exceptions.DocumentAiException;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.service.model.ExtractionData;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link DocumentAiClient} implementation that communicates with the DIE REST API over HTTP
 * using the SAP Cloud SDK destination and Apache HttpClient 5.
 *
 * <p>Two operations are provided:
 *
 * <ul>
 *   <li>{@link #submitDocument} — POSTs a multipart request containing the document file and a JSON
 *       options body, then parses the DIE job ID from the response.
 *   <li>{@link #getJobResult} — GETs the current status and extracted values for a previously
 *       submitted DIE job.
 * </ul>
 *
 * <p>All HTTP failures and unexpected response shapes are wrapped in the appropriate {@link
 * com.sap.cds.service.exceptions.DocumentAiException} subclass.
 */
public class DefaultDocumentAiClient implements DocumentAiClient {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDocumentAiClient.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String DOCUMENT_AI_API_PATH = "document-information-extraction/v1";
  public static final String DOCUMENT_JOBS = "/document/jobs";
  public static final String EXTRACTED_VALUES_TRUE = "?extractedValues=true";
  private final HttpDestination destination;
  private final HttpClient httpClient;

  /**
   * @param destination the pre-configured SAP Cloud SDK HTTP destination pointing to the DIE
   *     service base URL with OAuth2 credentials
   * @param httpClient the Apache HttpClient 5 instance used for all HTTP calls
   */
  public DefaultDocumentAiClient(HttpDestination destination, HttpClient httpClient) {
    this.destination = destination;
    this.httpClient = httpClient;
  }

  @Override
  public String submitDocument(DocumentInput documentInput) {
    URI submitUri = buildUri(DOCUMENT_AI_API_PATH + DOCUMENT_JOBS);
    HttpPost request = buildSubmitRequest(documentInput, submitUri);
    String body = executeRequest(request, submitUri);
    return extractJobId(body);
  }

  @Override
  public ExtractionData getJobResult(String dieJobId) {
    URI uri =
        buildUri(DOCUMENT_AI_API_PATH + DOCUMENT_JOBS + "/" + dieJobId + EXTRACTED_VALUES_TRUE);
    logger.info("[sap-document-ai] Polling DIE for dieJobId={}", dieJobId);
    HttpGet request = new HttpGet(uri);
    String body = executeRequest(request, uri);
    return parseJobResult(dieJobId, body);
  }

  private URI buildUri(String path) {
    String base = destination.getUri().toString();
    String prefix = base.endsWith("/") ? base : base + "/";
    return URI.create(prefix).resolve(path);
  }

  private HttpPost buildSubmitRequest(DocumentInput documentInput, URI submitUri) {
    logger.info(
        "[sap-document-ai] Submitting document to DIE at url={}, fileName={}, mimeType={}",
        submitUri,
        documentInput.fileName(),
        documentInput.mimeType());

    ContentType contentType =
        documentInput.mimeType() != null
            ? ContentType.create(documentInput.mimeType())
            : ContentType.APPLICATION_OCTET_STREAM;
    String options = documentInput.options();
    if (options == null) {
      logger.warn(
          "[sap-document-ai] No options provided for fileName={}, sending empty options to DIE",
          documentInput.fileName());
      options = "{}";
    }
    HttpPost request = new HttpPost(submitUri);
    request.setEntity(
        MultipartEntityBuilder.create()
            .addBinaryBody("file", documentInput.content(), contentType, documentInput.fileName())
            .addTextBody("options", options, ContentType.APPLICATION_JSON)
            .build());

    return request;
  }

  private String executeRequest(HttpUriRequestBase request, URI uri) {
    try {
      return httpClient.execute(
          request,
          response -> {
            String body = EntityUtils.toString(response.getEntity());
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
              throw new DocumentAiException.Request(statusCode, body);
            }
            return body;
          });
    } catch (IOException e) {
      throw new DocumentAiException.Connectivity(uri.toString(), e);
    }
  }

  private String extractJobId(String body) {
    try {
      JsonNode json = objectMapper.readTree(body);

      if (!json.has("id")) {
        throw new DocumentAiException.Processing("Unexpected DIE response. body=" + body, null);
      }

      String jobId = json.get("id").asText();
      logger.info("[sap-document-ai] Document submitted successfully, DIE jobId={}", jobId);
      return jobId;

    } catch (JsonProcessingException e) {
      throw new DocumentAiException.Processing("Failed to parse DIE response", e);
    }
  }

  private ExtractionData parseJobResult(String dieJobId, String body) {
    try {
      JsonNode json = objectMapper.readTree(body);
      String status = json.path("status").asText();
      if (status.isEmpty()) {
        throw new DocumentAiException.Processing(
            "DIE job response missing 'status' field for dieJobId=" + dieJobId + ". body=" + body,
            null);
      }
      logger.debug("[sap-document-ai] DIE job dieJobId={} status={}", dieJobId, status);
      return new ExtractionData(dieJobId, status, body);
    } catch (JsonProcessingException e) {
      throw new DocumentAiException.Processing("Failed to parse DIE job result response", e);
    }
  }
}
