/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Struct;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAiSetupHandlerTest {

  @Mock PersistenceService persistenceService;
  @Mock SubscribeEventContext subscribeContext;
  @Mock UnsubscribeEventContext unsubscribeContext;
  @Mock Result updateResult;

  DocumentAiSetupHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DocumentAiSetupHandler(persistenceService);
  }

  @Test
  void afterSubscribe_doesNotInteractWithDatabase() {
    when(subscribeContext.getTenant()).thenReturn("tenant-a");

    handler.afterSubscribe(subscribeContext);

    verify(persistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void beforeUnsubscribe_marksActiveJobsAsFailed() {
    when(unsubscribeContext.getTenant()).thenReturn("tenant-a");
    when(updateResult.rowCount()).thenReturn(3L);
    ArgumentCaptor<CqnUpdate> captor = ArgumentCaptor.forClass(CqnUpdate.class);
    when(persistenceService.run(captor.capture())).thenReturn(updateResult);

    handler.beforeUnsubscribe(unsubscribeContext);

    ExtractionJob entry = Struct.access(captor.getValue().entries().get(0)).as(ExtractionJob.class);
    assertThat(entry.getStatus()).isEqualTo(ExtractionStatus.FAILED.name());
  }

  @Test
  void beforeUnsubscribe_noActiveJobs_doesNotThrow() {
    when(unsubscribeContext.getTenant()).thenReturn("tenant-a");
    when(updateResult.rowCount()).thenReturn(0L);
    when(persistenceService.run(any(CqnUpdate.class))).thenReturn(updateResult);

    handler.beforeUnsubscribe(unsubscribeContext);

    verify(persistenceService).run(any(CqnUpdate.class));
  }
}
