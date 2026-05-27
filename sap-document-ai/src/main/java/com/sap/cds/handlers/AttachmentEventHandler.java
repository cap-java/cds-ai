package com.sap.cds.handlers;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.orchestrator.ExtractionService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = AttachmentService.class)
public class AttachmentEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentEventHandler.class);

    private final ExtractionService extractionService;

    public AttachmentEventHandler(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @After(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
    public void afterCreateAttachment(AttachmentCreateEventContext context) {
        String attachmentId = (String) context.getAttachmentIds().get(Attachments.ID);
        String tenantId = context.getUserInfo().getTenant();
        if (attachmentId == null) {
            log.warn("[sap-document-ai] attachmentId is null, skipping extraction");
        }
        log.info("[sap-document-ai] Attachment persisted. Triggering extraction for attachmentId={}, tenantId={}",
                attachmentId, tenantId);
        extractionService.startExtraction(attachmentId, tenantId);
    }

}
