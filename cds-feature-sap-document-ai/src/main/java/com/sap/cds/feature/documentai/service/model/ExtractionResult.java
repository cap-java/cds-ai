/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service.model;

/**
 * Immutable value object returned by {@link
 * com.sap.cds.feature.documentai.service.ExtractionService#triggerExtraction} to convey the
 * immediate outcome of a submission attempt.
 *
 * @param internalJobId the plugin-managed job ID created in the database
 * @param status the outcome of the submission attempt (see {@link Status})
 * @param documentAiJobId the DIE-assigned job ID, or {@code null} if the document was not yet
 *     submitted (status {@code PENDING} or {@code FAILED})
 */
public record ExtractionResult(String internalJobId, Status status, String documentAiJobId) {

  /** Immediate outcome of a {@code triggerExtraction} call. */
  public enum Status {
    /** Document submitted to DIE successfully. */
    SUCCESS,
    /** DIE was unavailable; job is queued for retry via the polling scheduler. */
    PENDING,
    /** Submission failed with an unrecoverable error. */
    FAILED
  }
}
