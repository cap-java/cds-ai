/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.model;

import java.io.InputStream;

public record DocumentInput(String fileName, String mimeType, InputStream content) {}
