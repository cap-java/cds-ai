/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service;

/**
 * Lifecycle statuses for a document extraction job.
 *
 * <p>The allowed transitions are enforced by {@link
 * com.sap.cds.feature.documentai.service.utils.StatusTransitionValidator}:
 *
 * <pre>
 *   PENDING → SUBMITTED | FAILED
 *   SUBMITTED → RUNNING | DONE | FAILED
 *   RUNNING → DONE | FAILED
 * </pre>
 */
public enum ExtractionStatus {
  /** Job created but not yet submitted to DIE (e.g. DIE service unavailable at submit time). */
  PENDING,
  /** Document submitted to DIE; awaiting processing. */
  SUBMITTED,
  /** DIE has started processing the document. */
  RUNNING,
  /** DIE processing finished successfully; extraction result is available. */
  DONE,
  /** Processing failed at any stage. */
  FAILED;

  /** Returns {@code true} if this status is terminal — no further transitions are permitted. */
  public boolean isTerminal() {
    return this == DONE || this == FAILED;
  }

  /**
   * Converts a persisted string value back to an {@link ExtractionStatus}.
   *
   * @param value the raw status string stored in the database
   * @return the matching {@link ExtractionStatus}
   * @throws IllegalArgumentException if {@code value} does not match any known status
   */
  public static ExtractionStatus fromString(String value) {
    try {
      return ExtractionStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown ExtractionStatus value in database: '" + value + "'", e);
    }
  }
}
