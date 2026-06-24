/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.handlers;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class DocumentSubmissionHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DocumentSubmissionHandler.class);

  private final ExtractionService extractionService;

  public DocumentSubmissionHandler(ExtractionService extractionService) {
    this.extractionService = extractionService;
  }

  @On(event = DocumentExtractionContext.CDS_NAME)
  public void onDocumentExtraction(DocumentExtractionContext context) {
    DocumentExtraction event = context.getData();
    String tenantId = context.getUserInfo().getTenant();

    logger.info(
        "[sap-document-ai] DocumentExtraction event received, fileName={}", event.getFileName());

    ExtractionResult result =
        extractionService.triggerExtraction(
            event.getFileName(), event.getMimeType(), event.getContent(), tenantId);

    switch (result.status()) {
      case FAILED ->
          logger.error("[sap-document-ai] Extraction failed for fileName={}", event.getFileName());
      case PENDING -> logger.warn("[sap-document-ai] Document AI unavailable, left as PENDING");
    }

    context.setCompleted();
  }
}
