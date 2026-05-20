/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.cds.feature.aicore.core.handler.AICoreApplicationServiceHandler;
import com.sap.cds.feature.aicore.core.handler.ActionHandler;
import com.sap.cds.feature.aicore.core.handler.ConfigurationHandler;
import com.sap.cds.feature.aicore.core.handler.DeploymentHandler;
import com.sap.cds.feature.aicore.core.handler.MockEntityHandler;
import com.sap.cds.feature.aicore.core.handler.ResourceGroupHandler;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AICoreServiceConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AICoreServiceConfiguration.class);

  private static boolean hasAICoreBinding(CdsRuntime runtime) {
    boolean hasServiceBinding =
        runtime
            .getEnvironment()
            .getServiceBindings()
            .filter(b -> ServiceBindingUtils.matches(b, "aicore"))
            .findFirst()
            .isPresent();
    if (hasServiceBinding) {
      return true;
    }
    String envKey = System.getenv("AICORE_SERVICE_KEY");
    return envKey != null && !envKey.isBlank();
  }

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();

    boolean hasBinding = hasAICoreBinding(runtime);

    boolean multiTenancyEnabled =
        runtime
            .getEnvironment()
            .getProperty("cds.requires.AICore.multiTenancy", Boolean.class, false);

    if (hasBinding) {
      AICoreServiceImpl service =
          new AICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime, multiTenancyEnabled);
      configurer.service(service);
      logger.info("Registered AICoreService backed by AI Core binding.");
    } else {
      MockAICoreServiceImpl mockService =
          new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime);
      configurer.service(mockService);
      logger.info("Registered MockAICoreService (no AI Core binding found).");
    }
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();

    boolean hasBinding = hasAICoreBinding(runtime);

    if (hasBinding) {
      AICoreServiceImpl service =
          (AICoreServiceImpl)
              runtime
                  .getServiceCatalog()
                  .getService(AICoreService.class, AICoreService.DEFAULT_NAME);

      configurer.eventHandler(new ResourceGroupHandler(service));
      configurer.eventHandler(new DeploymentHandler(service));
      configurer.eventHandler(new ConfigurationHandler(service));
      configurer.eventHandler(new ActionHandler(service));
      configurer.eventHandler(new AICoreApplicationServiceHandler(service));
      logger.debug("Registered Prod AI-Core Implementation");

      if (service.isMultiTenancyEnabled()) {
        configurer.eventHandler(new AICoreSetupHandler(service));
        logger.debug("Registered AI-Core Setup Handler for MTX subscribe/unsubscribe.");
      }
    } else {
      MockAICoreServiceImpl mockService =
          (MockAICoreServiceImpl)
              runtime
                  .getServiceCatalog()
                  .getService(AICoreService.class, AICoreService.DEFAULT_NAME);
      configurer.eventHandler(new MockEntityHandler());
      configurer.eventHandler(new AICoreApplicationServiceHandler(mockService));
      logger.debug("Registered Mock AI-Core Implementation");
    }
  }
}
