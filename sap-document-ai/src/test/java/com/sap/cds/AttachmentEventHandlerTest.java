/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.StartExtractionEventContext;
import com.sap.cds.services.request.UserInfo;
import java.util.Map;
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
  private static final String FILE_NAME = "dummy_invoice.pdf";
  private static final String MIME_TYPE = "application/pdf";
  private static final String ENTITY_NAME = "test.Entity";

  @Mock ExtractionService extractionService;
  @Mock AttachmentCreateEventContext context;
  @Mock UserInfo userInfo;
  @Mock MediaData mediaData;
  @Mock CdsEntity attachmentEntity;

  AttachmentEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new AttachmentEventHandler(extractionService);
    when(context.getAttachmentIds()).thenReturn(Map.of(Attachments.ID, ATTACHMENT_ID));
  }

  @Test
  void shouldEmitStartExtractionEvent() {
    when(context.getContentId()).thenReturn(CONTENT_ID);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(context.getData()).thenReturn(mediaData);
    when(context.getAttachmentEntity()).thenReturn(attachmentEntity);
    when(attachmentEntity.getQualifiedName()).thenReturn(ENTITY_NAME);
    when(userInfo.getTenant()).thenReturn(TENANT_ID);
    when(mediaData.getFileName()).thenReturn(FILE_NAME);
    when(mediaData.getMimeType()).thenReturn(MIME_TYPE);

    handler.afterCreateAttachment(context);

    ArgumentCaptor<StartExtractionEventContext> captor =
        ArgumentCaptor.forClass(StartExtractionEventContext.class);
    verify(extractionService).emit(captor.capture());

    StartExtractionEventContext emitted = captor.getValue();
    assertThat(emitted.getAttachmentId()).isEqualTo(ATTACHMENT_ID);
    assertThat(emitted.getContentId()).isEqualTo(CONTENT_ID);
    assertThat(emitted.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(emitted.getFileName()).isEqualTo(FILE_NAME);
    assertThat(emitted.getMimeType()).isEqualTo(MIME_TYPE);
    assertThat(emitted.getAttachmentEntityName()).isEqualTo(ENTITY_NAME);
  }

  @Test
  void shouldNotEmitWhenAttachmentIdIsMissing() {
    when(context.getAttachmentIds()).thenReturn(Map.of());

    handler.afterCreateAttachment(context);

    verify(extractionService, never()).emit(any());
  }
}
