/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.model;

import java.io.InputStream;

/**
 * Immutable value object carrying the document data and metadata needed for a DIE submission.
 *
 * @param fileName the original file name sent to DIE
 * @param mimeType the MIME type of the document (e.g. {@code application/pdf})
 * @param content the document byte stream; consumed exactly once during submission
 * @param options JSON options string forwarded to DIE; {@code null} is treated as empty options
 */
public record DocumentInput(
    String fileName, String mimeType, InputStream content, String options) {}
