/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.model;

public record ExtractionResult(String internalJobId, Status status, String documentAiJobId) {

  public enum Status {
    SUCCESS,
    PENDING,
    FAILED
  }
}
