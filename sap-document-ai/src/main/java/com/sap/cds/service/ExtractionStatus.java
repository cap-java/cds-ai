/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

public enum ExtractionStatus {
  PENDING,
  SUBMITTED,
  RUNNING,
  DONE,
  FAILED;

  public static ExtractionStatus fromString(String value) {
    try {
      return ExtractionStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown ExtractionStatus value in database: '" + value + "'", e);
    }
  }
}
