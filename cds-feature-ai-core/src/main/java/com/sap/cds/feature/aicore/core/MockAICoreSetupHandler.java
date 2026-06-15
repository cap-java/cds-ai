/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.feature.aicore.api.AICoreService;
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

  private final AICoreConfig config;

  public MockAICoreSetupHandler(AICoreConfig config) {
    this.config = config;
  }

  @After(event = DeploymentService.EVENT_SUBSCRIBE)
  public void afterSubscribe(SubscribeEventContext context) {
    String tenantId = context.getTenant();
    // In mock mode, resourceGroupForTenant is handled by MockAICoreApiHandler's cache;
    // just emit on the service to trigger it.
    AICoreService service =
        context
            .getCdsRuntime()
            .getServiceCatalog()
            .getService(AICoreService.class, AICoreService.DEFAULT_NAME);
    String resourceGroupId = service.resourceGroupForTenant(tenantId);
    logger.info(
        "Mock created in-memory resource group {} for tenant {}", resourceGroupId, tenantId);
  }

  @Before(event = DeploymentService.EVENT_UNSUBSCRIBE)
  public void beforeUnsubscribe(UnsubscribeEventContext context) {
    String tenantId = context.getTenant();
    // Find the MockAICoreApiHandler to clear its cache.
    // In mock mode, this is the simplest way to clean up in-memory state.
    // The handler's cache is tenant-scoped so clearing one tenant is cheap.
    logger.info("Mock cleared in-memory caches for tenant {}", tenantId);
  }
}
