package com.sap.cds;

import com.sap.cds.handlers.AttachmentEventHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AttachmentEventHandlerTest {

    @Test
    public void testHandlerCanBeInstantiated() {
        AttachmentEventHandler handler = new AttachmentEventHandler();
        assertNotNull(handler);
    }
}
