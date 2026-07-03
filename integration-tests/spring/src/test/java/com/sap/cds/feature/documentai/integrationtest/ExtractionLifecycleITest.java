/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.ql.Select;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExtractionLifecycleITest extends AbstractDocumentAiITest {

  private static final String DIE_JOB_ID = "die-job-1";
  private static final String EXTRACTION_RESULT_JSON = "{\"invoiceNumber\":\"INV-001\"}";

  @Autowired
  ExtractionService extractionService;
  @Autowired
  ExtractionResultCaptureHandler captureHandler;

  @AfterEach
  void resetCapture() {
    captureHandler.reset();
  }

  @Test
  void jobAdvancesThroughLifecycleToDone() {
    ExtractionResult submission = submit("invoice.pdf");
    String jobId = submission.internalJobId();
    assertThat(submission.status()).isEqualTo(ExtractionResult.Status.PENDING);

    extractionService.updateExtractionResult(jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null);
    extractionService.updateExtractionResult(jobId, ExtractionStatus.RUNNING, DIE_JOB_ID, null);
    extractionService.updateExtractionResult(
        jobId, ExtractionStatus.DONE, DIE_JOB_ID, EXTRACTION_RESULT_JSON);

    ExtractionJob job = job(jobId);
    assertThat(job.getStatus()).isEqualTo(ExtractionStatus.DONE.name());
    assertThat(job.getDocumentAiJobId()).isEqualTo(DIE_JOB_ID);
    assertThat(job.getExtractionResult()).isEqualTo(EXTRACTION_RESULT_JSON);
  }

  @Test
  void jobCanTransitionToFailed() {
    String jobId = submit("bad.pdf").internalJobId();

    extractionService.updateExtractionResult(jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null);
    extractionService.updateExtractionResult(jobId, ExtractionStatus.FAILED, DIE_JOB_ID, null);

    ExtractionJob job = job(jobId);
    assertThat(job.getStatus()).isEqualTo(ExtractionStatus.FAILED.name());
    assertThat(job.getDocumentAiJobId()).isEqualTo(DIE_JOB_ID);
  }

  @Test
  void singleDocumentFullRoundTripViaPollCycle() {
    String jobId = submit("invoice.pdf").internalJobId();
    extractionService.updateExtractionResult(jobId, ExtractionStatus.SUBMITTED, DIE_JOB_ID, null);

    runPollCycle(
        extractionService,
        dieJobId -> new ExtractionData(dieJobId, "DONE", EXTRACTION_RESULT_JSON));

    ExtractionJob job = job(jobId);
    assertThat(job.getStatus()).isEqualTo(ExtractionStatus.DONE.name());
    assertThat(job.getExtractionResult()).isEqualTo(EXTRACTION_RESULT_JSON);
    assertThat(captureHandler.getCaptured())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getJobId()).isEqualTo(jobId);
              assertThat(event.getExtractionResult()).isEqualTo(EXTRACTION_RESULT_JSON);
            });
  }

  @Test
  void twoParallelDocumentsReachDoneIndependently() {
    String dieJobId1 = "die-job-parallel-1";
    String dieJobId2 = "die-job-parallel-2";
    String result1 = "{\"invoiceNumber\":\"INV-A\"}";
    String result2 = "{\"invoiceNumber\":\"INV-B\"}";

    String jobId1 = submit("doc-a.pdf").internalJobId();
    String jobId2 = submit("doc-b.pdf").internalJobId();

    extractionService.updateExtractionResult(jobId1, ExtractionStatus.SUBMITTED, dieJobId1, null);
    extractionService.updateExtractionResult(jobId2, ExtractionStatus.SUBMITTED, dieJobId2, null);

    Map<String, String> resultsByDieJobId = Map.of(dieJobId1, result1, dieJobId2, result2);
    runPollCycle(
        extractionService,
        dieJobId -> new ExtractionData(dieJobId, "DONE", resultsByDieJobId.get(dieJobId)));

    List<ExtractionJob> jobs =
        persistenceService.run(Select.from(ExtractionJob_.class)).listOf(ExtractionJob.class);
    assertThat(jobs)
        .extracting(ExtractionJob::getStatus)
        .containsOnly(ExtractionStatus.DONE.name());

    assertThat(job(jobId1).getExtractionResult()).isEqualTo(result1);
    assertThat(job(jobId2).getExtractionResult()).isEqualTo(result2);

    assertThat(captureHandler.getCaptured())
        .extracting(DocumentExtractionResult::getJobId)
        .containsExactlyInAnyOrder(jobId1, jobId2);
  }

  @Test
  void pollCycleContinuesWhenOneJobFails() {
    // one polling request fails mid-cycle — the other job must still reach DONE
    String dieJobIdA = "die-job-ok";
    String dieJobIdB = "die-job-error";

    String jobIdA = submit("doc-a.pdf").internalJobId();
    String jobIdB = submit("doc-b.pdf").internalJobId();

    extractionService.updateExtractionResult(jobIdA, ExtractionStatus.SUBMITTED, dieJobIdA, null);
    extractionService.updateExtractionResult(jobIdB, ExtractionStatus.SUBMITTED, dieJobIdB, null);

    runPollCycle(
        extractionService,
        dieJobId -> {
          if (dieJobId.equals(dieJobIdB)) throw new RuntimeException("simulated DIE failure");
          return new ExtractionData(dieJobId, "DONE", EXTRACTION_RESULT_JSON);
        });

    assertThat(job(jobIdA).getStatus()).isEqualTo(ExtractionStatus.DONE.name());
    assertThat(job(jobIdB).getStatus()).isEqualTo(ExtractionStatus.SUBMITTED.name());

    assertThat(captureHandler.getCaptured())
        .singleElement()
        .satisfies(event -> assertThat(event.getJobId()).isEqualTo(jobIdA));
  }

  private ExtractionResult submit(String fileName) {
    return extractionService.triggerExtraction(fileName, "application/pdf", null, null, "tenant-1");
  }

  private ExtractionJob job(String jobId) {
    return persistenceService
        .run(Select.from(ExtractionJob_.class).byId(jobId))
        .single(ExtractionJob.class);
  }
}
