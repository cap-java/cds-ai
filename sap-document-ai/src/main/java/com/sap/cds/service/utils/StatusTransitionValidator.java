/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.utils;

import static com.sap.cds.service.ExtractionStatus.*;

import com.sap.cds.service.ExtractionStatus;

/**
 * Utility class that enforces the allowed state-machine transitions for {@link ExtractionStatus}.
 *
 * <p>Permitted transitions:
 *
 * <pre>
 *   PENDING   → SUBMITTED | FAILED
 *   SUBMITTED → RUNNING | DONE | FAILED
 *   RUNNING   → DONE | FAILED
 *   DONE / FAILED → (terminal, no further transitions)
 * </pre>
 *
 * Same-status transitions are always considered valid (idempotent updates).
 */
public class StatusTransitionValidator {

  private StatusTransitionValidator() {}

  /**
   * Returns {@code true} if transitioning from {@code current} to {@code next} is permitted.
   *
   * @param current the status the job is currently in
   * @param next the desired target status
   * @return {@code true} if the transition is allowed, {@code false} otherwise
   */
  public static boolean isValid(ExtractionStatus current, ExtractionStatus next) {
    if (current.equals(next)) return true; // idempotent

    return switch (current) {
      case PENDING -> SUBMITTED.equals(next) || FAILED.equals(next);
      case SUBMITTED -> RUNNING.equals(next) || DONE.equals(next) || FAILED.equals(next);
      case RUNNING -> DONE.equals(next) || FAILED.equals(next);
      default -> false;
    };
  }
}
