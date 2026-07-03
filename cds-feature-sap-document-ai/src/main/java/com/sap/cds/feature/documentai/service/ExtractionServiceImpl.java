/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service;

import static com.sap.cds.feature.documentai.handlers.ExtractionPollingHandler.*;
import static com.sap.cds.feature.documentai.service.ExtractionStatus.*;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.service.exceptions.ConcurrentJobUpdateException;
import com.sap.cds.feature.documentai.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.feature.documentai.service.model.DocumentInput;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.feature.documentai.service.model.ExtractionResult.Status;
import com.sap.cds.feature.documentai.service.utils.StatusTransitionValidator;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.ServiceDelegator;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.outbox.Schedule;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.InputStream;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ExtractionService}.
 *
 * <p>Orchestrates the full extraction lifecycle:
 *
 * <ol>
 *   <li>Persists a new {@code ExtractionJob} in {@code PENDING} status.
 *   <li>Delegates document submission to {@link DocumentAiProcessingService}.
 *   <li>On success, advances the job to {@code SUBMITTED} and schedules a polling cycle via the
 *       persistent outbox.
 *   <li>On failure, marks the job as {@code FAILED} and returns the appropriate result.
 * </ol>
 *
 * <p>Status updates use an optimistic-lock pattern: the {@code UPDATE} query includes a {@code
 * WHERE status = currentStatus} predicate. Zero rows affected raises {@link
 * com.sap.cds.feature.documentai.service.exceptions.ConcurrentJobUpdateException}.
 */
public class ExtractionServiceImpl extends ServiceDelegator implements ExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(ExtractionServiceImpl.class);

  private PersistenceService persistenceService;
  private DocumentAiProcessingService documentAiProcessingService;
  private OutboxService outboxService;
  private Duration pollDelay;

  public ExtractionServiceImpl() {
    super(NAME);
  }

  /**
   * Injects runtime dependencies after Spring/CDS wiring is complete.
   *
   * <p>Called from {@link
   * com.sap.cds.feature.documentai.configuration.DocumentAiServiceConfiguration} once all dependent
   * services are resolved from the service catalog.
   *
   * @param persistenceService the CDS persistence service for job CRUD operations
   * @param documentAiProcessingService the processing service wrapping the DIE HTTP client
   * @param outboxService the persistent outbox used to schedule polling; may be {@code null} if the
   *     outbox is not configured
   * @param pollDelay the delay before the first poll cycle, read from {@code
   *     cds.document-ai.polling.interval-seconds}
   */
  public void init(
      PersistenceService persistenceService,
      DocumentAiProcessingService documentAiProcessingService,
      OutboxService outboxService,
      Duration pollDelay) {
    this.persistenceService = persistenceService;
    this.documentAiProcessingService = documentAiProcessingService;
    this.outboxService = outboxService;
    this.pollDelay = pollDelay;
  }

  @Override
  public ExtractionResult triggerExtraction(
      String fileName, String mimeType, InputStream content, String options, String tenantId)
      throws IllegalStatusTransitionException {
    logger.info(
        "[sap-document-ai] Direct extraction triggered for fileName={}, tenantId={}",
        fileName,
        tenantId);

    String jobId = createExtractionJob(tenantId);

    if (!documentAiProcessingService.isAvailable()) {
      logger.warn(
          "[sap-document-ai] Document AI unavailable, job {} left as PENDING for retry", jobId);
      return new ExtractionResult(jobId, Status.PENDING, null);
    }

    DocumentInput documentInput = new DocumentInput(fileName, mimeType, content, options);
    return performExtraction(jobId, fileName, documentInput, tenantId);
  }

  @Override
  public void updateExtractionResult(
      String jobId, ExtractionStatus status, String dieJobId, String extractionResult)
      throws IllegalStatusTransitionException {
    updateExtractionJob(jobId, status, dieJobId, extractionResult);
  }

  private ExtractionResult performExtraction(
      String jobId, String fileName, DocumentInput documentInput, String tenantId) {
    try {
      String documentAiJobId = documentAiProcessingService.processDocument(jobId, documentInput);
      updateExtractionJob(jobId, SUBMITTED, documentAiJobId, null);
      schedulePolling();
      return new ExtractionResult(jobId, Status.SUCCESS, documentAiJobId);
    } catch (ConcurrentJobUpdateException e) {
      logger.warn(
          "[sap-document-ai] Concurrent update on jobId={}, skipping status write — job already advanced",
          jobId);
      return new ExtractionResult(jobId, Status.SUCCESS, null);
    } catch (IllegalStatusTransitionException e) {
      logger.error("[sap-document-ai] Invalid state transition for jobId={}", jobId, e);
      throw e;
    } catch (Exception e) {
      logger.error(
          "[sap-document-ai] Processing failed for fileName={}, tenantId={}",
          fileName,
          tenantId,
          e);
      markJobAsFailed(jobId);
      return new ExtractionResult(jobId, Status.FAILED, null);
    }
  }

  private void schedulePolling() {
    if (outboxService == null) {
      logger.warn("[sap-document-ai] Outbox not available, polling will not be scheduled");
      return;
    }
    outboxService.submit(
        POLL_EVENT,
        OutboxMessage.create(),
        Schedule.create().taskName(POLL_TASK_NAME).after(pollDelay));
    logger.debug("[sap-document-ai] Poll schedule submitted");
  }

  private void markJobAsFailed(String jobId) {
    try {
      updateExtractionJob(jobId, FAILED, null, null);
    } catch (Exception e) {
      logger.error("[sap-document-ai] Failed to update status to FAILED for jobId={}", jobId, e);
    }
  }

  private String createExtractionJob(String tenantId) {
    ExtractionJob job = ExtractionJob.create();
    job.setTenantId(tenantId);
    job.setStatus(PENDING.name());

    Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
    String jobId = result.single(ExtractionJob.class).getId();
    logger.info("[sap-document-ai] ExtractionJob created with status=PENDING, jobId={}", jobId);
    return jobId;
  }

  private void updateExtractionJob(
      String jobId, ExtractionStatus status, String documentAiJobId, String extractionResult) {
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
    if (extractionResult != null) {
      extractionJob.setExtractionResult(extractionResult);
    }

    Result updateResult =
        persistenceService.run(
            Update.entity(ExtractionJob_.class)
                .where(
                    j ->
                        j.get(ExtractionJob.ID)
                            .eq(jobId)
                            .and(j.get(ExtractionJob.STATUS).eq(currentStatus.name())))
                .entry(extractionJob));

    if (updateResult.rowCount() == 0) {
      String message =
          "Concurrent update detected for jobId=" + jobId + ", expected status=" + currentStatus;
      logger.warn("[sap-document-ai] {}", message);
      throw new ConcurrentJobUpdateException(message);
    }

    logger.info(
        "[sap-document-ai] ExtractionJob jobId={} status updated from {} to {}{}",
        jobId,
        currentStatus,
        status,
        documentAiJobId != null ? " with documentAiJobId=" + documentAiJobId : "");
  }
}
