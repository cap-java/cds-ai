/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service.documentai.client;

import java.io.InputStream;

public interface DocumentAiClient {
  String submitDocument(InputStream content);
}
