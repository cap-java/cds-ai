/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionStatus;

class StatusTransitionValidator {

  private StatusTransitionValidator() {}

  static boolean isValid(String current, String next) {
    return switch (current) {
      case ExtractionStatus.PENDING -> ExtractionStatus.PROCESSING.equals(next);
      case ExtractionStatus.PROCESSING ->
          ExtractionStatus.COMPLETED.equals(next) || ExtractionStatus.FAILED.equals(next);
      default -> false;
    };
  }
}
