/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;
import com.sap.cds.handlers.DocumentSubmissionHandler;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSubmissionHandlerTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String FILE_NAME = "invoice.pdf";
  private static final String MIME_TYPE = "application/pdf";

  @Mock ExtractionService extractionService;
  @Mock DocumentExtractionContext eventContext;
  @Mock UserInfo userInfo;

  DocumentSubmissionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DocumentSubmissionHandler(extractionService);
  }

  private DocumentExtraction createEvent() {
    DocumentExtraction event = DocumentExtraction.create();
    event.setFileName(FILE_NAME);
    event.setMimeType(MIME_TYPE);
    event.setContent(new ByteArrayInputStream("pdf-bytes".getBytes()));
    return event;
  }

  @Test
  void onDocumentExtraction_triggersExtraction() {
    when(eventContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(eventContext.getData()).thenReturn(createEvent());
    when(extractionService.triggerExtraction(any(), any(), any(), any(), any()))
        .thenReturn(
            new ExtractionResult("job-123", ExtractionResult.Status.SUCCESS, "dai-job-456"));

    handler.onDocumentExtraction(eventContext);

    verify(extractionService)
        .triggerExtraction(
            eq(FILE_NAME), eq(MIME_TYPE), any(InputStream.class), any(), eq(TENANT_ID));
  }

  @Test
  void onDocumentExtraction_logsPendingWhenServiceUnavailable() {
    when(eventContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(eventContext.getData()).thenReturn(createEvent());
    when(extractionService.triggerExtraction(any(), any(), any(), any(), any()))
        .thenReturn(new ExtractionResult(null, ExtractionResult.Status.PENDING, null));

    handler.onDocumentExtraction(eventContext);

    verify(extractionService).triggerExtraction(any(), any(), any(), any(), any());
  }

  @Test
  void onDocumentExtraction_logsFailedWhenExtractionFails() {
    when(eventContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(eventContext.getData()).thenReturn(createEvent());
    when(extractionService.triggerExtraction(any(), any(), any(), any(), any()))
        .thenReturn(new ExtractionResult("job-123", ExtractionResult.Status.FAILED, null));

    handler.onDocumentExtraction(eventContext);

    verify(extractionService).triggerExtraction(any(), any(), any(), any(), any());
  }
}
