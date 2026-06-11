/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;

public class SourceDocumentException extends ServiceException {

  protected SourceDocumentException(ErrorStatus status, String message) {
    super(status, message);
  }

  public static class NotFound extends SourceDocumentException {
    public NotFound(String sourceDocumentId) {
      super(ErrorStatuses.NOT_FOUND, "SourceDocument not found: " + sourceDocumentId);
    }
  }

  public static class ContentMissing extends SourceDocumentException {
    public ContentMissing(String sourceDocumentId) {
      super(ErrorStatuses.BAD_REQUEST, "No content uploaded for: " + sourceDocumentId);
    }
  }
}
