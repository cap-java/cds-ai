/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service.exceptions;

/**
 * Thrown when an optimistic-lock update of an extraction job detects that another thread or process
 * has already advanced the job's status.
 *
 * <p>The update query in {@code ExtractionServiceImpl} uses a {@code WHERE status = currentStatus}
 * predicate; zero rows affected means a concurrent writer got there first, and this exception is
 * raised instead of silently overwriting that newer state.
 */
public class ConcurrentJobUpdateException extends RuntimeException {

  /**
   * @param message description including the job ID and the expected status that was not found
   */
  public ConcurrentJobUpdateException(String message) {
    super(message);
  }
}
