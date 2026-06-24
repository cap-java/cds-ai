/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

import java.io.IOException;

public class DocumentAiException extends RuntimeException {

  protected DocumentAiException(String message, Throwable cause) {
    super(message, cause);
  }

  protected DocumentAiException(String message) {
    super(message);
  }

  public static class Connectivity extends DocumentAiException {
    public Connectivity(String url, IOException cause) {
      super("Failed to connect to DIE at " + url, cause);
    }
  }

  public static class Request extends DocumentAiException {
    private final int statusCode;
    private final String responseBody;

    public Request(int statusCode, String responseBody) {
      super("DIE request failed. Status=" + statusCode + ", body=" + responseBody);
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getResponseBody() {
      return responseBody;
    }
  }

  public static class Processing extends DocumentAiException {
    public Processing(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
