/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Struct;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsElementNotFoundException;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
  static final String TEST_PDF = "test.pdf";
  static final String CONTENT_TYPE = "application/pdf";
  static final String TEST_CONTENT = "test-content";
  static final String ENTITY_NAME = "test.Attachments";

  @Mock PersistenceService persistenceService;
  @Mock DocumentAiProcessingService documentAiProcessingService;
  @Mock Result insertResult;
  @Mock CdsRuntime cdsRuntime;
  @Mock CdsModel cdsModel;
  @Mock CdsEntity cdsEntity;

  ExtractionServiceImpl extractionService;

  @BeforeEach
  void setUp() {
    when(documentAiProcessingService.isAvailable()).thenReturn(true);
    extractionService = new ExtractionServiceImpl();
    extractionService.init(persistenceService, documentAiProcessingService);
  }

  @Test
  void startExtractionDoesNothingWhenServiceUnavailable() {
    when(documentAiProcessingService.isAvailable()).thenReturn(false);
    extractionService.onStartExtraction(eventContext());
    verify(persistenceService, never()).run(any(CqnInsert.class));
  }

  @Test
  void startExtractionCreatesOneJobWithCorrectFields() {
    mockContentLookup(contentStream());
    mockAllDatabaseCalls();
    mockSuccessfulProcessing();
    extractionService.onStartExtraction(eventContext());

    ArgumentCaptor<CqnInsert> insertCaptor = ArgumentCaptor.forClass(CqnInsert.class);
    verify(persistenceService, atLeastOnce()).run(insertCaptor.capture());
    ExtractionJob inserted =
        Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(inserted.getAttachmentId()).isEqualTo(ATT_123);
    assertThat(inserted.getTenantId()).isEqualTo(TENANT_1);
    assertThat(inserted.getStatus()).isEqualTo(PENDING.name());
  }

  @Test
  void startExtractionStoresDocumentAiJobIdAndUpdatesStatusToSubmitted() {
    Result statusResult = resultWithJobStatus(PENDING);
    mockContentThenStatus(contentStream(), statusResult);
    mockAllDatabaseCalls();
    mockSuccessfulProcessing();
    extractionService.onStartExtraction(eventContext());

    ArgumentCaptor<CqnUpdate> captor = ArgumentCaptor.forClass(CqnUpdate.class);
    verify(persistenceService, times(1)).run(captor.capture());

    ExtractionJob update =
        Struct.access(captor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(update.getStatus()).isEqualTo(SUBMITTED.name());
    assertThat(update.getDocumentAiJobId()).isEqualTo(DIE_JOB_ID);
  }

  @Test
  void startExtractionSkipsWhenAttachmentNotFound() {
    mockContentLookup(null);
    extractionService.onStartExtraction(eventContext());
    verify(persistenceService, never()).run(any(CqnInsert.class));
  }

  @Test
  void startExtractionSkipsWhenContentStreamIsNull() {
    mockContentLookupWithNullStream();
    extractionService.onStartExtraction(eventContext());
    verify(persistenceService, never()).run(any(CqnInsert.class));
  }

  @Test
  void updateStatusWithSameStateDoesNotRunJobAgain() {
    Result statusResult = resultWithJobStatus(SUBMITTED);
    mockContentThenStatus(contentStream(), statusResult);
    mockInsertDatabaseCalls();
    mockSuccessfulProcessing();

    extractionService.onStartExtraction(eventContext());

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void invalidTransitionIsLoggedAndNoStatusUpdateOccurs() {
    Result statusResult = resultWithJobStatus(COMPLETED);
    mockContentThenStatus(contentStream(), statusResult);
    mockInsertDatabaseCalls();
    mockSuccessfulProcessing();
    assertThrows(
        IllegalStatusTransitionException.class,
        () -> extractionService.onStartExtraction(eventContext()));
    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void markJobAsFailedSucceedsWhenTransitionFromPendingToFailedIsValid() {
    Result statusResult = resultWithJobStatus(PENDING);
    mockContentThenStatus(contentStream(), statusResult);
    mockAllDatabaseCalls();
    doThrow(new RuntimeException("simulated failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());
    extractionService.onStartExtraction(eventContext());
    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void triggerExtractionCreatesJobAsPendingWhenServiceUnavailable() {
    mockInsertDatabaseCalls(); // job creation must succeed
    when(documentAiProcessingService.isAvailable()).thenReturn(false);

    ExtractionResult result =
        extractionService.triggerExtraction(
            ATT_123, TEST_PDF, CONTENT_TYPE, contentStream(), TENANT_1);

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.PENDING);
    assertThat(result.internalJobId()).isNotNull();
    verify(persistenceService).run(any(CqnInsert.class)); // job was created
  }

  @Test
  void triggerExtractionMarksJobFailedOnInvalidStatusTransition() {
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
                ATT_123, TEST_PDF, CONTENT_TYPE, contentStream(), TENANT_1));

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void triggerExtractionSubmitsDocumentAndUpdatesStatus() {
    mockInsertDatabaseCalls();
    mockAllDatabaseCalls();
    Result statusResult = resultWithJobStatus(PENDING);
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(statusResult);
    when(documentAiProcessingService.processDocument(any(), any())).thenReturn(DIE_JOB_ID);

    extractionService.triggerExtraction(ATT_123, TEST_PDF, CONTENT_TYPE, contentStream(), TENANT_1);

    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
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

    extractionService.triggerExtraction(ATT_123, TEST_PDF, CONTENT_TYPE, contentStream(), TENANT_1);

    verify(persistenceService, times(1)).run(any(CqnUpdate.class));
  }

  private StartExtractionEventContext eventContext() {
    StartExtractionEventContext ctx = mock(StartExtractionEventContext.class);
    lenient().when(ctx.getAttachmentId()).thenReturn(ATT_123);
    lenient().when(ctx.getTenantId()).thenReturn(TENANT_1);
    lenient().when(ctx.getContentId()).thenReturn(CNT_123);
    lenient().when(ctx.getFileName()).thenReturn(TEST_PDF);
    lenient().when(ctx.getMimeType()).thenReturn(CONTENT_TYPE);
    lenient().when(ctx.getAttachmentEntityName()).thenReturn(ENTITY_NAME);
    lenient().when(ctx.getCdsRuntime()).thenReturn(cdsRuntime);
    return ctx;
  }

  private InputStream contentStream() {
    return new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
  }

  private void mockEntityLookup() {
    when(cdsRuntime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.getEntity(ENTITY_NAME)).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn(ENTITY_NAME);
    doThrow(CdsElementNotFoundException.class).when(cdsEntity).getTargetOf(any());
  }

  private void mockContentThenStatus(InputStream content, Result statusResult) {
    mockEntityLookup();
    Attachments attachment = Attachments.create();
    attachment.setContentId(CNT_123);
    attachment.setContent(content);

    Result contentResult = mock(Result.class);
    lenient().when(contentResult.rowCount()).thenReturn(1L);
    lenient().when(contentResult.single(Attachments.class)).thenReturn(attachment);
    lenient().when(contentResult.first(Attachments.class)).thenReturn(Optional.of(attachment));

    lenient()
        .when(persistenceService.run(any(CqnSelect.class)))
        .thenReturn(contentResult, statusResult);
  }

  private void mockContentLookup(InputStream content) {
    mockEntityLookup();
    Attachments attachment = Attachments.create();
    attachment.setContentId(CNT_123);
    attachment.setContent(content);

    Result contentResult = mock(Result.class);
    lenient().when(contentResult.rowCount()).thenReturn(content != null ? 1L : 0L);
    lenient().when(contentResult.single(Attachments.class)).thenReturn(attachment);
    lenient()
        .when(contentResult.first(Attachments.class))
        .thenReturn(content != null ? Optional.of(attachment) : Optional.empty());
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(contentResult);
  }

  private void mockContentLookupWithNullStream() {
    mockEntityLookup();
    Attachments attachment = Attachments.create();
    attachment.setContentId(CNT_123);
    attachment.setContent(null);

    Result contentResult = mock(Result.class);
    lenient().when(contentResult.rowCount()).thenReturn(1L);
    lenient().when(contentResult.single(Attachments.class)).thenReturn(attachment);
    lenient().when(contentResult.first(Attachments.class)).thenReturn(Optional.of(attachment));
    lenient().when(persistenceService.run(any(CqnSelect.class))).thenReturn(contentResult);
  }

  private void mockSuccessfulProcessing() {
    lenient()
        .when(documentAiProcessingService.processDocument(any(), any()))
        .thenReturn(DIE_JOB_ID);
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
