/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.recommendation.api.RecommendationClient;
import com.sap.cds.feature.recommendation.api.RecommendationClientResolver;
import com.sap.cds.feature.recommendation.api.RptInferenceClient;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.cds.RemoteService;
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

    RemoteService aiCoreService =
        serviceCatalog.getService(RemoteService.class, AICore.SERVICE_NAME);

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
        .findAny()
        .isPresent();
  }

  private static RecommendationClient resolveRptClient(RemoteService service) {
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    String resourceGroup = rgCtx.getResult();

    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(resourceGroup);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);
    String deploymentId = depCtx.getResult();

    InferenceClientContext infCtx = InferenceClientContext.create();
    infCtx.setResourceGroupId(resourceGroup);
    infCtx.setDeploymentId(deploymentId);
    service.emit(infCtx);

    return new RptInferenceClient(infCtx.getResult());
  }
}
