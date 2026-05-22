package com.sap.cds.service;

import java.io.InputStream;

public interface DocumentAiProcessingService {

    void processDocument(String jobId, InputStream content);

}
