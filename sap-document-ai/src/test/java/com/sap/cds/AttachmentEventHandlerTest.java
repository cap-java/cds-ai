/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsDefinition;
import com.sap.cds.reflect.CdsElementNotFoundException;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.services.changeset.ChangeSetContext;
import com.sap.cds.services.changeset.ChangeSetListener;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.ChangeSetContextRunner;
import com.sap.cds.services.runtime.RequestContextRunner;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerTest {

  private static final String ATTACHMENT_ID = "test-attachment-id";
  private static final String CONTENT_ID = "test-content-id";
  private static final String TENANT_ID = "test-tenant";
  private static final String TEST_ENTITY = "test.Entity";

  @Mock ExtractionService extractionService;
  @Mock PersistenceService persistenceService;
  @Mock AttachmentCreateEventContext context;
  @Mock UserInfo userInfo;
  @Mock MediaData mediaData;
  @Mock DocumentInput documentInput;
  @Mock CdsEntity attachmentEntity;
  @Mock CdsRuntime cdsRuntime;
  @Mock RequestContextRunner requestContextRunner;
  @Mock ChangeSetContextRunner changeSetContextRunner;
  @Mock ChangeSetContext changeSetContext;

  AttachmentEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new AttachmentEventHandler(extractionService, persistenceService);

    when(context.getAttachmentIds()).thenReturn(Map.of("ID", ATTACHMENT_ID));
    when(context.getContentId()).thenReturn(CONTENT_ID);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(context.getCdsRuntime()).thenReturn(cdsRuntime);

    // no draft sibling for this entity
    lenient()
        .doThrow(new CdsElementNotFoundException("no sibling", mock(CdsDefinition.class)))
        .when(attachmentEntity)
        .getTargetOf(any());

    // wire runtime to execute lambdas inline (synchronously)
    lenient().when(cdsRuntime.requestContext()).thenReturn(requestContextRunner);
    lenient()
        .doAnswer(
            inv -> {
              inv.getArgument(0, Consumer.class).accept(mock(RequestContext.class));
              return null;
            })
        .when(requestContextRunner)
        .run(any(Consumer.class));
    lenient().when(cdsRuntime.changeSetContext()).thenReturn(changeSetContextRunner);
    lenient()
        .doAnswer(
            inv -> {
              inv.getArgument(0, Consumer.class).accept(mock(ChangeSetContext.class));
              return null;
            })
        .when(changeSetContextRunner)
        .run(any(Consumer.class));
  }

  @Test
  void shouldStartExtractionAfterSuccessfulCommit() {
    mockAttachmentContext();
    mockExtractionContext();
    when(context.getChangeSetContext()).thenReturn(changeSetContext);
    InputStream content = new ByteArrayInputStream("pdf-bytes".getBytes());
    mockAttachmentLookup(createAttachmentResult(content));

    commitChangeSet();

    verify(extractionService)
        .startExtraction(eq(ATTACHMENT_ID), any(DocumentInput.class), eq(TENANT_ID));
  }

  @Test
  void shouldNotStartExtractionWhenChangeSetIsRolledBack() {
    when(context.getChangeSetContext()).thenReturn(changeSetContext);
    rollbackChangeSet();
    verify(extractionService, never()).startExtraction(any(), any(), any());
  }

  @Test
  void shouldNotStartExtractionWhenAttachmentRecordDoesNotExist() {
    Result emptyResult = mock(Result.class);
    when(emptyResult.rowCount()).thenReturn(0L);
    mockAttachmentContext();
    mockExtractionContext();
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(emptyResult);
    when(attachmentEntity.getQualifiedName()).thenReturn(TEST_ENTITY);
    when(context.getChangeSetContext()).thenReturn(changeSetContext);

    commitChangeSet();

    verify(extractionService, never()).startExtraction(any(), any(), any());
  }

  @Test
  void shouldNotStartExtractionWhenAttachmentContentIsMissing() {
    mockAttachmentContext();
    mockExtractionContext();
    mockAttachmentLookup(createAttachmentResult(null));
    when(context.getChangeSetContext()).thenReturn(changeSetContext);
    commitChangeSet();
    verify(extractionService, never()).startExtraction(any(), any(), any());
  }

  @Test
  void shouldNotRegisterChangeSetListenerWhenAttachmentIdIsMissing() {
    when(context.getAttachmentIds()).thenReturn(Map.of());
    handler.afterCreateAttachment(context);
    verify(changeSetContext, never()).register(any());
    verify(extractionService, never()).startExtraction(any(), any(), any());
  }

  private void mockAttachmentContext() {
    when(context.getData()).thenReturn(mediaData);
    when(context.getAttachmentEntity()).thenReturn(attachmentEntity);
  }

  private void mockExtractionContext() {
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(mediaData.getFileName()).thenReturn("dummy_invoice.pdf");
    when(mediaData.getMimeType()).thenReturn("application/pdf");
  }

  private ChangeSetListener captureRegisteredChangeSetListener() {
    handler.afterCreateAttachment(context);
    ArgumentCaptor<ChangeSetListener> captor = ArgumentCaptor.forClass(ChangeSetListener.class);
    verify(changeSetContext).register(captor.capture());
    return captor.getValue();
  }

  private Result createAttachmentResult(InputStream content) {
    Attachments attachment = Attachments.create();
    attachment.setContentId(CONTENT_ID);
    attachment.setContent(content);

    Result result = mock(Result.class);
    doReturn(1L).when(result).rowCount();
    doReturn(attachment).when(result).single(Attachments.class);
    doReturn(java.util.stream.Stream.of(attachment)).when(result).streamOf(Attachments.class);
    return result;
  }

  private void rollbackChangeSet() {
    ChangeSetListener listener = captureRegisteredChangeSetListener();
    listener.afterClose(false);
  }

  private void commitChangeSet() {
    ChangeSetListener listener = captureRegisteredChangeSetListener();
    listener.afterClose(true);
  }

  private void mockAttachmentLookup(Result result) {
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(result);
    when(attachmentEntity.getQualifiedName()).thenReturn(TEST_ENTITY);
  }
}
