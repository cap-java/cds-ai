package com.sap.cds.orchestrator;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ExtractionOrchestrator implements ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionOrchestrator.class);

    private final PersistenceService persistenceService;

    public ExtractionOrchestrator(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void startExtraction(String attachmentId, String tenantId) {
        logger.info("[sap-document-ai] Orchestrator triggered for attachmentId={}, tenantId={}", attachmentId, tenantId);

        ExtractionJob job = ExtractionJob.create();
        job.setAttachmentId(attachmentId);
        job.setStatus(ExtractionStatus.PENDING);
        job.setTenantId(tenantId);

        Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
        String jobId = result.single(ExtractionJob.class).getId();

        logger.info("[sap-document-ai] ExtractionJob created with status=Pending for attachmentId={}", attachmentId);

        CompletableFuture.runAsync(() -> {
            try {
                // assuming process has begun
                Thread.sleep(3000);
                updateStatus(jobId, ExtractionStatus.PROCESSING);

                // fake orchestration for now
                // assuming process has finished / failed
                Thread.sleep(3000);
                updateStatus(jobId, ExtractionStatus.COMPLETED);
            } catch (Exception e) {
                updateStatus(jobId, ExtractionStatus.FAILED);
            }
        });
    }

    private void updateStatus(String jobId, String status) {
            ExtractionJob extractionJob = ExtractionJob.create();
            extractionJob.setStatus(status);
            persistenceService.run(Update.entity(ExtractionJob_.class).byId(jobId).entry(extractionJob));
            logger.info("[sap-document-ai] ExtractionJob jobId={} status updated to {}", jobId, status);
    }


}
