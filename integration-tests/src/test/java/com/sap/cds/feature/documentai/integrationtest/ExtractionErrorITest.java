/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.feature.documentai.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExtractionErrorITest extends AbstractDocumentAiITest {

  private static final String DIE_JOB_ID = "die-1";

  @Autowired
  ExtractionService extractionService;

  @Test
  void submissionWithoutDieBindingCreatesPendingJob() {
    ExtractionResult result =
        extractionService.triggerExtraction(
            "invoice.pdf", "application/pdf", null, null, "tenant-1");

    assertThat(result.status()).isEqualTo(ExtractionResult.Status.PENDING);
    assertThat(result.internalJobId()).isNotNull();
    assertThat(result.documentAiJobId()).isNull();
  }

  @Test
  void invalidStatusTransitionThrows() {
    String jobId =
        extractionService
            .triggerExtraction("invoice.pdf", "application/pdf", null, null, "tenant-1")
            .internalJobId();

    extractionService.updateExtractionResult(jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null);
    extractionService.updateExtractionResult(
        jobId, ExtractionStatus.DONE, DIE_JOB_ID, "{\"result\":\"ok\"}");

    assertThatThrownBy(
            () ->
                extractionService.updateExtractionResult(
                    jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null))
        .isInstanceOf(IllegalStatusTransitionException.class)
        .hasMessageContaining(ExtractionStatus.DONE.name())
        .hasMessageContaining(ExtractionStatus.SUBMITTED.name());
  }
}
