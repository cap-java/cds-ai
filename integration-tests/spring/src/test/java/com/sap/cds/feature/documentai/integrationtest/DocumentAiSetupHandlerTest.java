/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.handlers.DocumentAiSetupHandler;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.ql.Select;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DocumentAiSetupHandlerTest extends AbstractDocumentAiTest {

  @Autowired ExtractionService extractionService;

  @Test
  void beforeUnsubscribe_marksActiveJobsAsFailed() {
    extractionService.triggerExtraction("doc1.pdf", "application/pdf", null, null, "tenant-a");
    extractionService.triggerExtraction("doc2.pdf", "application/pdf", null, null, "tenant-a");
    extractionService.triggerExtraction("doc3.pdf", "application/pdf", null, null, "tenant-b");

    fireUnsubscribe("tenant-a");

    List<ExtractionJob> tenantAJobs =
        persistenceService
            .run(Select.from(ExtractionJob_.class).where(j -> j.tenantId().eq("tenant-a")))
            .listOf(ExtractionJob.class);

    List<ExtractionJob> tenantBJobs =
        persistenceService
            .run(Select.from(ExtractionJob_.class).where(j -> j.tenantId().eq("tenant-b")))
            .listOf(ExtractionJob.class);

    assertThat(tenantAJobs)
        .hasSize(2)
        .allSatisfy(job -> assertThat(job.getStatus()).isEqualTo(ExtractionStatus.FAILED.name()));

    assertThat(tenantBJobs)
        .hasSize(1)
        .allSatisfy(job -> assertThat(job.getStatus()).isEqualTo(ExtractionStatus.PENDING.name()));
  }

  @Test
  void beforeUnsubscribe_doesNotAffectTerminalJobs() {
    String activeJobId =
        extractionService
            .triggerExtraction("active.pdf", "application/pdf", null, null, "tenant-a")
            .internalJobId();
    String doneJobId =
        extractionService
            .triggerExtraction("done.pdf", "application/pdf", null, null, "tenant-a")
            .internalJobId();

    extractionService.updateExtractionResult(
        doneJobId, ExtractionStatus.SUBMITTED, "die-job-1", null);
    extractionService.updateExtractionResult(
        doneJobId, ExtractionStatus.DONE, "die-job-1", "{\"result\":{}}");

    fireUnsubscribe("tenant-a");

    ExtractionJob activeJob =
        persistenceService
            .run(Select.from(ExtractionJob_.class).byId(activeJobId))
            .single(ExtractionJob.class);
    ExtractionJob doneJob =
        persistenceService
            .run(Select.from(ExtractionJob_.class).byId(doneJobId))
            .single(ExtractionJob.class);

    assertThat(activeJob.getStatus()).isEqualTo(ExtractionStatus.FAILED.name());
    assertThat(doneJob.getStatus()).isEqualTo(ExtractionStatus.DONE.name());
  }

  private void fireUnsubscribe(String tenantId) {
    UnsubscribeEventContext ctx = mock(UnsubscribeEventContext.class);
    when(ctx.getTenant()).thenReturn(tenantId);
    new DocumentAiSetupHandler(persistenceService).beforeUnsubscribe(ctx);
  }
}
