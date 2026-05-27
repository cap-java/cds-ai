/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(DeploymentService.DEFAULT_NAME)
public class MockAICoreSetupHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockAICoreSetupHandler.class);

  private final MockAICoreServiceImpl service;

  public MockAICoreSetupHandler(MockAICoreServiceImpl service) {
    this.service = service;
  }

  @After(event = DeploymentService.EVENT_SUBSCRIBE)
  public void afterSubscribe(SubscribeEventContext context) {
    String tenantId = context.getTenant();
    String resourceGroupId = service.resourceGroupForTenant(tenantId);
    logger.info(
        "Mock created in-memory resource group {} for tenant {}", resourceGroupId, tenantId);
  }

  @Before(event = DeploymentService.EVENT_UNSUBSCRIBE)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    service.clearTenantCache(tenantId);
    logger.info("Mock cleared in-memory caches for tenant {}", tenantId);
  }
}
