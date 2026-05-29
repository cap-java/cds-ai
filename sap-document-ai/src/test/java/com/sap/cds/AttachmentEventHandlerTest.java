package com.sap.cds;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.services.request.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerTest {

    @Mock
    ExtractionService extractionService;

    @Test
    void afterCreateAttachment_triggersOrchestrationWithTenant() {
        // Arrange
        AttachmentEventHandler handler = new AttachmentEventHandler(extractionService);
        AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
        UserInfo userInfo = mock(UserInfo.class);
        when(context.getAttachmentIds()).thenReturn(Map.of("ID", "test-attachment-id"));
        when(context.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getTenant()).thenReturn("test-tenant");

        // Act
        handler.afterCreateAttachment(context);

        // Assert
        verify(extractionService).startExtraction("test-attachment-id", "test-tenant");
    }

    @Test
    void afterCreateAttachment_skipsExtractionWhenAttachmentIdIsNull() {
        // Arrange
        AttachmentEventHandler handler = new AttachmentEventHandler(extractionService);
        AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
        UserInfo userInfo = mock(UserInfo.class);
        when(context.getAttachmentIds()).thenReturn(Map.of());
        when(context.getUserInfo()).thenReturn(userInfo);

        // Act
        handler.afterCreateAttachment(context);

        // Assert
        verify(extractionService, never()).startExtraction(any(), any());
    }

}
