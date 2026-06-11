/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.SourceDocument;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.StartExtractionContext;
import com.sap.cds.handlers.DocumentSubmissionHandler;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.model.ExtractionResult;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSubmissionHandlerTest {

  private static final String SOURCE_DOCUMENT_ID = "src-doc-123";
  private static final String TENANT_ID = "tenant-1";
  private static final String FILE_NAME = "invoice.pdf";
  private static final String MIME_TYPE = "application/pdf";

  @Mock ExtractionService extractionService;
  @Mock DocumentAiService documentAiService;
  @Mock CdsUpdateEventContext context;
  @Mock StartExtractionContext startExtractionContext;
  @Mock UserInfo userInfo;
  @Mock Result selectResult;
  @Mock Result updateResult;
  @Mock CqnUpdate cqnUpdate;

  DocumentSubmissionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DocumentSubmissionHandler(extractionService, documentAiService);
  }

  @Test
  void onStartExtraction_returnsExtractionJobOnSuccess() {
    when(startExtractionContext.getSourceDocumentId()).thenReturn(SOURCE_DOCUMENT_ID);
    when(startExtractionContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);

    SourceDocument doc =
        createDocument(SOURCE_DOCUMENT_ID, new ByteArrayInputStream("pdf-bytes".getBytes()));
    when(selectResult.first(SourceDocument.class)).thenReturn(java.util.Optional.of(doc));

    ExtractionResult extraction =
        new ExtractionResult("job-123", ExtractionResult.Status.SUCCESS, "dai-job-456");
    when(extractionService.triggerExtraction(
            eq(SOURCE_DOCUMENT_ID),
            eq(FILE_NAME),
            eq(MIME_TYPE),
            any(InputStream.class),
            eq(TENANT_ID)))
        .thenReturn(extraction);

    handler.onStartExtraction(startExtractionContext);

    verify(startExtractionContext).setResult(argThat(job -> "job-123".equals(job.getId())));
  }

  @Test
  void onStartExtraction_throwsNotFoundWhenDocumentNotFound() {
    when(startExtractionContext.getSourceDocumentId()).thenReturn(SOURCE_DOCUMENT_ID);
    when(startExtractionContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);
    when(selectResult.first(SourceDocument.class)).thenReturn(java.util.Optional.empty());

    assertThrows(
        com.sap.cds.service.exceptions.SourceDocumentException.NotFound.class,
        () -> handler.onStartExtraction(startExtractionContext));
  }

  @Test
  void onStartExtraction_throwsContentMissingWhenContentIsNull() {
    when(startExtractionContext.getSourceDocumentId()).thenReturn(SOURCE_DOCUMENT_ID);
    when(startExtractionContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);

    SourceDocument doc = createDocument(SOURCE_DOCUMENT_ID, null);
    when(selectResult.first(SourceDocument.class)).thenReturn(java.util.Optional.of(doc));

    assertThrows(
        com.sap.cds.service.exceptions.SourceDocumentException.ContentMissing.class,
        () -> handler.onStartExtraction(startExtractionContext));
  }

  @Test
  void afterContentUpload_triggersExtraction() {
    when(cqnUpdate.entries()).thenReturn(List.of(entryWithContent(SOURCE_DOCUMENT_ID)));
    when(context.getCqn()).thenReturn(cqnUpdate);
    mockUserInfo();

    SourceDocument idOnly = SourceDocument.create();
    idOnly.setId(SOURCE_DOCUMENT_ID);
    when(context.getResult()).thenReturn(updateResult);
    when(updateResult.listOf(SourceDocument.class)).thenReturn(List.of(idOnly));

    SourceDocument doc =
        createDocument(SOURCE_DOCUMENT_ID, new ByteArrayInputStream("pdf-bytes".getBytes()));
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);
    when(selectResult.listOf(SourceDocument.class)).thenReturn(List.of(doc));

    handler.afterContentUpload(context);

    verify(extractionService)
        .triggerExtraction(
            eq(SOURCE_DOCUMENT_ID),
            eq(FILE_NAME),
            eq(MIME_TYPE),
            any(InputStream.class),
            eq(TENANT_ID));
  }

  @Test
  void afterContentUpload_skipsWhenNoContentInUpdate() {
    when(cqnUpdate.entries()).thenReturn(List.of(Map.of(SourceDocument.ID, SOURCE_DOCUMENT_ID)));
    when(context.getCqn()).thenReturn(cqnUpdate);

    handler.afterContentUpload(context);

    verify(extractionService, never()).triggerExtraction(any(), any(), any(), any(), any());
    verify(documentAiService, never()).run(any(CqnSelect.class));
  }

  @Test
  void afterContentUpload_skipsWhenIdMissing() {
    when(cqnUpdate.entries()).thenReturn(List.of(Map.of()));
    when(context.getCqn()).thenReturn(cqnUpdate);

    handler.afterContentUpload(context);

    verify(extractionService, never()).triggerExtraction(any(), any(), any(), any(), any());
    verify(documentAiService, never()).run(any(CqnSelect.class));
  }

  @Test
  void afterContentUpload_skipsWhenResultReturnsNoIds() {
    Map<String, Object> entry = new HashMap<>();
    entry.put(SourceDocument.CONTENT, new ByteArrayInputStream("pdf-bytes".getBytes()));
    when(cqnUpdate.entries()).thenReturn(List.of(entry));
    when(context.getCqn()).thenReturn(cqnUpdate);

    when(context.getResult()).thenReturn(updateResult);
    when(updateResult.listOf(SourceDocument.class)).thenReturn(List.of());

    handler.afterContentUpload(context);

    verify(extractionService, never()).triggerExtraction(any(), any(), any(), any(), any());
    verify(documentAiService, never()).run(any(CqnSelect.class));
  }

  @Test
  void afterContentUpload_skipsWhenDocumentNotFound() {
    when(cqnUpdate.entries()).thenReturn(List.of(entryWithContent(SOURCE_DOCUMENT_ID)));
    when(context.getCqn()).thenReturn(cqnUpdate);
    mockUserInfo();

    SourceDocument idOnly = SourceDocument.create();
    idOnly.setId(SOURCE_DOCUMENT_ID);
    when(context.getResult()).thenReturn(updateResult);
    when(updateResult.listOf(SourceDocument.class)).thenReturn(List.of(idOnly));

    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);
    when(selectResult.listOf(SourceDocument.class)).thenReturn(List.of());

    handler.afterContentUpload(context);

    verify(extractionService, never()).triggerExtraction(any(), any(), any(), any(), any());
  }

  @Test
  void afterContentUpload_skipsWhenContentIsNull() {
    when(cqnUpdate.entries()).thenReturn(List.of(entryWithContent(SOURCE_DOCUMENT_ID)));
    when(context.getCqn()).thenReturn(cqnUpdate);
    mockUserInfo();

    SourceDocument idOnly = SourceDocument.create();
    idOnly.setId(SOURCE_DOCUMENT_ID);
    when(context.getResult()).thenReturn(updateResult);
    when(updateResult.listOf(SourceDocument.class)).thenReturn(List.of(idOnly));

    SourceDocument doc = createDocument(SOURCE_DOCUMENT_ID, null);
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);
    when(selectResult.listOf(SourceDocument.class)).thenReturn(List.of(doc));

    handler.afterContentUpload(context);

    verify(extractionService, never()).triggerExtraction(any(), any(), any(), any(), any());
  }

  @Test
  void afterContentUpload_continuesAndCollectsFailedIds() {
    when(cqnUpdate.entries())
        .thenReturn(List.of(entryWithContent("src-1"), entryWithContent("src-2")));
    when(context.getCqn()).thenReturn(cqnUpdate);
    mockUserInfo();

    SourceDocument id1 = SourceDocument.create();
    id1.setId("src-1");
    SourceDocument id2 = SourceDocument.create();
    id2.setId("src-2");
    when(context.getResult()).thenReturn(updateResult);
    when(updateResult.listOf(SourceDocument.class)).thenReturn(List.of(id1, id2));

    SourceDocument doc1 = createDocument("src-1", new ByteArrayInputStream("pdf-bytes".getBytes()));
    SourceDocument doc2 = createDocument("src-2", new ByteArrayInputStream("pdf-bytes".getBytes()));
    when(documentAiService.run(any(CqnSelect.class))).thenReturn(selectResult);
    when(selectResult.listOf(SourceDocument.class)).thenReturn(List.of(doc1, doc2));
    doThrow(new RuntimeException("DIE unavailable"))
        .when(extractionService)
        .triggerExtraction(eq("src-1"), any(), any(), any(), any());

    handler.afterContentUpload(context);

    verify(extractionService)
        .triggerExtraction(
            eq("src-1"), eq(FILE_NAME), eq(MIME_TYPE), any(InputStream.class), eq(TENANT_ID));
    verify(extractionService)
        .triggerExtraction(
            eq("src-2"), eq(FILE_NAME), eq(MIME_TYPE), any(InputStream.class), eq(TENANT_ID));
  }

  private void mockUserInfo() {
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
  }

  private Map<String, Object> entryWithContent(String id) {
    Map<String, Object> entry = new HashMap<>();
    entry.put(SourceDocument.ID, id);
    entry.put(SourceDocument.CONTENT, new ByteArrayInputStream("pdf-bytes".getBytes()));
    return entry;
  }

  private SourceDocument createDocument(String id, InputStream content) {
    SourceDocument doc = SourceDocument.create();
    doc.setId(id);
    doc.setFileName(FILE_NAME);
    doc.setMimeType(MIME_TYPE);
    doc.setContent(content);
    return doc;
  }
}
