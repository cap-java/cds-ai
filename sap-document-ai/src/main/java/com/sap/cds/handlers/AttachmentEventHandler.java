/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.handlers;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsElementNotFoundException;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.services.changeset.ChangeSetListener;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = AttachmentService.class)
public class AttachmentEventHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentEventHandler.class);

  private final ExtractionService extractionService;
  private final PersistenceService persistenceService;

  public AttachmentEventHandler(
      ExtractionService extractionService, PersistenceService persistenceService) {
    this.extractionService = extractionService;
    this.persistenceService = persistenceService;
  }

  @After(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void afterCreateAttachment(AttachmentCreateEventContext context) {
    String attachmentId = (String) context.getAttachmentIds().get(Attachments.ID);
    String tenantId = context.getUserInfo().getTenant();
    String contentId = context.getContentId();
    CdsRuntime cdsRuntime = context.getCdsRuntime();

    if (attachmentId == null) {
      logger.warn("[sap-document-ai] attachmentId is null, skipping extraction");
      return;
    }

    MediaData contextData = context.getData();
    CdsEntity attachmentEntity = context.getAttachmentEntity();

    context
        .getChangeSetContext()
        .register(
            new ChangeSetListener() {

              @Override
              public void afterClose(boolean completed) {
                logger.info("[sap-document-ai] afterClose fired, completed={}", completed);
                if (!completed) return;

                cdsRuntime
                    .requestContext()
                    .run(
                        (Consumer<RequestContext>)
                            requestCtx ->
                                cdsRuntime
                                    .changeSetContext()
                                    .run(
                                        changeSetCtx -> {
                                          String fileName = contextData.getFileName();
                                          String mimeType = contextData.getMimeType();
                                          triggerExtraction(
                                              attachmentEntity,
                                              contentId,
                                              attachmentId,
                                              tenantId,
                                              fileName,
                                              mimeType);
                                        }));
              }
            });
  }

  private void triggerExtraction(
      CdsEntity attachmentEntity,
      String contentId,
      String attachmentId,
      String tenantId,
      String fileName,
      String mimeType) {
    Optional<Attachments> row = getAttachment(attachmentEntity, contentId);

    if (row.isEmpty()) {
      logger.warn("[sap-document-ai] No attachment found for contentId={}, skipping", contentId);
      return;
    }

    Attachments attachment = row.get();

    InputStream attachmentContent = attachment.getContent();

    if (attachmentContent == null) {
      logger.warn("[sap-document-ai] Content is null for contentId={}, skipping", contentId);
      return;
    }

    DocumentInput documentInput =
        new DocumentInput(fileName, contentId, mimeType, attachmentContent);

    logger.info(
        "[sap-document-ai] Triggering extraction for attachmentId={}, contentId={}",
        attachmentId,
        contentId);

    extractionService.startExtraction(attachmentId, documentInput, tenantId);
  }

  private Optional<Attachments> getAttachment(CdsEntity attachmentEntity, String contentId) {
    logger.info(
        "Started finding attachment {} of entity {}.",
        contentId,
        attachmentEntity.getQualifiedName());

    List<SelectionResult> selectionResults = selectData(attachmentEntity, contentId);

    for (SelectionResult result : selectionResults) {
      long rowCount = result.result().rowCount();

      if (rowCount <= 0) {
        logger.info(
            "No attachment {} found in entity {}.", contentId, result.entity().getQualifiedName());
        continue;
      }

      if (rowCount > 1) {
        throw new IllegalStateException(
            "More than one attachment with contentId %s.".formatted(contentId));
      }

      Attachments found = result.result().single(Attachments.class);
      if (found != null) {
        return Optional.of(found);
      }
    }

    return Optional.empty();
  }

  private List<SelectionResult> selectData(CdsEntity attachmentEntity, String contentId) {
    List<SelectionResult> result = new ArrayList<>();
    try {
      CdsEntity entity = (CdsEntity) attachmentEntity.getTargetOf(Drafts.SIBLING_ENTITY);
      Result selectionResult = readData(contentId, entity);
      result.add(new SelectionResult(entity, selectionResult));
    } catch (CdsElementNotFoundException ignored) {
      // no sibling found nothing to select
    }
    Result selectionResult = readData(contentId, attachmentEntity);
    result.add(new SelectionResult(attachmentEntity, selectionResult));

    return result;
  }

  private Result readData(String contentId, CdsEntity entity) {
    CqnSelect select =
        Select.from(entity)
            .columns(Attachments.CONTENT_ID, Attachments.CONTENT)
            .where(e -> e.get(Attachments.CONTENT_ID).eq(contentId));

    Result result = persistenceService.run(select);
    result
        .streamOf(Attachments.class)
        .forEach(
            attachment ->
                logger.debug(
                    "Found attachment {} in entity {}.",
                    attachment.getContentId(),
                    entity.getQualifiedName()));
    return result;
  }

  private record SelectionResult(CdsEntity entity, Result result) {}
}
