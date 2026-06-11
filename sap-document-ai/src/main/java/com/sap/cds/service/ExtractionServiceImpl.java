/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsElementNotFoundException;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.service.model.ExtractionResult.Status;
import com.sap.cds.service.model.ExtractionSource;
import com.sap.cds.service.utils.StatusTransitionValidator;
import com.sap.cds.services.ServiceDelegator;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractionServiceImpl extends ServiceDelegator implements ExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(ExtractionServiceImpl.class);

  private PersistenceService persistenceService;
  private DocumentAiProcessingService documentAiProcessingService;

  public ExtractionServiceImpl() {
    super(NAME);
  }

  public void init(
      PersistenceService persistenceService,
      DocumentAiProcessingService documentAiProcessingService) {
    this.persistenceService = persistenceService;
    this.documentAiProcessingService = documentAiProcessingService;
  }

  @Override
  public ExtractionResult triggerExtraction(
      String sourceDocumentId,
      String fileName,
      String mimeType,
      InputStream content,
      String tenantId) {
    logger.info(
        "[sap-document-ai] Direct extraction triggered for sourceDocumentId={}, tenantId={}",
        sourceDocumentId,
        tenantId);
    // create pending job
    String jobId = createExtractionJob(ExtractionSource.sourceDocument(sourceDocumentId), tenantId);

    // check for availability of the service.
    if (!documentAiProcessingService.isAvailable()) {
      logger.warn(
          "[sap-document-ai] Document AI unavailable, job {} left as PENDING for retry", jobId);
      return new ExtractionResult(jobId, Status.PENDING, null);
    }

    DocumentInput documentInput = new DocumentInput(fileName, mimeType, content);
    ExtractionResult extractionResult =
        performExtraction(jobId, sourceDocumentId, documentInput, tenantId);
    return extractionResult;
  }

  @On(event = EVENT_START_EXTRACTION)
  public void onStartExtraction(StartExtractionEventContext context) {
    context.setCompleted();

    if (!documentAiProcessingService.isAvailable()) {
      logger.warn("[sap-document-ai] Document AI client is not available, skipping submission");
      return;
    }

    String attachmentId = context.getAttachmentId();
    String tenantId = context.getTenantId();
    String fileName = context.getFileName();
    String contentId = context.getContentId();
    String mimeType = context.getMimeType();

    logger.info(
        "[sap-document-ai] Orchestrator triggered for attachmentId={}, tenantId={}",
        attachmentId,
        tenantId);
    CdsEntity attachmentEntity =
        context.getCdsRuntime().getCdsModel().getEntity(context.getAttachmentEntityName());

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

    DocumentInput documentInput = new DocumentInput(fileName, mimeType, attachmentContent);
    String jobId = createExtractionJob(ExtractionSource.attachment(attachmentId), tenantId);

    logger.info(
        "[sap-document-ai] Triggering extraction for attachmentId={}, contentId={}",
        attachmentId,
        contentId);

    ExtractionResult result = performExtraction(jobId, attachmentId, documentInput, tenantId);
    // log temporarily
    if (result.status() == ExtractionResult.Status.FAILED) {
      logger.warn(
          "[sap-document-ai] Extraction failed for attachmentId={}, jobId={}", attachmentId, jobId);
    }
  }

  private ExtractionResult performExtraction(
      String jobId, String sourceId, DocumentInput documentInput, String tenantId) {
    try {
      String documentAiJobId = documentAiProcessingService.processDocument(jobId, documentInput);
      updateExtractionJob(jobId, SUBMITTED, documentAiJobId);
      // TODO: transition to PROCESSING and COMPLETED via async polling callback, not here
      //      updateExtractionJob(jobId, PROCESSING, null); // or replace w/ documentAiJobId
      //      updateExtractionJob(jobId, COMPLETED, null); // or replace w/ documentAiJobId
      return new ExtractionResult(jobId, Status.SUCCESS, documentAiJobId);
    } catch (IllegalStatusTransitionException e) { // example: COMPLETED -> FAILED
      logger.error("[sap-document-ai] Invalid state transition for jobId={}", jobId, e);
      throw e;
    } catch (Exception e) { // example : PROCESSING -> FAILED
      logger.error(
          "[sap-document-ai] Processing failed for sourceId={}, tenantId={}",
          sourceId,
          tenantId,
          e);
      markJobAsFailed(jobId);
      return new ExtractionResult(jobId, Status.FAILED, null);
    }
  }

  private void markJobAsFailed(String jobId) {
    try {
      updateExtractionJob(jobId, FAILED, null);
    } catch (Exception e) {
      logger.error("[sap-document-ai] Failed to update status to FAILED for jobId={}", jobId, e);
    }
  }

  private String createExtractionJob(ExtractionSource source, String tenantId) {
    ExtractionJob job = ExtractionJob.create();
    job.setAttachmentId(
        source.attachmentId()); // if the entry point is via attachments, this is populated
    job.setSourceDocumentId(
        source.sourceDocumentId()); // if it's standalone via OData APIs, then this is populated
    job.setTenantId(tenantId);
    job.setStatus(PENDING.name());

    Result result = persistenceService.run(Insert.into(ExtractionJob_.class).entry(job));
    String jobId = result.single(ExtractionJob.class).getId();
    boolean isAttachment = source.attachmentId() != null;
    logger.info(
        "[sap-document-ai] ExtractionJob created with status=PENDING, sourceType={}, sourceId={}, jobId={}",
        isAttachment ? "attachment" : "sourceDocument",
        isAttachment ? source.attachmentId() : source.sourceDocumentId(),
        jobId);
    return jobId;
  }

  private void updateExtractionJob(String jobId, ExtractionStatus status, String documentAiJobId) {
    Result current = persistenceService.run(Select.from(ExtractionJob_.class).byId(jobId));
    ExtractionStatus currentStatus = fromString(current.single(ExtractionJob.class).getStatus());

    if (currentStatus.equals(status)) {
      logger.debug(
          "[sap-document-ai] ExtractionJob jobId={} already in status {}, skipping update",
          jobId,
          status);
      return;
    }

    if (!StatusTransitionValidator.isValid(currentStatus, status)) {
      throw new IllegalStatusTransitionException(
          "Invalid status transition from " + currentStatus + " to " + status);
    }

    ExtractionJob extractionJob = ExtractionJob.create();
    extractionJob.setStatus(status.name());
    if (documentAiJobId != null) {
      extractionJob.setDocumentAiJobId(documentAiJobId);
    }

    Result updateResult =
        persistenceService.run(
            Update.entity(ExtractionJob_.class)
                .byId(jobId)
                .where(j -> j.get(ExtractionJob.STATUS).eq(currentStatus.name()))
                .entry(extractionJob));

    if (updateResult.rowCount() == 0) {
      logger.error(
          "[sap-document-ai] Status update skipped for jobId={} — concurrent modification detected (expected status={}, update affected 0 rows)",
          jobId,
          currentStatus);
      throw new IllegalStatusTransitionException(
          "Concurrent modification detected for jobId="
              + jobId
              + ", expected status="
              + currentStatus);
    }

    logger.info(
        "[sap-document-ai] ExtractionJob jobId={} status updated from {} to {}{}",
        jobId,
        currentStatus,
        status,
        documentAiJobId != null ? " with documentAiJobId=" + documentAiJobId : "");
  }

  private Optional<Attachments> getAttachment(CdsEntity attachmentEntity, String contentId) {
    logger.info(
        "Started finding attachment {} of entity {}.",
        contentId,
        attachmentEntity.getQualifiedName());

    Optional<Attachments> attachmentsOptional =
        selectData(attachmentEntity, contentId).stream()
            .filter(result -> hasExactlyOneAttachment(contentId, result))
            .findFirst()
            .map(
                r -> {
                  Attachments found = r.result().single(Attachments.class);
                  logger.debug(
                      "Found attachment {} in entity {}.",
                      found.getContentId(),
                      r.entity().getQualifiedName());
                  return found;
                });
    return attachmentsOptional;
  }

  private static boolean hasExactlyOneAttachment(String contentId, SelectionResult result) {
    long rowCount = result.result().rowCount();
    if (rowCount <= 0) {
      logger.info(
          "No attachment {} found in entity {}.", contentId, result.entity().getQualifiedName());
      return false;
    }
    if (rowCount > 1) {
      throw new IllegalStateException(
          "More than one attachment with contentId %s.".formatted(contentId));
    }
    return true;
  }

  private List<SelectionResult> selectData(CdsEntity attachmentEntity, String contentId) {
    List<SelectionResult> result = new ArrayList<>();
    try {
      CdsEntity sibling = attachmentEntity.getTargetOf(Drafts.SIBLING_ENTITY);
      result.add(new SelectionResult(sibling, readData(contentId, sibling)));
    } catch (CdsElementNotFoundException ignored) {
      // no draft sibling — nothing to select
    }
    result.add(new SelectionResult(attachmentEntity, readData(contentId, attachmentEntity)));
    return result;
  }

  private Result readData(String contentId, CdsEntity entity) {
    CqnSelect select =
        Select.from(entity)
            .columns(Attachments.CONTENT_ID, Attachments.CONTENT)
            .where(e -> e.get(Attachments.CONTENT_ID).eq(contentId));
    return persistenceService.run(select);
  }

  private record SelectionResult(CdsEntity entity, Result result) {}
}
