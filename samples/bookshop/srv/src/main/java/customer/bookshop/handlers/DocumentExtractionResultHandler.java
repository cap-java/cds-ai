/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package customer.bookshop.handlers;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles extraction results from the SAP Document AI plugin. In a real application this handler
 * would parse the extraction JSON, look up the corresponding SupplierInvoice by documentAiJobId,
 * and populate invoiceNumber, invoiceDate, totalAmount, and line items.
 *
 * <p>This sample implementation logs the result for demonstration purposes.
 */
@Component
@ServiceName(value = "*", type = ApplicationService.class)
public class DocumentExtractionResultHandler implements EventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentExtractionResultHandler.class);

  @On(event = DocumentExtractionResultContext.CDS_NAME)
  public void onExtractionCompleted(DocumentExtractionResultContext context) {
    DocumentExtractionResult data = context.getData();
    logger.info(
        "[bookshop] Invoice extraction completed! jobId={}, result={}",
        data.getJobId(),
        data.getExtractionResult());
    // TODO: parse extractionResult JSON, update the SupplierInvoice entity with extracted fields
    context.setCompleted();
  }
}
