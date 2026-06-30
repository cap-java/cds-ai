/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.exceptions;

/**
 * Base exception for all errors originating from interaction with the Document AI (DIE) service.
 *
 * <p>Concrete failure modes are represented by the three nested subclasses:
 *
 * <ul>
 *   <li>{@link Connectivity} — network-level failures (timeouts, DNS, etc.)
 *   <li>{@link Request} — non-2xx HTTP responses from DIE
 *   <li>{@link Processing} — unexpected or malformed response payloads
 * </ul>
 */
public class DocumentAiException extends RuntimeException {

  /**
   * @param message human-readable description of the failure
   * @param cause the underlying exception, or {@code null}
   */
  protected DocumentAiException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * @param message human-readable description of the failure
   */
  protected DocumentAiException(String message) {
    super(message);
  }

  /** Raised when the HTTP connection to the DIE service cannot be established. */
  public static class Connectivity extends DocumentAiException {

    /**
     * @param url the URL that was being contacted when the error occurred
     * @param cause the underlying I/O exception
     */
    public Connectivity(String url, Exception cause) {
      super("Failed to connect to DIE at " + url, cause);
    }
  }

  /** Raised when DIE returns a non-2xx HTTP response. */
  public static class Request extends DocumentAiException {
    private final int statusCode;
    private final String responseBody;

    /**
     * @param statusCode the HTTP status code returned by DIE
     * @param responseBody the raw response body, included for diagnostics
     */
    public Request(int statusCode, String responseBody) {
      super("DIE request failed. Status=" + statusCode + ", body=" + responseBody);
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    /**
     * @return the HTTP status code returned by DIE
     */
    public int getStatusCode() {
      return statusCode;
    }

    /**
     * @return the raw response body returned by DIE
     */
    public String getResponseBody() {
      return responseBody;
    }
  }

  /** Raised when a DIE response cannot be parsed or is missing required fields. */
  public static class Processing extends DocumentAiException {

    /**
     * @param message description of the parsing failure
     * @param cause the underlying parse exception, or {@code null}
     */
    public Processing(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
