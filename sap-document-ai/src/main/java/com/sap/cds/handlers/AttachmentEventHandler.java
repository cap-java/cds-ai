package com.sap.cds.handlers;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.orchestrator.ExtractionOrchestrator;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* Currently a placeholder handler, to integrate with attachments and test if it is triggered :)
* */
@ServiceName(value = "*", type = AttachmentService.class)
public class AttachmentEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentEventHandler.class);

    private final ExtractionOrchestrator extractionOrchestrator;

    public AttachmentEventHandler(ExtractionOrchestrator extractionOrchestrator){
        this.extractionOrchestrator = extractionOrchestrator;
    }

    @After(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
    public void afterCreateAttachment(AttachmentCreateEventContext context) {
        log.info("[sap-document-ai] After Attachment created: {}", context.getAttachmentIds().get(Attachments.ID));
        String attachmentId = (String) context.getAttachmentIds().get(Attachments.ID);
        log.info("[sap-document-ai] Attachment persisted successfully. Triggering extraction workflow for attachmentId={}", attachmentId);
        log.info("[sap-document-ai] attachmentIds={}", context.getAttachmentIds());
        log.info("[sap-document-ai] data={}", context.getData());
        extractionOrchestrator.startExtraction(attachmentId);
    }

}
