/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

public class DocumentAiRequestException extends RuntimeException {

  public final int statusCode;
  public final String responseBody;

  public DocumentAiRequestException(int statusCode, String responseBody) {
    super("DIE request failed. Status=" + statusCode + ", body=" + responseBody);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }
}
