/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.feature.documentai.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.ql.Select;
import com.sap.cds.services.Service;
import org.junit.jupiter.api.Test;

class PluginLoadITest extends AbstractDocumentAiITest {

  @Test
  void documentAiServiceIsRegisteredInCatalog() {
    Service documentAiService =
        serviceCatalog.getService(Service.class, DocumentAiService_.CDS_NAME);
    assertThat(documentAiService).isNotNull();
  }

  @Test
  void extractionJobTableIsAccessible() {
    persistenceService.run(Select.from(ExtractionJob_.class).columns(ExtractionJob_::ID));
  }
}
