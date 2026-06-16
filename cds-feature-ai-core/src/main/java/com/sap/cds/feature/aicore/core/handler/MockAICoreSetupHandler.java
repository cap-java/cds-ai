/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(DeploymentService.DEFAULT_NAME)
public class MockAICoreSetupHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockAICoreSetupHandler.class);

  private final MockAICoreApiHandler mockHandler;

  public MockAICoreSetupHandler(MockAICoreApiHandler mockHandler) {
    this.mockHandler = mockHandler;
  }

  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void afterSubscribe(SubscribeEventContext context) {
    String tenantId = context.getTenant();
    // Trigger resource group creation in mock cache
    mockHandler.getTenantResourceGroupCache().computeIfAbsent(tenantId, id -> "cds-" + id);
    logger.info("Mock created in-memory resource group for tenant {}", tenantId);
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    mockHandler.clearTenantCache(tenantId);
    logger.info("Mock cleared in-memory caches for tenant {}", tenantId);
  }
}
