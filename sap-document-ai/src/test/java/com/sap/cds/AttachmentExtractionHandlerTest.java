package com.sap.cds;

import com.sap.cds.handlers.AttachmentExtractionHandler;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AttachmentExtractionHandlerTest extends TestCase {

    public AttachmentExtractionHandlerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(AttachmentExtractionHandlerTest.class);
    }

    public void testHandlerCanBeInstantiated() {
        AttachmentExtractionHandler handler = new AttachmentExtractionHandler();
        assertNotNull(handler);
    }
}
