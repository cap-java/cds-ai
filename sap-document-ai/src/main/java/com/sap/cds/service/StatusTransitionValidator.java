/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import static com.sap.cds.service.ExtractionStatus.*;

class StatusTransitionValidator {

  private StatusTransitionValidator() {}

  static boolean isValid(ExtractionStatus current, ExtractionStatus next) {
    if (current.equals(next)) return true; // idempotent

    return switch (current) {
      case PENDING -> PROCESSING.equals(next);
      case PROCESSING -> COMPLETED.equals(next) || FAILED.equals(next);
      default -> false;
    };
  }
}
