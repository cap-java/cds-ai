/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service;

import static com.sap.cds.feature.documentai.service.ExtractionStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.outbox.Schedule;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceImplTest {

  static final String TENANT_1 = "tenant-1";
  static final String DIE_JOB_ID = "die-job-123";
  static final String TEST_PDF = "test.pdf";
  static final String CONTENT_TYPE = "application/pdf";
  static final String TEST_CONTENT = "test-content";

  @Mock PersistenceService persistenceService;
  @Mock DocumentAiProcessingService documentAiProcessingService;
  @Mock OutboxService outboxService;
  @Mock Result insertResult;

  ExtractionServiceImpl extractionService;

  @BeforeEach
  void setUp() {
    when(documentAiProcessingService.isAvailable()).thenReturn(true);
    extractionService = new ExtractionServiceImpl();
    extractionService.init(
        persistenceService, documentAiProcessingService, outboxService, Duration.ofSeconds(3));
  }

  @Test
  void triggerExtractionCreatesJobAsPendingWhenServiceUnavailable() {
    mockInsertDatabaseCalls();
    when(documentAiProcessingService.isAvailable()).thenReturn(false);

    ExtractionResult result =
        extractionService.triggerExtraction(
            TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.PENDING);
    assertThat(result.internalJobId()).isNotNull();
    verify(persistenceService).run(any(CqnInsert.class));
  }

  @Test
  void triggerExtractionCreatesJobWithCorrectFields() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);

    extractionService.triggerExtraction(TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    var insertCaptor = org.mockito.ArgumentCaptor.forClass(CqnInsert.class);
    verify(persistenceService, atLeastOnce()).run(insertCaptor.capture());
    ExtractionJob inserted =
        com.sap.cds.Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(inserted.getTenantId()).isEqualTo(TENANT_1);
    assertThat(inserted.getStatus()).isEqualTo(PENDING.name());
  }

  @Test
  void triggerExtractionSubmitsDocumentAndUpdatesStatusToSubmitted() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);

    ExtractionResult result =
        extractionService.triggerExtraction(
            TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.SUCCESS);
    assertThat(result.documentAiJobId()).isEqualTo(DIE_JOB_ID);
    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
    verify(outboxService).submit(any(), any(), any(Schedule.class));
  }

  @Test
  void triggerExtractionDoesNotThrowWhenOutboxIsNull() {
    extractionService.init(
        persistenceService, documentAiProcessingService, null, Duration.ofSeconds(3));
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);

    ExtractionResult result =
        extractionService.triggerExtraction(
            TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.SUCCESS);
  }

  @Test
  void triggerExtractionMarksJobFailedOnProcessingError() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    doThrow(new RuntimeException("simulated failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());

    ExtractionResult result =
        extractionService.triggerExtraction(
            TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.FAILED);
    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void triggerExtractionReturnSuccessOnConcurrentUpdate() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);
    Result zeroRowResult = mock(Result.class);
    when(zeroRowResult.rowCount()).thenReturn(0L);
    when(persistenceService.run(any(CqnUpdate.class))).thenReturn(zeroRowResult);

    ExtractionResult result =
        extractionService.triggerExtraction(
            TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.SUCCESS);
  }

  @Test
  void triggerExtractionThrowsOnInvalidStatusTransition() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    doThrow(new IllegalStatusTransitionException("invalid transition"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());

    assertThrows(
        IllegalStatusTransitionException.class,
        () ->
            extractionService.triggerExtraction(
                TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1));

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void updateStatusWithSameStateSkipsUpdate() {
    mockInsertDatabaseCalls();
    Result statusResult = resultWithJobStatus(SUBMITTED);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);

    extractionService.triggerExtraction(TEST_PDF, CONTENT_TYPE, contentStream(), null, TENANT_1);

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  private InputStream contentStream() {
    return new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
  }

  private void mockInsertDatabaseCalls() {
    ExtractionJob createdJob = ExtractionJob.create();
    createdJob.setId("test-job-id");
    lenient().when(insertResult.single(ExtractionJob.class)).thenReturn(createdJob);
    lenient().when(persistenceService.run(any(CqnInsert.class))).thenReturn(insertResult);
  }

  private void mockAllDatabaseCalls() {
    mockInsertDatabaseCalls();
    Result updateResult = mock(Result.class);
    lenient().when(updateResult.rowCount()).thenReturn(1L);
    lenient().when(persistenceService.run(any(CqnUpdate.class))).thenReturn(updateResult);
  }

  private Result resultWithJobStatus(ExtractionStatus status) {
    ExtractionJob job = ExtractionJob.create();
    job.setStatus(status.name());
    Result result = mock(Result.class);
    lenient().when(result.single(ExtractionJob.class)).thenReturn(job);
    lenient().when(result.first(ExtractionJob.class)).thenReturn(Optional.of(job));
    return result;
  }
}
