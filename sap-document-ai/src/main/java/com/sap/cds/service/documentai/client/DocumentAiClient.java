/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import com.sap.cds.service.model.DocumentInput;
import com.sap.cds.service.model.ExtractionData;

/**
 * Low-level HTTP client interface for the Document Information Extraction (DIE) service.
 *
 * <p>Abstracts the REST calls so that higher-level services and handlers are not coupled to the
 * Apache HTTP client or SAP Cloud SDK destination APIs.
 */
public interface DocumentAiClient {

  /**
   * Submits a document to the DIE service for extraction.
   *
   * @param documentInput the document content and metadata
   * @return the DIE-assigned job ID for the submitted document
   * @throws com.sap.cds.service.exceptions.DocumentAiException if the HTTP call fails or the
   *     response cannot be parsed
   */
  String submitDocument(DocumentInput documentInput);

  /**
   * Polls the DIE service for the current status and result of a previously submitted job.
   *
   * @param dieJobId the job ID returned by {@link #submitDocument}
   * @return an {@link ExtractionData} containing the DIE status and the raw result JSON
   * @throws com.sap.cds.service.exceptions.DocumentAiException if the HTTP call fails or the
   *     response cannot be parsed
   */
  ExtractionData getJobResult(String dieJobId);
}
