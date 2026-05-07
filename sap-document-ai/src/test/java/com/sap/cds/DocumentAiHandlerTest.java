package com.sap.cds;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class DocumentAiHandlerTest extends TestCase {

    public DocumentAiHandlerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(DocumentAiHandlerTest.class);
    }

    public void testHandlerCanBeInstantiated() {
        DocumentAiHandler handler = new DocumentAiHandler();
        assertNotNull(handler);
    }
}
