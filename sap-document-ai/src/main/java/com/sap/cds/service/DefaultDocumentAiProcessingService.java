package com.sap.cds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class DefaultDocumentAiProcessingService implements DocumentAiProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDocumentAiProcessingService.class);

    @Override
    public void processDocument(String jobId, InputStream content) {
        logger.info("[sap-document-ai] Processing document for jobId={} and content={}", jobId, content);
        try {
            // TODO: Replace mock delay with real Document AI integration
            Thread.sleep(3000);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[sap-document-ai] Interrupted during extraction for jobId={}", jobId, e);
        } catch (Exception e) {
            logger.error("[sap-document-ai] Extraction failed for jobId={}", jobId, e);
        }
    }
}
