/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.handlers;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.StartExtractionEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = AttachmentService.class)
public class AttachmentEventHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentEventHandler.class);

  private final ExtractionService extractionService;

  public AttachmentEventHandler(ExtractionService extractionService) {
    this.extractionService = extractionService;
  }

  @After(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void afterCreateAttachment(AttachmentCreateEventContext context) {
    String attachmentId = (String) context.getAttachmentIds().get(Attachments.ID);

    if (attachmentId == null) {
      logger.warn("[sap-document-ai] attachmentId is null, skipping extraction");
      return;
    }

    MediaData data = context.getData();

    StartExtractionEventContext eventContext = StartExtractionEventContext.create();
    eventContext.setAttachmentId(attachmentId);
    eventContext.setContentId(context.getContentId());
    eventContext.setTenantId(context.getUserInfo().getTenant());
    eventContext.setFileName(data.getFileName());
    eventContext.setMimeType(data.getMimeType());
    eventContext.setAttachmentEntityName(context.getAttachmentEntity().getQualifiedName());

    logger.info(
        "[sap-document-ai] Queuing extraction for attachmentId={}, contentId={}",
        attachmentId,
        context.getContentId());

    extractionService.emit(eventContext);
  }
}
