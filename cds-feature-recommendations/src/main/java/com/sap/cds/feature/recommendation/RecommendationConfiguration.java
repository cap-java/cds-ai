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
import java.util.List;
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

    if (db == null) {
      logger.info(
          "No PersistenceService found, skipping Fiori recommendation handler registration.");
      return;
    }

    boolean hasBind = hasAICoreBinding(runtime);
    // The real resolver is a lambda resolved at prediction time. That's necessary because
    // resource group and deployment ID are tenant-specific and are only available at
    // prediction time from the request context. AICoreService is captured in the closure.
    RecommendationClientResolver<List<String>> clientResolver =
        hasBind
            ? keyNames -> resolveRptClient(aiCoreService, keyNames)
            : keyNames -> new MockRecommendationClient(keyNames);

    FioriRecommendationHandler handler = new FioriRecommendationHandler(clientResolver, db);
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

  private static RecommendationClient resolveRptClient(
      AICoreService service, List<String> keyNames) {
    String resourceGroup = service.resourceGroup();
    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    return new RptInferenceClient(service.inferenceClient(resourceGroup, deploymentId), keyNames);
  }
}
