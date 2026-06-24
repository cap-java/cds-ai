/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

public class ConcurrentJobUpdateException extends RuntimeException {
  public ConcurrentJobUpdateException(String message) {
    super(message);
  }
}
