/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.handlers;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.outbox.Schedule;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbox-driven handler that polls the DIE service for the status of all active extraction jobs.
 *
 * <p>Registered against the persistent unordered outbox service. On each invocation it:
 *
 * <ol>
 *   <li>Queries all jobs in {@code SUBMITTED} or {@code RUNNING} status.
 *   <li>For each job, calls {@link DocumentAiClient#getJobResult} and maps the DIE status to an
 *       {@link ExtractionStatus} transition.
 *   <li>Persists the new status via {@link ExtractionService#updateExtractionResult}.
 *   <li>When a job reaches {@code DONE}, emits a {@code DocumentExtractionResult} event on the
 *       {@code DocumentAiService} so consumer handlers can react.
 *   <li>If jobs remain active, re-schedules itself via the outbox after {@link
 *       #DEFAULT_POLL_INTERVAL_SECONDS} seconds.
 * </ol>
 *
 * <p>This self-rescheduling pattern means polling stops automatically once all jobs reach a
 * terminal status ({@code DONE} or {@code FAILED}), avoiding unnecessary cycles.
 *
 * <p>The poll interval defaults to 3 seconds and can be overridden via the application property
 * {@code cds.document-ai.polling.interval-seconds}.
 */
@ServiceName(value = ExtractionPollingHandler.OUTBOX_NAME, type = OutboxService.class)
public class ExtractionPollingHandler implements EventHandler {

  static final String OUTBOX_NAME = OutboxService.PERSISTENT_UNORDERED_NAME;
  public static final String POLL_EVENT = "document-ai/poll-extraction-jobs";
  public static final String POLL_TASK_NAME = "document-ai-poll-extraction-jobs";
  public static final int DEFAULT_POLL_INTERVAL_SECONDS = 3;

  private static final Logger logger = LoggerFactory.getLogger(ExtractionPollingHandler.class);

  private final PersistenceService persistenceService;
  private final ExtractionService extractionService;
  private final DocumentAiClient documentAiClient;
  private final OutboxService outboxService;
  private final CdsRuntime runtime;
  private final Duration pollDelay;

  /**
   * @param persistenceService the CDS persistence service for querying active jobs
   * @param extractionService the extraction service for updating job status
   * @param documentAiClient the DIE HTTP client
   * @param outboxService the persistent outbox used to reschedule polling cycles
   * @param runtime the CDS runtime for service catalog lookups
   * @param pollDelay the delay between successive poll cycles
   */
  public ExtractionPollingHandler(
      PersistenceService persistenceService,
      ExtractionService extractionService,
      DocumentAiClient documentAiClient,
      OutboxService outboxService,
      CdsRuntime runtime,
      Duration pollDelay) {
    this.persistenceService = persistenceService;
    this.extractionService = extractionService;
    this.documentAiClient = documentAiClient;
    this.outboxService = outboxService;
    this.runtime = runtime;
    this.pollDelay = pollDelay;
  }

  /**
   * Outbox event handler that performs a single poll cycle across all active jobs.
   *
   * @param context the outbox message context; {@link OutboxMessageEventContext#setCompleted()} is
   *     called to acknowledge the message regardless of per-job errors
   */
  @On(event = POLL_EVENT)
  public void pollExtractionJobs(OutboxMessageEventContext context) {
    List<ExtractionJob> activeJobs =
        persistenceService
            .run(
                Select.from(ExtractionJob_.class)
                    .where(
                        j ->
                            j.status()
                                .eq(ExtractionStatus.SUBMITTED.name())
                                .or(j.status().eq(ExtractionStatus.RUNNING.name()))))
            .listOf(ExtractionJob.class);

    logger.debug("[sap-document-ai] Polling {} active extraction job(s)", activeJobs.size());

    if (activeJobs.isEmpty()) {
      logger.debug("[sap-document-ai] No active jobs, polling stopped");
      context.setCompleted();
      return;
    }

    for (ExtractionJob job : activeJobs) {
      processJob(job);
    }

    if (outboxService != null) {
      outboxService.submit(
          POLL_EVENT,
          OutboxMessage.create(),
          Schedule.create().taskName(POLL_TASK_NAME).after(pollDelay));
    } else {
      logger.warn("[sap-document-ai] Outbox not available, next poll cycle will not be scheduled");
    }

    context.setCompleted();
  }

  private void processJob(ExtractionJob job) {
    String jobId = job.getId();
    String dieJobId = job.getDocumentAiJobId();

    if (dieJobId == null) {
      logger.warn("[sap-document-ai] jobId={} has no DIE job ID, skipping poll", jobId);
      return;
    }

    try {
      ExtractionData result = documentAiClient.getJobResult(dieJobId);
      ExtractionStatus newStatus = mapDieStatus(result.dieStatus());

      if (newStatus == null) {
        logger.debug(
            "[sap-document-ai] jobId={} DIE status={} — no transition yet",
            jobId,
            result.dieStatus());
        return;
      }

      String extractionResult = newStatus == ExtractionStatus.DONE ? result.rawResult() : null;

      extractionService.updateExtractionResult(jobId, newStatus, dieJobId, extractionResult);

      if (newStatus == ExtractionStatus.DONE) {
        logger.info(
            "[sap-document-ai] Extraction complete for jobId={}, dieJobId={}", jobId, dieJobId);
        emitExtractionCompleted(jobId, extractionResult);
      }

    } catch (Exception e) {
      logger.error(
          "[sap-document-ai] Failed to poll/update jobId={}, dieJobId={}", jobId, dieJobId, e);
    }
  }

  private void emitExtractionCompleted(String jobId, String extractionResult) {
    ApplicationService documentAiService =
        runtime
            .getServiceCatalog()
            .getService(ApplicationService.class, DocumentAiService_.CDS_NAME);
    if (documentAiService == null) {
      logger.warn(
          "[sap-document-ai] DocumentAiService not found in catalog, cannot emit result for jobId={}",
          jobId);
      return;
    }
    DocumentExtractionResult eventData = DocumentExtractionResult.create();
    eventData.setJobId(jobId);
    eventData.setExtractionResult(extractionResult);
    DocumentExtractionResultContext eventContext = DocumentExtractionResultContext.create();
    eventContext.setData(eventData);
    documentAiService.emit(eventContext);
    logger.info("[sap-document-ai] Emitted DocumentExtractionResult for jobId={}", jobId);
  }

  private ExtractionStatus mapDieStatus(String dieStatus) {
    return switch (dieStatus.toUpperCase()) {
      case "RUNNING" -> ExtractionStatus.RUNNING;
      case "DONE" -> ExtractionStatus.DONE;
      case "FAILED" -> ExtractionStatus.FAILED;
      default -> null; // PENDING or unknown — no transition
    };
  }
}
