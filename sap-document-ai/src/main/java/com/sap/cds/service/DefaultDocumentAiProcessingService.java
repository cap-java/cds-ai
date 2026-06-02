/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.documentai.client.DocumentAiClient;
import java.io.InputStream;
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
  public void processDocument(String jobId, InputStream content) {
    logger.info(
        "[sap-document-ai] Processing document for jobId={} and content={}", jobId, content);
    try {
      // TODO: Replace mock delay with real Document AI integration
      Thread.sleep(3000);
      String result = documentAiClient.submitDocument(content);
      logger.info("logging the result after submitting document {}", result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("[sap-document-ai] Interrupted during extraction for jobId={}", jobId, e);
    } catch (Exception e) {
      logger.error("[sap-document-ai] Extraction failed for jobId={}", jobId, e);
    }
  }
}
