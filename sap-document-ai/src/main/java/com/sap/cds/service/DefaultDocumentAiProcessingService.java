/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.documentai.client.DocumentAiClient;
import com.sap.cds.service.exceptions.DocumentAiException;
import com.sap.cds.service.model.DocumentInput;

/**
 * Default implementation of {@link DocumentAiProcessingService}.
 *
 * <p>Delegates directly to {@link DocumentAiClient}. When no DIE service binding is configured, the
 * configuration layer passes {@code null} as the client and {@link #isAvailable()} returns {@code
 * false}, allowing the rest of the plugin to remain operational while queuing jobs as {@code
 * PENDING}.
 */
public class DefaultDocumentAiProcessingService implements DocumentAiProcessingService {

  public static final String SAP_DOCUMENT_AI_SERVICE_LABEL = "sap-document-information-extraction";

  private final DocumentAiClient documentAiClient;

  public DefaultDocumentAiProcessingService(DocumentAiClient documentAiClient) {
    this.documentAiClient = documentAiClient;
  }

  @Override
  public String processDocument(String jobId, DocumentInput documentInput) {
    try {
      String documentAiJobId = documentAiClient.submitDocument(documentInput);
      return documentAiJobId;
    } catch (Exception e) {
      throw new DocumentAiException.Processing("Failed to process document for jobId=" + jobId, e);
    }
  }

  @Override
  public boolean isAvailable() {
    return documentAiClient != null;
  }
}
