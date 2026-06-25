/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package customer.bookshop.handlers;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ServiceName("sap.document.ai.DocumentAiService")
public class DocumentExtractionResultHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DocumentExtractionResultHandler.class);

  @On(event = DocumentExtractionResultContext.CDS_NAME)
  public void onExtractionCompleted(DocumentExtractionResultContext context) {
    DocumentExtractionResult data = context.getData();
    logger.info("[bookshop] Extraction completed & results are ready! jobId={} result ={}", data.getJobId(), data.getExtractionResult());
    context.setCompleted();
  }
}
