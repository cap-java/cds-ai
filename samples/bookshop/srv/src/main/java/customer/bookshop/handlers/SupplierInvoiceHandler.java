/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package customer.bookshop.handlers;

import cds.gen.supplierinvoicesservice.SupplierInvoices;
import cds.gen.supplierinvoicesservice.SupplierInvoicesAttachments_;
import cds.gen.supplierinvoicesservice.SupplierInvoices_;
import cds.gen.supplierinvoicesservice.SupplierInvoicesExtractInvoiceDataContext;
import cds.gen.supplierinvoicesservice.SupplierInvoicesService_;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.Service;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@ServiceName(SupplierInvoicesService_.CDS_NAME)
public class SupplierInvoiceHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SupplierInvoiceHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String STATUS_EXTRACTING = "EXTRACTING";

  private final DraftService invoicesService;
  private final CdsModel cdsModel;
  private final ServiceCatalog serviceCatalog;

  public SupplierInvoiceHandler(
      @Qualifier(SupplierInvoicesService_.CDS_NAME) DraftService invoicesService,
      CdsModel cdsModel,
      ServiceCatalog serviceCatalog) {
    this.invoicesService = invoicesService;
    this.cdsModel = cdsModel;
    this.serviceCatalog = serviceCatalog;
  }

  @On(event = SupplierInvoicesExtractInvoiceDataContext.CDS_NAME)
  public void onExtractInvoiceData(SupplierInvoicesExtractInvoiceDataContext context) {
    String invoiceId =
        (String)
            CqnAnalyzer.create(cdsModel)
                .analyze(context.getCqn())
                .rootKeys()
                .get(SupplierInvoices_.ID);

    if (invoiceId == null) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Could not determine invoice ID.");
    }

    // Read the invoice with its first attachment
    SupplierInvoices invoice =
        invoicesService
            .run(
                Select.from(SupplierInvoices_.class)
                    .columns(
                        i -> i.ID(),
                        i -> i.status_code(),
                        i ->
                            i.attachments()
                                .expand(a -> a.ID(), a -> a.fileName(), a -> a.mimeType()))
                    .where(i -> i.ID().eq(invoiceId).and(i.IsActiveEntity().eq(true))))
            .single(SupplierInvoices.class);

    if (invoice.getAttachments() == null || invoice.getAttachments().isEmpty()) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "No attachment found for this invoice. Upload a PDF document first.");
    }

    var attachment = invoice.getAttachments().get(0);
    String fileName = attachment.getFileName();
    String mimeType =
        attachment.getMimeType() != null ? attachment.getMimeType() : "application/pdf";

    // Read the binary content separately
    InputStream content =
        (InputStream)
            invoicesService
                .run(
                    Select.from(SupplierInvoicesAttachments_.class)
                        .columns(a -> a.content())
                        .where(a -> a.ID().eq(attachment.getId())))
                .single()
                .get("content");

    if (content == null) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST, "Attachment has no content. Please re-upload the document.");
    }

    // Emit DocumentExtraction event
    Service documentAiService =
        serviceCatalog.getService(Service.class, DocumentAiService_.CDS_NAME);
    if (documentAiService == null) {
      throw new ServiceException(
          ErrorStatuses.SERVER_ERROR,
          "Document AI service is not available. Ensure cds-feature-sap-document-ai is configured.");
    }

    DocumentExtraction event = DocumentExtraction.create();
    event.setFileName(fileName);
    event.setMimeType(mimeType);
    event.setContent(content);
    try {
      event.setOptions(
          objectMapper.writeValueAsString(
              Map.of(
                  "clientId", "default",
                  "documentType", "invoice",
                  "schemaId", "cf8cc8a9-1eee-42d9-9a3e-507a61baac23",
                  "templateId", "detect")));
    } catch (JsonProcessingException e) {
      throw new ServiceException(
          ErrorStatuses.SERVER_ERROR, "Failed to build extraction options", e);
    }

    DocumentExtractionContext eventContext = DocumentExtractionContext.create();
    eventContext.setData(event);
    documentAiService.emit(eventContext);

    // Update invoice status to EXTRACTING
    invoicesService.run(
        Update.entity(SupplierInvoices_.class)
            .where(i -> i.ID().eq(invoiceId))
            .data(Map.of("status_code", STATUS_EXTRACTING)));

    logger.info(
        "[SupplierInvoiceHandler] Emitted DocumentExtraction for invoiceId={}", invoiceId);
    context.setResult(true);
  }
}
