/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.recommendation.api.RecommendationClient;
import com.sap.cds.feature.recommendation.api.RecommendationClientResolver;
import com.sap.cds.feature.recommendation.api.RptInferenceClient;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
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

    PersistenceService db =
        serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

    boolean hasBind = hasAICoreBinding(runtime);
    RecommendationClientResolver resolver =
        hasBind
            ? RecommendationConfiguration::resolveRptClient
            : service -> new MockRecommendationClient();

    FioriRecommendationHandler handler =
        new FioriRecommendationHandler(aiCoreService, resolver, db);
    configurer.eventHandler(handler);
    configurer.eventHandler(new RecommendationModelChangedHandler(handler));
  }

  private static boolean hasAICoreBinding(CdsRuntime runtime) {
    return runtime
        .getEnvironment()
        .getServiceBindings()
        .filter(b -> ServiceBindingUtils.matches(b, "aicore"))
        .findFirst()
        .isPresent();
  }

  private static RecommendationClient resolveRptClient(AICoreService service) {
    String resourceGroup = service.resourceGroup();
    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    return new RptInferenceClient(service.inferenceClient(resourceGroup, deploymentId));
  }
}
