/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EventEmissionTest extends AbstractDocumentAiTest {

  private static final String DIE_JOB_ID = "die-job-emit-1";
  private static final String EXTRACTION_RESULT_JSON = "{\"invoiceNumber\":\"INV-042\"}";

  @Autowired
  ExtractionService extractionService;
  @Autowired
  ExtractionResultCaptureHandler captureHandler;

  @AfterEach
  void resetCapture() {
    captureHandler.reset();
  }

  @Test
  void pollingHandlerEmitsDocumentExtractionResultWhenJobReachesDone() {
    String jobId =
        extractionService
            .triggerExtraction("invoice.pdf", "application/pdf", null, null, "tenant-1")
            .internalJobId();

    extractionService.updateExtractionResult(jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null);

    runPollCycle(
        extractionService,
        dieJobId -> new ExtractionData(dieJobId, "DONE", EXTRACTION_RESULT_JSON));

    assertThat(captureHandler.getCaptured())
        .singleElement()
        .satisfies(
            captured -> {
              assertThat(captured.getJobId()).isEqualTo(jobId);
              assertThat(captured.getExtractionResult()).isEqualTo(EXTRACTION_RESULT_JSON);
            });
  }
}
