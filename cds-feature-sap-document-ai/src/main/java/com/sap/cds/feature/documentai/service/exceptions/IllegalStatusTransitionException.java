/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.service.exceptions;

/**
 * Thrown when an attempt is made to transition an extraction job to a status that is not permitted
 * by the state machine defined in {@link
 * com.sap.cds.feature.documentai.service.utils.StatusTransitionValidator}.
 */
public class IllegalStatusTransitionException extends RuntimeException {

  /**
   * @param message description including the current and target statuses
   */
  public IllegalStatusTransitionException(String message) {
    super(message);
  }
}
