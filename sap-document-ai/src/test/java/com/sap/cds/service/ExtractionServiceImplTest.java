/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Struct;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceImplTest {

  static final String TENANT_1 = "tenant-1";
  static final String ATT_123 = "att-123";
  static final String CNT_123 = "cnt-123";
  static final String DIE_JOB_ID = "die-job-123";
  public static final String TEST_PDF = "test.pdf";
  public static final String CONTENT_TYPE = "application/pdf";
  public static final String TEST_CONTENT = "test-content";

  @Mock PersistenceService persistenceService;
  @Mock DocumentAiProcessingService documentAiProcessingService;
  @Mock Result insertResult;

  DocumentInput documentInput;
  ExtractionServiceImpl extractionService;

  @BeforeEach
  void setUp() {
    when(documentAiProcessingService.isAvailable()).thenReturn(true);
    documentInput =
        new DocumentInput(
            TEST_PDF,
            CNT_123,
            CONTENT_TYPE,
            new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
    extractionService = new ExtractionServiceImpl(persistenceService, documentAiProcessingService);
  }

  @Test
  void startExtractionDoesNothingWhenServiceUnavailable() {
    when(documentAiProcessingService.isAvailable()).thenReturn(false);
    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);
    verify(persistenceService, never()).run(any(CqnInsert.class));
  }

  @Test
  void startExtractionCreatesOneJobWithCorrectFields() {
    mockAllDatabaseCalls();
    mockSuccessfulProcessing();
    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);

    ArgumentCaptor<CqnInsert> insertCaptor = forClass(CqnInsert.class);
    verify(persistenceService, times(1)).run(insertCaptor.capture());
    ExtractionJob inserted =
        Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(inserted.getAttachmentId()).isEqualTo(ATT_123);
    assertThat(inserted.getTenantId()).isEqualTo(TENANT_1);
    assertThat(inserted.getStatus()).isEqualTo(PENDING.name());
  }

  @Test
  void startExtractionStoresDocumentAiJobIdAndUpdatesStatusToSubmitted() {
    mockAllDatabaseCalls();
    mockSuccessfulProcessing();
    mockStatusResult(PENDING);
    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);

    ArgumentCaptor<CqnUpdate> captor = forClass(CqnUpdate.class);
    verify(persistenceService, times(2)).run(captor.capture());
    List<CqnUpdate> updates = captor.getAllValues();

    ExtractionJob statusUpdate =
        Struct.access(updates.get(0).entries().get(0)).as(ExtractionJob.class);
    assertThat(statusUpdate.getStatus()).isEqualTo(SUBMITTED.name());

    ExtractionJob jobUpdate =
        Struct.access(updates.get(1).entries().get(0)).as(ExtractionJob.class);
    assertThat(jobUpdate.getDocumentAiJobId()).isEqualTo(DIE_JOB_ID);
  }

  @Test
  void startExtractionFailsWhenJobNotFound() {
    mockInsertDatabaseCalls();
    mockSuccessfulProcessing();
    Result emptyResult = mock(Result.class);
    when(emptyResult.single(ExtractionJob.class)).thenThrow(new RuntimeException("not found"));
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(emptyResult);

    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void updateStatusWithSameStateDoesNotRunJobAgain() {
    // SELECT returns SUBMITTED — updateStatus(SUBMITTED) is a same-state no-op, no status UPDATE.
    // updateDocumentAiJobId still fires, so exactly 1 UPDATE total (for the job ID, not status).
    mockInsertDatabaseCalls();
    mockSuccessfulProcessing();
    mockStatusResult(SUBMITTED);

    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);

    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void invalidTransitionIsLoggedAndNoStatusUpdateOccurs() {
    mockInsertDatabaseCalls();
    mockSuccessfulProcessing();
    mockStatusResult(COMPLETED);
    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);
    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void markJobAsFailedSucceedsWhenTransitionFromPendingToFailedIsValid() {
    mockAllDatabaseCalls();
    mockStatusResult(PENDING);
    doThrow(new RuntimeException("simulated failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());
    extractionService.startExtraction(ATT_123, documentInput, TENANT_1);
    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  private void mockStatusResult(ExtractionStatus status) {
    Result result = resultWithJobStatus(status);
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(result);
  }

  private void mockSuccessfulProcessing() {
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);
  }

  private void mockInsertDatabaseCalls() {
    ExtractionJob createdJob = ExtractionJob.create();
    createdJob.setId("test-job-id");
    when(insertResult.single(ExtractionJob.class)).thenReturn(createdJob);
    when(persistenceService.run(any(CqnInsert.class))).thenReturn(insertResult);
  }

  private void mockAllDatabaseCalls() {
    mockInsertDatabaseCalls();
    when(persistenceService.run(any(CqnUpdate.class))).thenReturn(mock(Result.class));
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
