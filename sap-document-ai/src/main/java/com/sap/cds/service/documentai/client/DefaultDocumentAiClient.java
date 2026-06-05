/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.io.InputStream;
import java.net.URI;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDocumentAiClient implements DocumentAiClient {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDocumentAiClient.class);

  private final HttpDestination destination;

  // TODO: Remove this suppress warning once httpClient is used in submitDocument
  @SuppressWarnings("PMD.UnusedPrivateField")
  private final HttpClient httpClient;

  public DefaultDocumentAiClient(HttpDestination destination, HttpClient httpClient) {
    this.destination = destination;
    this.httpClient = httpClient;
  }

  @Override
  public String submitDocument(InputStream content) {
    URI baseUri = destination.getUri();
    logger.info("[sap-document-ai] Submitting document to DIE at url={}", baseUri);
    return null;
  }
}
