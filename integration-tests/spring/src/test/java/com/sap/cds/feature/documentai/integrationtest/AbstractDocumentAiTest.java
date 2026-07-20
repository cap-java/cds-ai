/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.integrationtest;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.handlers.ExtractionPollingHandler;
import com.sap.cds.feature.documentai.service.ExtractionService;
import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.feature.documentai.service.model.DocumentInput;
import com.sap.cds.feature.documentai.service.model.ExtractionData;
import com.sap.cds.ql.Delete;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
abstract class AbstractDocumentAiTest {

  @Autowired ServiceCatalog serviceCatalog;
  @Autowired PersistenceService persistenceService;
  @Autowired CdsRuntime cdsRuntime;

  @BeforeEach
  @AfterEach
  void resetTestData() {
    persistenceService.run(Delete.from(ExtractionJob_.class));
  }

  // Executes a single polling cycle using a test DIE client that returns results supplied by the
  // caller.
  void runPollCycle(
      ExtractionService extractionService, Function<String, ExtractionData> jobResultFn) {
    ExtractionPollingHandler handler =
        new ExtractionPollingHandler(
            persistenceService,
            extractionService,
            pollingClient(jobResultFn),
            null,
            cdsRuntime,
            Duration.ZERO,
            false);

    OutboxMessageEventContext ctx =
        EventContext.create(OutboxMessageEventContext.class, ExtractionPollingHandler.POLL_EVENT);
    handler.pollExtractionJobs(ctx);
  }

  private DocumentAiClient pollingClient(Function<String, ExtractionData> jobResultFn) {
    return new DocumentAiClient() {
      @Override
      public String submitDocument(DocumentInput input, String tenantId) {
        throw new UnsupportedOperationException("Submission is not supported by this test client.");
      }

      @Override
      public ExtractionData getJobResult(String dieJobId, String tenantId) {
        return jobResultFn.apply(dieJobId);
      }
    };
  }
}
