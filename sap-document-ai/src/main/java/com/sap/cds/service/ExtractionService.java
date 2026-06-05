/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import java.io.InputStream;

public interface ExtractionService {

  void startExtraction(String attachmentId, String contentId, String tenantId, InputStream content);
}
