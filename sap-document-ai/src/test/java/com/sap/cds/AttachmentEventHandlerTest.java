/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerTest {

  @Mock ExtractionService extractionService;

  @Test
  void afterCreateAttachmentTriggersOrchestrationWithTenant() {
    // Arrange
    AttachmentEventHandler handler = new AttachmentEventHandler(extractionService);
    AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
    UserInfo userInfo = mock(UserInfo.class);
    MediaData mediaData = mock(MediaData.class);
    InputStream content = new ByteArrayInputStream("test".getBytes());
    when(context.getAttachmentIds()).thenReturn(Map.of("ID", "test-attachment-id"));
    when(context.getContentId()).thenReturn("test-content-id");
    when(context.getUserInfo()).thenReturn(userInfo);
    when(context.getData()).thenReturn(mediaData);
    when(mediaData.getContent()).thenReturn(content);
    when(userInfo.getTenant()).thenReturn("test-tenant");

    // Act
    handler.afterCreateAttachment(context);

    // Assert
    verify(extractionService)
        .startExtraction("test-attachment-id", "test-content-id", "test-tenant", content);
  }

  @Test
  void afterCreateAttachmentSkipsExtractionWhenAttachmentIdIsNull() {
    // Arrange
    AttachmentEventHandler handler = new AttachmentEventHandler(extractionService);
    AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(context.getAttachmentIds()).thenReturn(Map.of());
    when(context.getUserInfo()).thenReturn(userInfo);

    // Act
    handler.afterCreateAttachment(context);

    // Assert
    verify(extractionService, never()).startExtraction(any(), any(), any(), any());
  }
}
