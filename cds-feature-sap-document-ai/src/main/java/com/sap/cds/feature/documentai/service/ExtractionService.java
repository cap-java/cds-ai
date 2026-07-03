/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service;

import com.sap.cds.feature.documentai.service.exceptions.IllegalStatusTransitionException;
import com.sap.cds.feature.documentai.service.model.ExtractionResult;
import com.sap.cds.services.Service;
import java.io.InputStream;

/**
 * CDS service interface for managing document extraction jobs.
 *
 * <p>Handles the lifecycle of an extraction job from initial submission through status updates.
 * Implementations are expected to persist job state and coordinate with the Document AI processing
 * layer.
 */
public interface ExtractionService extends Service {

  String NAME = "ExtractionService";

  /**
   * Triggers a new document extraction job.
   *
   * <p>Creates a job record in {@code PENDING} status, then attempts to submit the document to the
   * Document AI service. If the service is unavailable, the job remains {@code PENDING} for later
   * retry. On successful submission the job transitions to {@code SUBMITTED} and polling is
   * scheduled.
   *
   * @param fileName the original file name, forwarded to the DIE service
   * @param mimeType the MIME type of the document content
   * @param content the document byte stream
   * @param options JSON options string passed to the DIE service; may be {@code null}
   * @param tenantId the tenant under which the job is created
   * @return an {@link ExtractionResult} describing the outcome and the internal job ID
   * @throws IllegalStatusTransitionException if the resulting status update violates the allowed
   *     state machine
   */
  ExtractionResult triggerExtraction(
      String fileName, String mimeType, InputStream content, String options, String tenantId)
      throws IllegalStatusTransitionException;

  /**
   * Updates the status of an existing extraction job after a poll result from DIE.
   *
   * @param jobId the internal job ID
   * @param status the new {@link ExtractionStatus} to apply
   * @param dieJobId the DIE-side job ID to persist alongside the status update; may be {@code null}
   * @param extractionResult the raw JSON result returned by DIE; only non-{@code null} when status
   *     is {@code DONE}
   * @throws IllegalStatusTransitionException if the transition from the current status to {@code
   *     status} is not permitted
   */
  void updateExtractionResult(
      String jobId, ExtractionStatus status, String dieJobId, String extractionResult)
      throws IllegalStatusTransitionException;
}
