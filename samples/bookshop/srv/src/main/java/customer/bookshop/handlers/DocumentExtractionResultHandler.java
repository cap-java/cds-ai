/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package customer.bookshop.handlers;

import cds.gen.sap.capire.bookshop.SupplierInvoices;
import cds.gen.sap.capire.bookshop.SupplierInvoices_;
import cds.gen.sap.capire.bookshop.Suppliers;
import cds.gen.sap.capire.bookshop.Suppliers_;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Handles extraction results from the SAP Document AI plugin. Parses the extraction JSON returned
 * by the DIE service and populates the corresponding SupplierInvoice entity with the extracted
 * header fields (invoice number, date, total amount, currency, and matched supplier).
 */
@Component
@ServiceName(value = "*", type = ApplicationService.class)
public class DocumentExtractionResultHandler implements EventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentExtractionResultHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String STATUS_EXTRACTED = "EXTRACTED";
  private static final String STATUS_EXTRACTING = "EXTRACTING";
  private static final String STATUS_FAILED = "FAILED";

  @Autowired private PersistenceService db;

  @On(event = DocumentExtractionResultContext.CDS_NAME)
  public void onExtractionCompleted(DocumentExtractionResultContext context) {
    DocumentExtractionResult data = context.getData();
    logger.info("[bookshop] Invoice extraction completed! jobId={}", data.getJobId());

    try {
      JsonNode root = objectMapper.readTree(data.getExtractionResult());
      JsonNode headerFields = root.path("extraction").path("headerFields");

      String documentNumber = getHeaderFieldValue(headerFields, "documentNumber");
      String documentDate = getHeaderFieldValue(headerFields, "documentDate");
      String currencyCode = getHeaderFieldValue(headerFields, "currencyCode");
      BigDecimal grossAmount = getHeaderFieldNumber(headerFields, "grossAmount");
      String senderName = getHeaderFieldValue(headerFields, "senderName");

      logger.info(
          "[bookshop] Extracted: number={}, date={}, amount={} {}, sender={}",
          documentNumber, documentDate, grossAmount, currencyCode, senderName);

      // Update the invoice that is currently in EXTRACTING status.
      // In a production app you would correlate by a stored jobId; for this sample
      // we use the status as a simple correlation mechanism.
      Map<String, Object> updateData = new HashMap<>();
      updateData.put(SupplierInvoices.STATUS_CODE, STATUS_EXTRACTED);
      if (documentNumber != null) updateData.put(SupplierInvoices.INVOICE_NUMBER, documentNumber);
      if (documentDate != null) updateData.put(SupplierInvoices.INVOICE_DATE, documentDate);
      if (grossAmount != null) updateData.put(SupplierInvoices.TOTAL_AMOUNT, grossAmount);
      if (currencyCode != null) updateData.put(SupplierInvoices.CURRENCY_CODE, currencyCode);

      // Match extracted sender name to a Supplier record (case-insensitive)
      if (senderName != null) {
        findSupplierByName(senderName)
            .ifPresentOrElse(
                supplier -> {
                  updateData.put(SupplierInvoices.SUPPLIER_ID, supplier.getId());
                  logger.info(
                      "[bookshop] Matched supplier: {} ({})", supplier.getName(), supplier.getId());
                },
                () -> logger.warn("[bookshop] No supplier found for sender name: {}", senderName));
      }

      long updated =
          db.run(
                  Update.entity(SupplierInvoices_.CDS_NAME)
                      .where(i -> i.get(SupplierInvoices.STATUS_CODE).eq(STATUS_EXTRACTING))
                      .data(updateData))
              .rowCount();

      logger.info("[bookshop] Updated {} invoice(s) with extracted data", updated);

    } catch (Exception e) {
      logger.error("[bookshop] Failed to process extraction result", e);
      db.run(
          Update.entity(SupplierInvoices_.CDS_NAME)
              .where(i -> i.get(SupplierInvoices.STATUS_CODE).eq(STATUS_EXTRACTING))
              .data(Map.of(SupplierInvoices.STATUS_CODE, STATUS_FAILED)));
    }

    context.setCompleted();
  }

  private Optional<Suppliers> findSupplierByName(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    return db.run(Select.from(Suppliers_.class)
            .where(s -> s.name().eq(name)))
        .listOf(Suppliers.class)
        .stream()
        .filter(s -> s.getName() != null && normalized.equals(s.getName().toLowerCase(Locale.ROOT)))
        .findFirst();
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
