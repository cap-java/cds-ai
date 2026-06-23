/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.service.model.ExtractionResult.Status;
import com.sap.cds.service.utils.StatusTransitionValidator;
import com.sap.cds.services.ServiceDelegator;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractionServiceImpl extends ServiceDelegator implements ExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(ExtractionServiceImpl.class);

  private PersistenceService persistenceService;
  private DocumentAiProcessingService documentAiProcessingService;

  public ExtractionServiceImpl() {
    super(NAME);
  }

  public void init(
      PersistenceService persistenceService,
      DocumentAiProcessingService documentAiProcessingService) {
    this.persistenceService = persistenceService;
    this.documentAiProcessingService = documentAiProcessingService;
  }

  @Override
  public ExtractionResult triggerExtraction(
      String sourceDocumentId,
      String fileName,
      String mimeType,
      InputStream content,
      String tenantId) {
    logger.info(
        "[sap-document-ai] Direct extraction triggered for sourceDocumentId={}, tenantId={}",
        sourceDocumentId,
        tenantId);
    // create pending job
    String jobId = createExtractionJob(sourceDocumentId, tenantId);

    // check for availability of the service.
    if (!documentAiProcessingService.isAvailable()) {
      logger.warn(
          "[sap-document-ai] Document AI unavailable, job {} left as PENDING for retry", jobId);
      return new ExtractionResult(jobId, Status.PENDING, null);
    }

    DocumentInput documentInput = new DocumentInput(fileName, mimeType, content);
    ExtractionResult extractionResult =
        performExtraction(jobId, sourceDocumentId, documentInput, tenantId);
    return extractionResult;
  }

  private ExtractionResult performExtraction(
      String jobId, String sourceId, DocumentInput documentInput, String tenantId) {
    try {
      String documentAiJobId = documentAiProcessingService.processDocument(jobId, documentInput);
      updateExtractionJob(jobId, SUBMITTED, documentAiJobId);
      // TODO: transition to PROCESSING and COMPLETED via async polling callback, not here
      //      updateExtractionJob(jobId, PROCESSING, null); // or replace w/ documentAiJobId
      //      updateExtractionJob(jobId, COMPLETED, null); // or replace w/ documentAiJobId
      return new ExtractionResult(jobId, Status.SUCCESS, documentAiJobId);
    } catch (IllegalStatusTransitionException e) { // example: COMPLETED -> FAILED
      logger.error("[sap-document-ai] Invalid state transition for jobId={}", jobId, e);
      throw e;
    } catch (Exception e) { // example : PROCESSING -> FAILED
      logger.error(
          "[sap-document-ai] Processing failed for sourceId={}, tenantId={}",
          sourceId,
          tenantId,
          e);
      markJobAsFailed(jobId);
      return new ExtractionResult(jobId, Status.FAILED, null);
    }
  }

  private void markJobAsFailed(String jobId) {
    try {
      updateExtractionJob(jobId, FAILED, null);
    } catch (Exception e) {
      logger.error("[sap-document-ai] Failed to update status to FAILED for jobId={}", jobId, e);
    }
  }

  private String createExtractionJob(String sourceDocumentId, String tenantId) {
    ExtractionJob job = ExtractionJob.create();
    job.setSourceDocumentId(sourceDocumentId);
    job.setTenantId(tenantId);
    job.setStatus(PENDING.name());

    Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
    String jobId = result.single(ExtractionJob.class).getId();
    logger.info(
        "[sap-document-ai] ExtractionJob created with status=PENDING,  sourceId={}, jobId={}",
        sourceDocumentId,
        jobId);
    return jobId;
  }

  private void updateExtractionJob(String jobId, ExtractionStatus status, String documentAiJobId) {
    Result current = persistenceService.run(Select.from(ExtractionJob_.class).byId(jobId));
    ExtractionStatus currentStatus = fromString(current.single(ExtractionJob.class).getStatus());

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
    extractionJob.setStatus(status.name());
    if (documentAiJobId != null) {
      extractionJob.setDocumentAiJobId(documentAiJobId);
    }

    Result updateResult =
        persistenceService.run(
            Update.entity(ExtractionJob_.class)
                .byId(jobId)
                .where(j -> j.get(ExtractionJob.STATUS).eq(currentStatus.name()))
                .entry(extractionJob));

    if (updateResult.rowCount() == 0) {
      logger.error(
          "[sap-document-ai] Status update skipped for jobId={} — concurrent modification detected (expected status={}, update affected 0 rows)",
          jobId,
          currentStatus);
      throw new IllegalStatusTransitionException(
          "Concurrent modification detected for jobId="
              + jobId
              + ", expected status="
              + currentStatus);
    }

    logger.info(
        "[sap-document-ai] ExtractionJob jobId={} status updated from {} to {}{}",
        jobId,
        currentStatus,
        status,
        documentAiJobId != null ? " with documentAiJobId=" + documentAiJobId : "");
  }
}
