/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.model.DocumentInput;

public interface DocumentAiProcessingService {

  boolean isAvailable();

  String processDocument(String jobId, DocumentInput documentInput);
}
