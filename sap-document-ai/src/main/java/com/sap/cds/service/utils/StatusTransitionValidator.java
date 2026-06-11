/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.utils;

import static com.sap.cds.service.ExtractionStatus.*;

import com.sap.cds.service.ExtractionStatus;

public class StatusTransitionValidator {

  private StatusTransitionValidator() {}

  public static boolean isValid(ExtractionStatus current, ExtractionStatus next) {
    if (current.equals(next)) return true; // idempotent

    return switch (current) {
      case PENDING -> SUBMITTED.equals(next) || FAILED.equals(next);
      case SUBMITTED -> PROCESSING.equals(next) || FAILED.equals(next);
      case PROCESSING -> COMPLETED.equals(next) || FAILED.equals(next);
      default -> false;
    };
  }
}
