package customer.bookshop.handlers;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.Books_;
import cds.gen.adminservice.BooksAttachments;
import cds.gen.adminservice.BooksAttachments_;
import cds.gen.adminservice.BooksDraftActivateContext;
import cds.gen.adminservice.BooksExtractDocumentDataContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@ServiceName(AdminService_.CDS_NAME)
public class DocumentExtractionHandler implements EventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentExtractionHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final DraftService adminService;
  private final CdsModel cdsModel;
  private final ServiceCatalog serviceCatalog;

  public DocumentExtractionHandler(
      @Qualifier(AdminService_.CDS_NAME) DraftService adminService,
      CdsModel cdsModel,
      ServiceCatalog serviceCatalog) {
    this.adminService = adminService;
    this.cdsModel = cdsModel;
    this.serviceCatalog = serviceCatalog;
  }

  @Before(event = BooksDraftActivateContext.CDS_NAME, entity = Books_.CDS_NAME)
  public void beforeDraftActivate(BooksDraftActivateContext context) {
    String bookId = (String) CqnAnalyzer.create(cdsModel)
        .analyze(context.getCqn().ref())
        .rootKeys()
        .get(Books_.ID);
    if (bookId == null) return;

    long count = adminService.run(
        Select.from(BooksAttachments_.class)
            .columns(b -> b.ID())
            .where(b -> b.up__ID().eq(bookId).and(b.IsActiveEntity().eq(false)))
    ).rowCount();

    logger.info("[DocumentExtractionHandler] draftActivate bookId={}, draft attachment count={}", bookId, count);

    if (count > 1) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST,
          "Only one attachment is allowed per book.");
    }
  }

  @On(event = BooksExtractDocumentDataContext.CDS_NAME)
  public void onExtractDocumentData(BooksExtractDocumentDataContext context) {
    // get attachment
    String bookId = (String) CqnAnalyzer.create(cdsModel)
        .analyze(context.getCqn())
        .rootKeys()
        .get(Books_.ID);

    if (bookId == null) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Could not determine book ID.");
    }

    BooksAttachments attachment = adminService.run(
        Select.from(BooksAttachments_.class)
            .columns(b -> b.ID(), b -> b.fileName(), b -> b.mimeType(), b -> b.content())
            .where(b -> b.up__ID().eq(bookId).and(b.IsActiveEntity().eq(true)))
    ).first(BooksAttachments.class).orElse(null);

    if (attachment == null) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST,
          "No attachment found for this book. Please upload a document first.");
    }

    if (attachment.getContent() == null) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST,
          "Attachment has no content. Please re-upload the document.");
    }

    Service documentAiService = serviceCatalog.getService(Service.class, DocumentAiService_.CDS_NAME);
    if (documentAiService == null) {
      throw new ServiceException(ErrorStatuses.SERVER_ERROR,
          "Document AI service is not available. Please ensure the sap-document-ai plugin is configured.");
    }

    DocumentExtraction event = DocumentExtraction.create();
    event.setFileName(attachment.getFileName());
    event.setMimeType(attachment.getMimeType());
    event.setContent(attachment.getContent());
    try {
      event.setOptions(objectMapper.writeValueAsString(java.util.Map.of(
          "clientId", "default",
          "documentType", "invoice",
          "receivedDate", "2020-02-17",
          "schemaId", "cf8cc8a9-1eee-42d9-9a3e-507a61baac23",
          "templateId", "detect",
          "candidateTemplateIds", java.util.List.of(),
          "enrichment", java.util.Map.of())));
    } catch (JsonProcessingException e) {
      throw new ServiceException(ErrorStatuses.SERVER_ERROR, "Failed to build extraction options", e);
    }

    DocumentExtractionContext eventContext = DocumentExtractionContext.create();
    eventContext.setData(event);

    // emit event
    documentAiService.emit(eventContext);

    logger.info("[DocumentExtractionHandler] Emitted DocumentExtraction event for bookId={}", bookId);

    context.setResult(true);
  }
}
