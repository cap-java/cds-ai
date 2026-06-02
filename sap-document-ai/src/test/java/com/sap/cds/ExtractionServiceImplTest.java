/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.service.DocumentAiProcessingService;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceImplTest {
  public static final String TENANT_1 = "tenant-1";
  public static final String ATT_123 = "att-123";
  public static final String CNT_123 = "cnt-123";
  @Mock PersistenceService persistenceService;

  @Mock DocumentAiProcessingService documentAiProcessingService;

  @Mock Result insertResult;

  ExtractionServiceImpl extractionService;

  InputStream mockContent;

  @BeforeEach
  void setUp() {
    ExtractionJob createdJob = ExtractionJob.create();
    createdJob.setId("test-job-id");
    when(insertResult.single(ExtractionJob.class)).thenReturn(createdJob);
    when(persistenceService.run(any(CqnInsert.class))).thenReturn(insertResult);
    lenient().when(persistenceService.run(any(CqnUpdate.class))).thenReturn(mock(Result.class));
    mockContent = new ByteArrayInputStream("test-content".getBytes());
    extractionService = new ExtractionServiceImpl(persistenceService, documentAiProcessingService);
  }

  @Test
  void startExtractionCreatesOneJobWithCorrectFields() {
    // Arrange
    String attachmentId = ATT_123;
    String contentId = CNT_123;
    String tenantId = TENANT_1;

    // Act
    extractionService.startExtraction(attachmentId, contentId, tenantId, mockContent);

    // Assert
    ArgumentCaptor<CqnInsert> insertCaptor = ArgumentCaptor.forClass(CqnInsert.class);
    verify(persistenceService, times(1)).run(insertCaptor.capture());
    ExtractionJob inserted =
        Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(inserted.getAttachmentId()).isEqualTo(attachmentId);
    assertThat(inserted.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void startExtractionTransitionsStatusToProcessingThenCompleted() {
    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    List<CqnUpdate> updates = awaitTwoStatusUpdates();
    assertStatusSequence(updates, ExtractionStatus.PROCESSING, ExtractionStatus.COMPLETED);
  }

  @Test
  void startExtractionTransitionsStatusToFailedWhenProcessingThrows() {
    doThrow(new RuntimeException("simulated processing failure"))
        .when(documentAiProcessingService)
        .processDocument(any(), any());

    extractionService.startExtraction(ATT_123, CNT_123, TENANT_1, mockContent);

    List<CqnUpdate> updates = awaitTwoStatusUpdates();
    assertStatusSequence(updates, ExtractionStatus.PROCESSING, ExtractionStatus.FAILED);
  }

  private List<CqnUpdate> awaitTwoStatusUpdates() {
    ArgumentCaptor<CqnUpdate> updateCaptor = ArgumentCaptor.forClass(CqnUpdate.class);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(persistenceService, times(2)).run(updateCaptor.capture()));
    return updateCaptor.getAllValues();
  }

  private void assertStatusSequence(List<CqnUpdate> updates, String first, String second) {
    assertThat(Struct.access(updates.get(0).entries().get(0)).as(ExtractionJob.class).getStatus())
        .isEqualTo(first);
    assertThat(Struct.access(updates.get(1).entries().get(0)).as(ExtractionJob.class).getStatus())
        .isEqualTo(second);
  }
}
