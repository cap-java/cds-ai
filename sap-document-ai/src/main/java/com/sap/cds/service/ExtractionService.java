package com.sap.cds.service;

import java.io.InputStream;

public interface ExtractionService {

    void startExtraction(String attachmentId, String contentId, String tenantId, InputStream content);

}
