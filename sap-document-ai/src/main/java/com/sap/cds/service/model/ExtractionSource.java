/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.model;

public record ExtractionSource(String attachmentId, String sourceDocumentId) {

  public ExtractionSource {
    if ((attachmentId == null) == (sourceDocumentId == null)) {
      throw new IllegalArgumentException(
          "Exactly one of attachmentId or sourceDocumentId must be provided");
    }
  }

  public static ExtractionSource attachment(String attachmentId) {
    return new ExtractionSource(attachmentId, null);
  }

  public static ExtractionSource sourceDocument(String sourceDocumentId) {
    return new ExtractionSource(null, sourceDocumentId);
  }
}
