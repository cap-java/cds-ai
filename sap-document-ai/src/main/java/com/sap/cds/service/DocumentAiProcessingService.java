/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-notifications contributors.
*/
package com.sap.cds.service;

import java.io.InputStream;

public interface DocumentAiProcessingService {

  void processDocument(String jobId, InputStream content);
}
