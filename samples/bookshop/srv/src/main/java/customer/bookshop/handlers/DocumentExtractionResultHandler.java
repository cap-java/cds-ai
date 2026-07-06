/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package customer.bookshop.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles extraction results from the SAP Document AI plugin. Parses the extraction JSON returned
 * by the DIE service and populates the corresponding SupplierInvoice entity with the extracted
 * header fields (invoice number, date, total amount, currency).
 */
@Component
@ServiceName(value = "*", type = ApplicationService.class)
public class DocumentExtractionResultHandler implements EventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentExtractionResultHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final PersistenceService db;

  public DocumentExtractionResultHandler(PersistenceService db) {
    this.db = db;
  }

  @On(event = DocumentExtractionResultContext.CDS_NAME)
  public void onExtractionCompleted(DocumentExtractionResultContext context) {
    DocumentExtractionResult data = context.getData();
    logger.info(
        "[bookshop] Invoice extraction completed! jobId={}", data.getJobId());

    try {
      JsonNode root = objectMapper.readTree(data.getExtractionResult());
      JsonNode headerFields = root.path("extraction").path("headerFields");

      // Extract relevant header fields from the DIE response
      String documentNumber = getHeaderFieldValue(headerFields, "documentNumber");
      String documentDate = getHeaderFieldValue(headerFields, "documentDate");
      String currencyCode = getHeaderFieldValue(headerFields, "currencyCode");
      BigDecimal grossAmount = getHeaderFieldNumber(headerFields, "grossAmount");

      logger.info(
          "[bookshop] Extracted: number={}, date={}, amount={} {}, sender={}",
          documentNumber,
          documentDate,
          grossAmount,
          currencyCode,
          getHeaderFieldValue(headerFields, "senderName"));

      // Update the invoice that is currently in EXTRACTING status.
      // In a production app you would correlate by a stored jobId; for this sample
      // we use the status as a simple correlation mechanism.
      Map<String, Object> updateData = new HashMap<>();
      updateData.put("status_code", "EXTRACTED");
      if (documentNumber != null) updateData.put("invoiceNumber", documentNumber);
      if (documentDate != null) updateData.put("invoiceDate", documentDate);
      if (grossAmount != null) updateData.put("totalAmount", grossAmount);
      if (currencyCode != null) updateData.put("currency_code", currencyCode);

      long updated =
          db.run(
                  Update.entity("sap.capire.bookshop.SupplierInvoices")
                      .where(i -> i.get("status_code").eq("EXTRACTING"))
                      .data(updateData))
              .rowCount();

      logger.info("[bookshop] Updated {} invoice(s) with extracted data", updated);

    } catch (Exception e) {
      logger.error("[bookshop] Failed to process extraction result", e);
      // Mark as FAILED
      db.run(
          Update.entity("sap.capire.bookshop.SupplierInvoices")
              .where(i -> i.get("status_code").eq("EXTRACTING"))
              .data(Map.of("status_code", "FAILED")));
    }

    context.setCompleted();
  }

  private String getHeaderFieldValue(JsonNode headerFields, String fieldName) {
    for (JsonNode field : headerFields) {
      if (fieldName.equals(field.path("name").asText())) {
        return field.path("value").asText(null);
      }
    }
    return null;
  }

  private BigDecimal getHeaderFieldNumber(JsonNode headerFields, String fieldName) {
    for (JsonNode field : headerFields) {
      if (fieldName.equals(field.path("name").asText())) {
        JsonNode value = field.path("value");
        if (value.isNumber()) {
          return BigDecimal.valueOf(value.doubleValue());
        }
        return null;
      }
    }
    return null;
  }
}
