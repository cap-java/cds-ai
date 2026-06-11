/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.documentai.client.DocumentAiClient;
import com.sap.cds.service.exceptions.DocumentAiException;
import com.sap.cds.service.model.DocumentInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDocumentAiProcessingService implements DocumentAiProcessingService {

  private static final Logger logger =
      LoggerFactory.getLogger(DefaultDocumentAiProcessingService.class);
  public static final String SAP_DOCUMENT_AI_SERVICE_LABEL = "sap-document-information-extraction";

  private final DocumentAiClient documentAiClient;

  public DefaultDocumentAiProcessingService(DocumentAiClient documentAiClient) {
    this.documentAiClient = documentAiClient;
  }

  @Override
  public String processDocument(String jobId, DocumentInput documentInput) {
    logger.info(
        "[sap-document-ai] Processing document for jobId={}, fileName={}",
        jobId,
        documentInput.fileName());

    try {
      String documentAiJobId = documentAiClient.submitDocument(documentInput);
      logger.info(
          "[sap-document-ai] Document submitted successfully for jobId={}, DIE jobId={}",
          jobId,
          documentAiJobId);
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
