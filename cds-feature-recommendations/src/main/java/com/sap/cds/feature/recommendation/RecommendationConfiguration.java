/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.MockAICoreServiceImpl;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecommendationConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(RecommendationConfiguration.class);

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();
    ServiceCatalog serviceCatalog = runtime.getServiceCatalog();

    AICoreService aiCoreService =
        serviceCatalog.getService(AICoreService.class, AICoreService.DEFAULT_NAME);

    if (aiCoreService == null) {
      logger.info("No AICoreService found, skipping Fiori recommendation handler registration.");
      return;
    }

    RecommendationClientResolver resolver =
        aiCoreService instanceof MockAICoreServiceImpl
            ? (service, tenantId) -> new MockRecommendationClient()
            : RecommendationConfiguration::resolveRptClient;

    FioriRecommendationHandler handler = new FioriRecommendationHandler(aiCoreService, resolver);
    configurer.eventHandler(handler);
    configurer.eventHandler(new RecommendationModelChangedHandler(handler));
  }

  private static RecommendationClient resolveRptClient(AICoreService service, String tenantId) {
    String resourceGroup = service.resourceGroupForTenant(tenantId);
    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    return new RptInferenceClient(
        service.inferenceClient(resourceGroup, deploymentId), service.getRetry());
  }
}
