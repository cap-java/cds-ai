/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.integrationtest;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
@ServiceName(
    value = DocumentAiService_.CDS_NAME,
    type = com.sap.cds.services.cds.ApplicationService.class)
class ExtractionResultCaptureHandler implements EventHandler {

  private final List<DocumentExtractionResult> captured = new ArrayList<>();

  @After(event = DocumentExtractionResultContext.CDS_NAME)
  public void onExtractionResult(DocumentExtractionResultContext context) {
    captured.add(context.getData());
  }

  public List<DocumentExtractionResult> getCaptured() {
    return List.copyOf(captured);
  }

  public void reset() {
    captured.clear();
  }
}
