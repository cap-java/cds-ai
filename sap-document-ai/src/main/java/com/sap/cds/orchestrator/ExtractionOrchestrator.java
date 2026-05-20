package com.sap.cds.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionOrchestrator.class);

    public void startExtraction(String attachmentId) {
        logger.info("[sap-document-ai] Orchestrator triggered w/ attachment id {}", attachmentId);
    }

}
