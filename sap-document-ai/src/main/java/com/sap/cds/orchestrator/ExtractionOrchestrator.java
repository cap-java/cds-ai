package com.sap.cds.orchestrator;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));

        logger.info("[sap-document-ai] ExtractionJob created with status=Pending for attachmentId={}", attachmentId);
    }

}
