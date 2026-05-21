package com.sap.cds;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.orchestrator.ExtractionService;
import com.sap.cds.services.request.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentEventHandlerTest {

    @Mock
    ExtractionService extractionService;

    @Test
    void afterCreateAttachment_triggersOrchestrationWithTenant() {
        AttachmentEventHandler handler = new AttachmentEventHandler(extractionService);
        assertNotNull(handler);
        AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
        UserInfo userInfo = mock(UserInfo.class);
        when(context.getAttachmentIds()).thenReturn(Map.of("ID", "test-attachment-id"));
        when(context.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getTenant()).thenReturn("test-tenant");
        handler.afterCreateAttachment(context);
        verify(extractionService).startExtraction("test-attachment-id", "test-tenant");
    }

}
