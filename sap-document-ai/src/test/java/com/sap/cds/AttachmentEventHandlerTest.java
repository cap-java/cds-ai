package com.sap.cds;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.orchestrator.ExtractionOrchestrator;
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
    ExtractionOrchestrator extractionOrchestrator;

    @Test
    void afterCreateAttachment_triggersOrchestration() {
        AttachmentEventHandler handler = new AttachmentEventHandler(extractionOrchestrator);
        assertNotNull(handler);
        AttachmentCreateEventContext context = mock(AttachmentCreateEventContext.class);
        when(context.getAttachmentIds()).thenReturn(Map.of("ID", "test-attachment-id"));
        handler.afterCreateAttachment(context);
        verify(extractionOrchestrator).startExtraction("test-attachment-id");
    }

}
