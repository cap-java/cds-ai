/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.service.model.DocumentInput;

/**
 * Abstraction over the Document AI (DIE) submission layer.
 *
 * <p>Decouples {@link com.sap.cds.service.ExtractionServiceImpl} from the concrete HTTP client so
 * the service can remain operational (returning {@code PENDING} jobs) when no DIE binding is
 * configured.
 */
public interface DocumentAiProcessingService {

  /**
   * Returns {@code true} if a DIE binding is available and document submission is possible.
   *
   * @return {@code true} when the underlying client is initialised, {@code false} otherwise
   */
  boolean isAvailable();

  /**
   * Submits a document to the DIE service and returns the DIE-assigned job ID.
   *
   * @param jobId the internal extraction job ID, used for correlation in logs and exceptions
   * @param documentInput the document content and metadata to submit
   * @return the job ID assigned by the DIE service
   * @throws com.sap.cds.service.exceptions.DocumentAiException if submission or response parsing
   *     fails
   */
  String processDocument(String jobId, DocumentInput documentInput);
}
