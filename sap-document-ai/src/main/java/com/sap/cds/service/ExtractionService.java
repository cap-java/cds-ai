/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.services.Service;

public interface ExtractionService extends Service {

  String NAME = "ExtractionService";

  String EVENT_START_EXTRACTION = "startExtraction";
}
