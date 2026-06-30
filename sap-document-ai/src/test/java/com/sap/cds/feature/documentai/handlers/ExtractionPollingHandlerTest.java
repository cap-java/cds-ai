/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.outbox.Schedule;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionPollingHandlerTest {

  private static final String JOB_ID = "job-123";
  private static final String DIE_JOB_ID = "die-job-456";
  private static final String RAW_RESULT = "{\"extraction\":{}}";

  @Mock PersistenceService persistenceService;
  @Mock ExtractionService extractionService;
  @Mock DocumentAiClient documentAiClient;
  @Mock OutboxService outboxService;
  @Mock CdsRuntime runtime;
  @Mock ServiceCatalog serviceCatalog;
  @Mock ApplicationService documentAiService;
  @Mock OutboxMessageEventContext context;
  @Mock Result queryResult;

  ExtractionPollingHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ExtractionPollingHandler(
            persistenceService,
            extractionService,
            documentAiClient,
            outboxService,
            runtime,
            Duration.ofSeconds(ExtractionPollingHandler.DEFAULT_POLL_INTERVAL_SECONDS));
  }

  private void mockEmit() {
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(serviceCatalog.getService(ApplicationService.class, DocumentAiService_.CDS_NAME))
        .thenReturn(documentAiService);
  }

  @Test
  void pollStopsAndSetsCompletedWhenNoActiveJobs() {
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(queryResult);
    when(queryResult.listOf(ExtractionJob.class)).thenReturn(List.of());

    handler.pollExtractionJobs(context);

    verify(outboxService, never()).submit(any(), any(), any(Schedule.class));
    verify(context).setCompleted();
  }

  @Test
  void pollReschedulesWhenActiveJobsExist() {
    mockActiveJob(DIE_JOB_ID);
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "RUNNING", null));

    handler.pollExtractionJobs(context);

    verify(outboxService)
        .submit(eq(ExtractionPollingHandler.POLL_EVENT), any(), any(Schedule.class));
    verify(context).setCompleted();
  }

  @Test
  void pollSkipsJobWithNoDieJobId() {
    mockActiveJob(null);

    handler.pollExtractionJobs(context);

    verify(documentAiClient, never()).getJobResult(any());
  }

  @Test
  void pollDoesNotUpdateStatusWhenDieReturnsPending() {
    mockActiveJob(DIE_JOB_ID);
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "PENDING", null));

    handler.pollExtractionJobs(context);

    verify(extractionService, never()).updateExtractionResult(any(), any(), any(), any());
  }

  @Test
  void pollUpdatesStatusToRunningWithoutEmittingEvent() {
    mockActiveJob(DIE_JOB_ID);
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "RUNNING", null));

    handler.pollExtractionJobs(context);

    verify(extractionService)
        .updateExtractionResult(JOB_ID, ExtractionStatus.RUNNING, DIE_JOB_ID, null);
    verify(documentAiService, never()).emit(any());
  }

  @Test
  void pollUpdatesStatusToFailedWithoutEmittingEvent() {
    mockActiveJob(DIE_JOB_ID);
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "FAILED", null));

    handler.pollExtractionJobs(context);

    verify(extractionService)
        .updateExtractionResult(JOB_ID, ExtractionStatus.FAILED, DIE_JOB_ID, null);
    verify(documentAiService, never()).emit(any());
  }

  @Test
  void pollUpdatesStatusToDoneAndEmitsEvent() {
    mockEmit();
    mockActiveJob(DIE_JOB_ID);
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "DONE", RAW_RESULT));

    handler.pollExtractionJobs(context);

    verify(extractionService)
        .updateExtractionResult(JOB_ID, ExtractionStatus.DONE, DIE_JOB_ID, RAW_RESULT);
    verify(documentAiService).emit(any());
  }

  @Test
  void pollContinuesToNextJobWhenOneThrows() {
    ExtractionJob failingJob = ExtractionJob.create();
    failingJob.setId("job-fail");
    failingJob.setDocumentAiJobId("die-fail");

    ExtractionJob goodJob = ExtractionJob.create();
    goodJob.setId(JOB_ID);
    goodJob.setDocumentAiJobId(DIE_JOB_ID);

    when(persistenceService.run(any(CqnSelect.class))).thenReturn(queryResult);
    when(queryResult.listOf(ExtractionJob.class)).thenReturn(List.of(failingJob, goodJob));

    when(documentAiClient.getJobResult("die-fail")).thenThrow(new RuntimeException("timeout"));
    when(documentAiClient.getJobResult(DIE_JOB_ID))
        .thenReturn(new ExtractionData(DIE_JOB_ID, "RUNNING", null));

    handler.pollExtractionJobs(context);

    verify(extractionService)
        .updateExtractionResult(JOB_ID, ExtractionStatus.RUNNING, DIE_JOB_ID, null);
    verify(context).setCompleted();
  }

  private void mockActiveJob(String dieJobId) {
    ExtractionJob job = ExtractionJob.create();
    job.setId(JOB_ID);
    job.setDocumentAiJobId(dieJobId);
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(queryResult);
    when(queryResult.listOf(ExtractionJob.class)).thenReturn(List.of(job));
  }
}
