package com.sap.cds.service;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtractionServiceImpl implements ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionServiceImpl.class);
    private static final int MAX_PARALLEL_EXTRACTIONS = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_EXTRACTIONS);

    private final PersistenceService persistenceService;
    private final DocumentAiProcessingService documentAiProcessingService;

    public ExtractionServiceImpl(PersistenceService persistenceService, DocumentAiProcessingService documentAiProcessingService) {
        this.persistenceService = persistenceService;
        this.documentAiProcessingService = documentAiProcessingService;
    }

    @Override
    public void startExtraction(String attachmentId, String contentId, String tenantId, InputStream content) {
        logger.info("[sap-document-ai] Orchestrator triggered for attachmentId={}, tenantId={}", attachmentId, tenantId);
        String jobId = createExtractionJob(attachmentId, tenantId);

        CompletableFuture.runAsync(() -> {
            try {
                updateStatus(jobId, ExtractionStatus.PROCESSING);
                documentAiProcessingService.processDocument(jobId, content);
                updateStatus(jobId, ExtractionStatus.COMPLETED);
            } catch (Exception e) {
                updateStatus(jobId, ExtractionStatus.FAILED);
            }
        }, executor);
    }

    private String createExtractionJob(String attachmentId, String tenantId) {
        ExtractionJob job = ExtractionJob.create();
        job.setAttachmentId(attachmentId);
        job.setTenantId(tenantId);

        Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
        String jobId = result.single(ExtractionJob.class).getId();
        logger.info("[sap-document-ai] ExtractionJob created with status=Pending for attachmentId={} & jobId={}", attachmentId, jobId);
        return jobId;
    }

    private void updateStatus(String jobId, String status) {
        ExtractionJob extractionJob = ExtractionJob.create();
        extractionJob.setStatus(status);
        persistenceService.run(Update.entity(ExtractionJob_.class).byId(jobId).entry(extractionJob));
        logger.info("[sap-document-ai] ExtractionJob jobId={} status updated to {}", jobId, status);
    }

 }
