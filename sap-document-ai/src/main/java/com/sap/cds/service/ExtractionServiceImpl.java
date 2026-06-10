/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractionServiceImpl implements ExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(ExtractionServiceImpl.class);

  private final PersistenceService persistenceService;
  private final DocumentAiProcessingService documentAiProcessingService;

  public ExtractionServiceImpl(
      PersistenceService persistenceService,
      DocumentAiProcessingService documentAiProcessingService) {
    this.persistenceService = persistenceService;
    this.documentAiProcessingService = documentAiProcessingService;
  }

  @Override
  public void startExtraction(
      String attachmentId, String contentId, String tenantId, InputStream content) {
    logger.info(
        "[sap-document-ai] Orchestrator triggered for attachmentId={}, tenantId={}",
        attachmentId,
        tenantId);

    if (!documentAiProcessingService.isAvailable()) {
      logger.warn("[sap-document-ai] Document AI client is not available, skipping submission");
      return;
    }

    String jobId = createExtractionJob(attachmentId, tenantId);

    try {
      updateStatus(jobId, ExtractionStatus.PROCESSING);
      documentAiProcessingService.processDocument(jobId, content);
      updateStatus(jobId, ExtractionStatus.COMPLETED);
    } catch (IllegalStatusTransitionException e) { // example: COMPLETED -> FAILED
      logger.error("[sap-document-ai] Invalid state transition for jobId={}", jobId, e);
    } catch (Exception e) { // example : PROCESSING -> FAILED
      logger.error(
          "[sap-document-ai] Processing failed for attachmentId={}, tenantId={}",
          attachmentId,
          tenantId,
          e);

      markJobAsFailed(jobId);
    }
  }

  private void markJobAsFailed(String jobId) {
    try {
      updateStatus(jobId, ExtractionStatus.FAILED);
    } catch (Exception e) {
      logger.error("[sap-document-ai] Failed to update status to FAILED for jobId={}", jobId, e);
    }
  }

  private String createExtractionJob(String attachmentId, String tenantId) {
    ExtractionJob job = ExtractionJob.create();
    job.setAttachmentId(attachmentId);
    job.setTenantId(tenantId);

    Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
    String jobId = result.single(ExtractionJob.class).getId();
    logger.info(
        "[sap-document-ai] ExtractionJob created with status=Pending for attachmentId={} & jobId={}",
        attachmentId,
        jobId);
    return jobId;
  }

  private void updateStatus(String jobId, String status) {
    Result current = persistenceService.run(Select.from(ExtractionJob_.class).byId(jobId));
    String currentStatus = current.single(ExtractionJob.class).getStatus();

    if (currentStatus.equals(status)) {
      logger.debug(
          "[sap-document-ai] ExtractionJob jobId={} already in status {}, skipping update",
          jobId,
          status);
      return;
    }

    if (!StatusTransitionValidator.isValid(currentStatus, status)) {
      throw new IllegalStatusTransitionException(
          "Invalid status transition from " + currentStatus + " to " + status);
    }

    ExtractionJob extractionJob = ExtractionJob.create();
    extractionJob.setStatus(status);
    persistenceService.run(Update.entity(ExtractionJob_.class).byId(jobId).entry(extractionJob));
    logger.info("[sap-document-ai] ExtractionJob jobId={} status updated to {}", jobId, status);
  }
}
