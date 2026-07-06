/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package customer.bookshop.handlers;

import cds.gen.sap.capire.bookshop.SupplierInvoices;
import cds.gen.sap.capire.bookshop.SupplierInvoicesAttachments;
import cds.gen.sap.capire.bookshop.SupplierInvoicesAttachments_;
import cds.gen.sap.capire.bookshop.SupplierInvoices_;
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
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ServiceName(SupplierInvoicesService_.CDS_NAME)
public class SupplierInvoiceHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SupplierInvoiceHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String STATUS_EXTRACTING = "EXTRACTING";

  @Autowired private PersistenceService db;
  @Autowired private CdsModel cdsModel;
  @Autowired private ServiceCatalog serviceCatalog;

  @On(event = SupplierInvoicesExtractInvoiceDataContext.CDS_NAME)
  public void onExtractInvoiceData(SupplierInvoicesExtractInvoiceDataContext context) {
    String invoiceId =
        (String)
            CqnAnalyzer.create(cdsModel)
                .analyze(context.getCqn())
                .rootKeys()
                .get(SupplierInvoices.ID);

    if (invoiceId == null) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Could not determine invoice ID.");
    }

    // Read the first attachment for this invoice
    var attachment =
        db.run(
                Select.from(SupplierInvoicesAttachments_.class)
                    .columns(a -> a.ID(), a -> a.fileName(), a -> a.mimeType(), a -> a.content())
                    .where(a -> a.up__ID().eq(invoiceId))
                    .limit(1))
            .first(SupplierInvoicesAttachments.class)
            .orElse(null);

    if (attachment == null) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "No attachment found for this invoice. Upload a PDF document first.");
    }

    String fileName = attachment.getFileName();
    String mimeType =
        attachment.getMimeType() != null ? attachment.getMimeType() : "application/pdf";
    InputStream content = attachment.getContent();

    if (content == null) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST, "Attachment has no content. Please re-upload the document.");
    }

    // Emit DocumentExtraction event to the doc-ai plugin
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
    db.run(
        Update.entity(SupplierInvoices_.CDS_NAME)
            .where(i -> i.get(SupplierInvoices.ID).eq(invoiceId))
            .data(Map.of(SupplierInvoices.STATUS_CODE, STATUS_EXTRACTING)));

    logger.info("[SupplierInvoiceHandler] Emitted DocumentExtraction for invoiceId={}", invoiceId);
    context.setResult(true);
  }
}
