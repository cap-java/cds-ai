package com.sap.cds.handlers;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* Currently a placeholder handler, to integrate with attachments and test if it is triggered :)
* */
@ServiceName(value = "*", type = AttachmentService.class)
public class AttachmentEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentEventHandler.class);

    @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
    public void onCreateAttachment(AttachmentCreateEventContext context) {
        log.info("[sap-document-ai] Attachment created: {}", context.getAttachmentIds().get(Attachments.ID));
    }
}
