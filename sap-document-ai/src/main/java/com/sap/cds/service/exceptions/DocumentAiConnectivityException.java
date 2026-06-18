/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

import java.io.IOException;

public class DocumentAiConnectivityException extends RuntimeException {

  public DocumentAiConnectivityException(String url, IOException cause) {
    super("Failed to connect to DIE at " + url, cause);
  }
}
