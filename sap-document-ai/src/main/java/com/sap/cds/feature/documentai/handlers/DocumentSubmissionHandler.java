/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.handlers;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDS event handler that listens for {@code DocumentExtraction} events on any {@link
 * ApplicationService} and delegates to {@link ExtractionService} to create and submit an extraction
 * job.
 *
 * <p>The handler is intentionally service-name-agnostic ({@code @ServiceName(value = "*")}) so
 * consumer applications can emit {@code DocumentExtraction} from their own CAP service without
 * needing to couple to the plugin's internal service name.
 */
@ServiceName(value = "*", type = ApplicationService.class)
public class DocumentSubmissionHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DocumentSubmissionHandler.class);

  private final ExtractionService extractionService;

  public DocumentSubmissionHandler(ExtractionService extractionService) {
    this.extractionService = extractionService;
  }

  /**
   * Handles an incoming {@code DocumentExtraction} event.
   *
   * <p>Extracts the file metadata and content from the event context, calls {@link
   * ExtractionService#triggerExtraction}, and logs a warning or error if the job could not be
   * submitted immediately.
   *
   * @param context the CDS event context carrying the {@link DocumentExtraction} payload
   */
  @On(event = DocumentExtractionContext.CDS_NAME)
  public void onDocumentExtraction(DocumentExtractionContext context) {
    DocumentExtraction event = context.getData();
    String tenantId = context.getUserInfo().getTenant();

    logger.info(
        "[sap-document-ai] DocumentExtraction event received, fileName={}", event.getFileName());

    ExtractionResult result =
        extractionService.triggerExtraction(
            event.getFileName(),
            event.getMimeType(),
            event.getContent(),
            event.getOptions(),
            tenantId);

    if (result.status() == ExtractionResult.Status.FAILED) {
      logger.error("[sap-document-ai] Extraction failed for fileName={}", event.getFileName());
    } else if (result.status() == ExtractionResult.Status.PENDING) {
      logger.warn("[sap-document-ai] Document AI unavailable, left as PENDING");
    }

    context.setCompleted();
  }
}
