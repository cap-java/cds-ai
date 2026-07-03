/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.ql.Select;
import com.sap.cds.services.Service;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DocumentSubmissionITest extends AbstractDocumentAiITest {

  @Autowired
  ExtractionService extractionService;

  @Test
  void submissionWithoutDieBindingCreatesJobAsPending() {
    Service documentAiService =
        serviceCatalog.getService(Service.class, DocumentAiService_.CDS_NAME);

    documentAiService.emit(createExtractionContext());

    assertThat(persistenceService.run(Select.from(ExtractionJob_.class)).listOf(ExtractionJob.class))
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.getStatus()).isEqualTo("PENDING");
              assertThat(job.getId()).isNotNull();
            });
  }

  @Test
  void submissionStoresTenantOnJob() {
    ExtractionResult submission =
        extractionService.triggerExtraction(
            "invoice.pdf", "application/pdf", null, null, "tenant-1");

    ExtractionJob job =
        persistenceService
            .run(Select.from(ExtractionJob_.class).byId(submission.internalJobId()))
            .single(ExtractionJob.class);

    assertThat(job.getStatus()).isEqualTo("PENDING");
    assertThat(job.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void jobsForDifferentTenantsAreStoredIndependently() {
    String jobId1 =
        extractionService
            .triggerExtraction("doc.pdf", "application/pdf", null, null, "tenant-a")
            .internalJobId();
    String jobId2 =
        extractionService
            .triggerExtraction("doc.pdf", "application/pdf", null, null, "tenant-b")
            .internalJobId();

    ExtractionJob job1 =
        persistenceService
            .run(Select.from(ExtractionJob_.class).byId(jobId1))
            .single(ExtractionJob.class);
    ExtractionJob job2 =
        persistenceService
            .run(Select.from(ExtractionJob_.class).byId(jobId2))
            .single(ExtractionJob.class);

    assertThat(job1.getTenantId()).isEqualTo("tenant-a");
    assertThat(job2.getTenantId()).isEqualTo("tenant-b");
    assertThat(job1.getId()).isNotEqualTo(job2.getId());
  }

  private DocumentExtractionContext createExtractionContext() {
    DocumentExtraction event = DocumentExtraction.create();
    event.setFileName("test.pdf");
    event.setMimeType("application/pdf");
    event.setContent(new ByteArrayInputStream("pdf-content".getBytes(StandardCharsets.UTF_8)));

    DocumentExtractionContext ctx = DocumentExtractionContext.create();
    ctx.setData(event);
    return ctx;
  }
}
