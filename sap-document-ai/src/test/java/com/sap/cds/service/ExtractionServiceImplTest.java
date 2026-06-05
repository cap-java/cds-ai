/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Struct;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.assertj.core.api.Assertions;
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

  @Mock PersistenceService persistenceService;
  @Mock DocumentAiProcessingService documentAiProcessingService;
  @Mock Result insertResult;

  ExtractionServiceImpl extractionService;
  InputStream mockContent;

  @BeforeEach
  void setUp() {
    ExtractionJob createdJob = ExtractionJob.create();
    createdJob.setId("test-job-id");
    lenient().when(insertResult.single(ExtractionJob.class)).thenReturn(createdJob);
    lenient().when(persistenceService.run(any(CqnInsert.class))).thenReturn(insertResult);
    lenient().when(persistenceService.run(any(CqnUpdate.class))).thenReturn(mock(Result.class));
    lenient().when(documentAiProcessingService.isAvailable()).thenReturn(true);
    Result pendingResult = jobWithStatus(ExtractionStatus.PENDING);
    Result processingResult = jobWithStatus(ExtractionStatus.PROCESSING);
    lenient()
        .when(persistenceService.run(any(CqnSelect.class)))
        .thenReturn(pendingResult, processingResult);
    mockContent = new ByteArrayInputStream("test-content".getBytes());
    extractionService = new ExtractionServiceImpl(persistenceService, documentAiProcessingService);
  }

  @Test
  void startExtractionDoesNothingWhenServiceUnavailable() {
    when(documentAiProcessingService.isAvailable()).thenReturn(false);

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    verify(persistenceService, never()).run(any(CqnInsert.class));
  }

  @Test
  void startExtractionCreatesOneJobWithCorrectFields() {
    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    ArgumentCaptor<CqnInsert> insertCaptor = forClass(CqnInsert.class);
    verify(persistenceService, times(1)).run(insertCaptor.capture());
    ExtractionJob inserted =
        Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
    Assertions.assertThat(inserted.getAttachmentId()).isEqualTo(ATT_123);
    Assertions.assertThat(inserted.getTenantId()).isEqualTo(TENANT_1);
  }

  @Test
  void startExtractionTransitionsStatusToProcessingThenCompleted() {
    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);
    List<CqnUpdate> updates = captureStatusUpdates(2);
    assertStatusSequence(updates, ExtractionStatus.PROCESSING, ExtractionStatus.COMPLETED);
  }

  @Test
  void startExtractionTransitionsStatusToFailedWhenProcessingThrows() {
    doThrow(new RuntimeException("simulated failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    List<CqnUpdate> updates = captureStatusUpdates(2);
    assertStatusSequence(updates, ExtractionStatus.PROCESSING, ExtractionStatus.FAILED);
  }

  @Test
  void startExtractionFailsWhenJobNotFound() {
    Result emptyResult = mock(Result.class);
    when(emptyResult.single(ExtractionJob.class)).thenThrow(new RuntimeException("not found"));
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(emptyResult);

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void markJobAsFailedIsSkippedWhenTransitionFromPendingToFailedIsInvalid() {
    Result pendingResult = jobWithStatus(ExtractionStatus.PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(pendingResult);
    doThrow(new RuntimeException("simulated failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void invalidTransitionIsLoggedAndNoStatusUpdateOccurs() {
    // COMPLETED has no valid outgoing transitions — PROCESSING update throws IllegalStateException
    Result completedResult = jobWithStatus(ExtractionStatus.COMPLETED);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(completedResult);

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  private Result jobWithStatus(String status) {
    ExtractionJob job = ExtractionJob.create();
    job.setStatus(status);
    Result result = mock(Result.class);
    lenient().when(result.single(ExtractionJob.class)).thenReturn(job);
    return result;
  }

  private List<CqnUpdate> captureStatusUpdates(int expectedCount) {
    ArgumentCaptor<CqnUpdate> captor = forClass(CqnUpdate.class);
    verify(persistenceService, times(expectedCount)).run(captor.capture());
    return captor.getAllValues();
  }

  private void assertStatusSequence(List<CqnUpdate> updates, String first, String second) {
    Assertions.assertThat(
            Struct.access(updates.get(0).entries().get(0)).as(ExtractionJob.class).getStatus())
        .isEqualTo(first);
    Assertions.assertThat(
            Struct.access(updates.get(1).entries().get(0)).as(ExtractionJob.class).getStatus())
        .isEqualTo(second);
  }
}
