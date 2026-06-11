/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.handlers;

import static com.sap.cds.service.ExtractionService.EVENT_START_EXTRACTION;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.*;
import com.sap.cds.ql.Select;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.exceptions.SourceDocumentException;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(DocumentAiService_.CDS_NAME)
public class DocumentSubmissionHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DocumentSubmissionHandler.class);

  private final ExtractionService extractionService;
  private final DocumentAiService documentAiService;

  public DocumentSubmissionHandler(
      ExtractionService extractionService, DocumentAiService documentAiService) {
    this.extractionService = extractionService;
    this.documentAiService = documentAiService;
  }

  @On(event = EVENT_START_EXTRACTION)
  public void onStartExtraction(StartExtractionContext context) {
    context.setCompleted();

    String sourceDocumentId = context.getSourceDocumentId();
    String tenantId = context.getUserInfo().getTenant();

    logger.info(
        "[sap-document-ai] startExtraction action called for sourceDocumentId={}",
        sourceDocumentId);

    SourceDocument document =
        documentAiService
            .run(
                Select.from(SourceDocument_.class)
                    .columns(
                        SourceDocument.ID,
                        SourceDocument.FILE_NAME,
                        SourceDocument.MIME_TYPE,
                        SourceDocument.CONTENT)
                    .byId(sourceDocumentId))
            .first(SourceDocument.class)
            .orElse(null);

    if (document == null) {
      throw new SourceDocumentException.NotFound(sourceDocumentId);
    }

    InputStream content = document.getContent();
    if (content == null) {
      throw new SourceDocumentException.ContentMissing(sourceDocumentId);
    }

    ExtractionResult extraction =
        extractionService.triggerExtraction(
            sourceDocumentId, document.getFileName(), document.getMimeType(), content, tenantId);

    ExtractionJob result = ExtractionJob.create();
    result.setId(extraction.internalJobId());
    result.setSourceDocumentId(sourceDocumentId);
    context.setResult(result);
  }

  @After(event = CqnService.EVENT_UPDATE, entity = SourceDocument_.CDS_NAME)
  public void afterContentUpload(CdsUpdateEventContext context) {

    // Trigger extraction only when document content was updated
    boolean contentUpdated =
        context.getCqn().entries().stream()
            .anyMatch(entry -> entry.containsKey(SourceDocument.CONTENT));

    if (!contentUpdated) {
      logger.debug(
          "[sap-document-ai] SourceDocument UPDATE contained no content changes, skipping extraction");
      return;
    }

    List<String> sourceDocumentIds =
        context.getResult().listOf(SourceDocument.class).stream()
            .map(SourceDocument::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (sourceDocumentIds.isEmpty()) {
      logger.debug("[sap-document-ai] No SourceDocument IDs in result, skipping extraction");
      return;
    }

    String tenantId = context.getUserInfo().getTenant();
    List<String> failedIds = new ArrayList<>();

    List<SourceDocument> documents =
        documentAiService
            .run(
                Select.from(SourceDocument_.class)
                    .columns(
                        SourceDocument.ID,
                        SourceDocument.FILE_NAME,
                        SourceDocument.MIME_TYPE,
                        SourceDocument.CONTENT)
                    .where(d -> d.get(SourceDocument.ID).in(sourceDocumentIds)))
            .listOf(SourceDocument.class);

    for (SourceDocument document : documents) {
      InputStream content = document.getContent();
      String sourceDocumentId = document.getId();

      if (content == null) {
        logger.warn(
            "[sap-document-ai] Content is null for sourceDocumentId={}, skipping extraction",
            sourceDocumentId);
        failedIds.add(sourceDocumentId);
        continue;
      }

      try {
        logger.info(
            "[sap-document-ai] Content uploaded for sourceDocumentId={}, triggering extraction",
            sourceDocumentId);
        ExtractionResult extraction =
            extractionService.triggerExtraction(
                sourceDocumentId,
                document.getFileName(),
                document.getMimeType(),
                content,
                tenantId);
        switch (extraction.status()) {
          case FAILED -> {
            logger.error(
                "[sap-document-ai] Extraction failed for sourceDocumentId={}", sourceDocumentId);
            failedIds.add(sourceDocumentId);
          }
          case PENDING ->
              logger.warn(
                  "[sap-document-ai] Document AI unavailable, sourceDocumentId={} left as PENDING",
                  sourceDocumentId);
          default -> {}
        }
      } catch (Exception e) {
        logger.error(
            "[sap-document-ai] Extraction failed for sourceDocumentId={}", sourceDocumentId, e);
        failedIds.add(sourceDocumentId);
      }
    }

    if (!failedIds.isEmpty()) {
      logger.error(
          "[sap-document-ai] Extraction failed for {} of {} document(s): {}",
          failedIds.size(),
          documents.size(),
          failedIds);
    }
  }
}
