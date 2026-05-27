package com.sap.cds;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.persistence.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceImplTest {

    @Mock
    PersistenceService persistenceService;

    @Mock
    Result insertResult;

    ExtractionServiceImpl orchestrator;

    @BeforeEach
    void setUp() {
        ExtractionJob createdJob = ExtractionJob.create();
        createdJob.setId("test-job-id");
        when(insertResult.single(ExtractionJob.class)).thenReturn(createdJob);
        when(persistenceService.run(any(CqnInsert.class))).thenReturn(insertResult);
        lenient().when(persistenceService.run(any(CqnUpdate.class))).thenReturn(mock(Result.class));
        orchestrator = new ExtractionServiceImpl(persistenceService);
    }

    @Test
    void startExtraction_createsOneJobWithCorrectFields() {
        // Arrange
        String attachmentId = "att-123";
        String tenantId = "tenant-1";

        // Act
        orchestrator.startExtraction(attachmentId, tenantId);

        // Assert
        ArgumentCaptor<CqnInsert> insertCaptor = ArgumentCaptor.forClass(CqnInsert.class);
        verify(persistenceService, times(1)).run(insertCaptor.capture());
        ExtractionJob inserted = Struct.access(insertCaptor.getValue().entries().get(0)).as(ExtractionJob.class);
        assertThat(inserted.getAttachmentId()).isEqualTo(attachmentId);
        assertThat(inserted.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void startExtraction_transitionsStatusToProcessingThenCompleted() {
        // Arrange
        ArgumentCaptor<CqnUpdate> updateCaptor = ArgumentCaptor.forClass(CqnUpdate.class);

        // Act
        orchestrator.startExtraction("att-123", "tenant-1");

        // Assert
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(persistenceService, times(2)).run(updateCaptor.capture())
        );
        List<CqnUpdate> updates = updateCaptor.getAllValues();
        assertThat(Struct.access(updates.get(0).entries().get(0)).as(ExtractionJob.class).getStatus())
                .isEqualTo(ExtractionStatus.PROCESSING);
        assertThat(Struct.access(updates.get(1).entries().get(0)).as(ExtractionJob.class).getStatus())
                .isEqualTo(ExtractionStatus.COMPLETED);
    }

    @Test
    void startExtraction_transitionsStatusToFailedWhenProcessingThrows() {
        // Arrange
        when(persistenceService.run(any(CqnUpdate.class)))
                .thenThrow(new RuntimeException("simulated processing failure"));
        ArgumentCaptor<CqnUpdate> updateCaptor = ArgumentCaptor.forClass(CqnUpdate.class);

        // Act
        orchestrator.startExtraction("att-123", "tenant-1");

        // Assert
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(persistenceService, atLeastOnce()).run(updateCaptor.capture())
        );
        List<CqnUpdate> updates = updateCaptor.getAllValues();
        ExtractionJob lastUpdate = Struct.access(updates.get(updates.size() - 1).entries().get(0)).as(ExtractionJob.class);
        assertThat(lastUpdate.getStatus()).isEqualTo(ExtractionStatus.FAILED);
    }

}
