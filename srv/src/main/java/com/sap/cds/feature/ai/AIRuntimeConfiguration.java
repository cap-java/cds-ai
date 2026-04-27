/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai;

import com.sap.cds.feature.ai.client.setup.AICoreSetup;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIRuntimeConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AIRuntimeConfiguration.class);

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {

    CdsRuntime runtime = configurer.getCdsRuntime();
    ServiceCatalog serviceCatalog = runtime.getServiceCatalog();

    PersistenceService persistenceService =
        serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    logger.info("PersistenceService obtained from ServiceCatalog: " + (persistenceService != null));

    Optional<ServiceBinding> bindingOpt = getAIBinding(configurer.getCdsRuntime().getEnvironment());
    // If the AI Core service binding is present, create the AICoreSetup event handler to manage
    // resource groups for tenants.
    Optional<AICoreSetup> setupOpt = bindingOpt.map(b -> new AICoreSetup());
    setupOpt.ifPresent(
        setup -> {
          configurer.eventHandler(setup);
          logger.info("Registered AICoreSetup as event handler for MTX subscribe/unsubscribe.");
        });
    configurer.eventHandler(new FioriRecommendationHandler(setupOpt, persistenceService));
    logger.info("Registered FioriRecommendationHandler for recommendations.");
  }

  private static Optional<ServiceBinding> getAIBinding(CdsEnvironment environment) {
    return environment
        .getServiceBindings()
        .filter(b -> b.getServiceName().map(name -> name.equals("aicore")).orElse(false))
        .findFirst();
  }
}
