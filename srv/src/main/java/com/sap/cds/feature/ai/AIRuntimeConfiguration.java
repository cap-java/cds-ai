/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai;

import com.sap.cds.feature.ai.client.setup.AICoreSetupHandler;
import com.sap.cds.services.ServiceCatalog;
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

    Optional<ServiceBinding> binding =
        runtime
            .getEnvironment()
            .getServiceBindings()
            .filter(b -> b.getServiceName().map(name -> name.equals("aicore")).orElse(false))
            .findFirst();
    // If the AI Core service binding is present, create the AICoreSetup event handler to manage
    // resource groups for tenants.
    // The binding itself does *not* need to be passed to the AICoreSetup; the AICoreSetup uses
    // the com.sap.ai.sdk.core library which reads the binding directly from the environment
    // variable AICORE_SERVICE_KEY.
    Optional<AICoreSetupHandler> setup =
        binding.map(b -> new AICoreSetupHandler(runtime.getEnvironment()));
    setup.ifPresent(
        s -> {
          configurer.eventHandler(s);
          logger.info("Registered AICoreSetup as event handler for MTX subscribe/unsubscribe.");
        });
    configurer.eventHandler(new FioriRecommendationHandler(setup, persistenceService));
    logger.info("Registered FioriRecommendationHandler for recommendations.");
  }
}
