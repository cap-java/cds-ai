/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import java.io.InputStream;

public interface DocumentAiProcessingService {

  void processDocument(String jobId, InputStream content);
}
