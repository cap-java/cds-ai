/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.handlers;

import com.sap.cds.Result;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.ExtractionJob_;
import com.sap.cds.feature.documentai.service.ExtractionStatus;
import com.sap.cds.ql.Update;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hooks into the MTX tenant lifecycle to react to subscribe and unsubscribe events.
 *
 * <p>On subscribe: logs that the tenant is onboarded. No provisioning is needed because the DIE
 * service auto-creates a {@code clientId} namespace on first use.
 *
 * <p>On unsubscribe: marks all active jobs ({@code PENDING}, {@code SUBMITTED}, {@code RUNNING})
 * belonging to the tenant as {@code FAILED}. Without this, those jobs would remain in a
 * non-terminal state and generate poll errors indefinitely after the tenant's credentials are
 * revoked.
 */
@ServiceName(DeploymentService.DEFAULT_NAME)
public class DocumentAiSetupHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DocumentAiSetupHandler.class);

  private final PersistenceService persistenceService;

  public DocumentAiSetupHandler(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void afterSubscribe(SubscribeEventContext context) {
    logger.info(
        "[sap-document-ai] Tenant {} subscribed — DIE clientId namespace will be created on first use",
        context.getTenant());
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    logger.info(
        "[sap-document-ai] Tenant {} unsubscribing — marking active jobs as FAILED", tenantId);

    ExtractionJob failedStatus = ExtractionJob.create();
    failedStatus.setStatus(ExtractionStatus.FAILED.name());

    List<String> activeStatuses =
        Arrays.stream(ExtractionStatus.values())
            .filter(s -> !s.isTerminal())
            .map(ExtractionStatus::name)
            .toList();

    Result result =
        persistenceService.run(
            Update.entity(ExtractionJob_.class)
                .where(j -> j.tenantId().eq(tenantId).and(j.status().in(activeStatuses)))
                .entry(failedStatus));

    logger.info(
        "[sap-document-ai] Marked {} active job(s) as FAILED for tenant {}",
        result.rowCount(),
        tenantId);
  }
}
