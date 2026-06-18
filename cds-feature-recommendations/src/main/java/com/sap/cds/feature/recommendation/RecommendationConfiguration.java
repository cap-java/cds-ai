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
import java.util.List;
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
    // The real resolver is a lambda resolved at prediction time. That's necessary because
    // resource group and deployment ID are tenant-specific and are only available at
    // prediction time from the request context. The RemoteService is captured in the closure.
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
      RemoteService service, List<String> keyNames) {
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    String resourceGroup = rgCtx.getResult();
    if (resourceGroup == null) {
      throw new IllegalStateException("Failed to resolve resource group from AI Core service");
    }

    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(resourceGroup);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);
    String deploymentId = depCtx.getResult();
    if (deploymentId == null) {
      throw new IllegalStateException(
          "Failed to resolve deployment ID for resource group: " + resourceGroup);
    }

    InferenceClientContext infCtx = InferenceClientContext.create();
    infCtx.setResourceGroupId(resourceGroup);
    infCtx.setDeploymentId(deploymentId);
    service.emit(infCtx);
    if (infCtx.getResult() == null) {
      throw new IllegalStateException(
          "Failed to create inference client for deployment: " + deploymentId);
    }

    return new RptInferenceClient(infCtx.getResult(), keyNames);
  }
}
