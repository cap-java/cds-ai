/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.handler.ActionHandler;
import com.sap.cds.feature.aicore.core.handler.ConfigurationHandler;
import com.sap.cds.feature.aicore.core.handler.DeploymentHandler;
import com.sap.cds.feature.aicore.core.handler.MockEntityHandler;
import com.sap.cds.feature.aicore.core.handler.ResourceGroupHandler;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CdsRuntimeConfiguration} that wires the {@code AICore} service and its event handlers into
 * the CAP Java runtime.
 *
 * <p>Detects the presence of an SAP AI Core service binding (either a regular service binding or
 * the {@code AICORE_SERVICE_KEY} environment variable used for hybrid local testing) and registers
 * either {@link AICoreServiceImpl} (when a binding is found) or {@link MockAICoreServiceImpl}
 * (no-binding fallback). Picked up automatically through {@code ServiceLoader}; applications do not
 * need to instantiate this class directly.
 */
public class AICoreServiceConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AICoreServiceConfiguration.class);

  private static boolean hasAICoreModel(CdsRuntime runtime) {
    return runtime.getCdsModel().findService("AICore").isPresent();
  }

  private static boolean hasAICoreBinding(CdsRuntime runtime) {
    return runtime
        .getEnvironment()
        .getServiceBindings()
        .filter(b -> ServiceBindingUtils.matches(b, "aicore"))
        .findFirst()
        .isPresent();
  }

  /**
   * Detects multi-tenancy by checking the standard CAP Java {@code cds.multiTenancy.sidecar.url}
   * property or the presence of a {@link DeploymentService} in the service catalog. This aligns
   * with the standard CAP Java convention — no custom property flag is needed.
   */
  private static boolean detectMultiTenancy(CdsRuntime runtime) {
    CdsProperties props = runtime.getEnvironment().getCdsProperties();
    String sidecarUrl = props.getMultiTenancy().getSidecar().getUrl();
    if (sidecarUrl != null && !sidecarUrl.isBlank()) {
      return true;
    }
    return runtime.getServiceCatalog().getService(DeploymentService.class, DeploymentService.DEFAULT_NAME) != null;
  }

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();

    if (!hasAICoreModel(runtime)) {
      logger.debug("AICore CDS model not found in runtime model — skipping service registration.");
      return;
    }

    boolean hasBinding = hasAICoreBinding(runtime);

    boolean multiTenancyEnabled = detectMultiTenancy(runtime);

    if (hasBinding) {
      AICoreServiceImpl service =
          new AICoreServiceImpl(
              AICoreService.DEFAULT_NAME,
              runtime,
              multiTenancyEnabled,
              new DeploymentApi(),
              new ConfigurationApi(),
              new ResourceGroupApi(),
              new AiCoreService());
      configurer.service(service);
      logger.info("Registered AICoreService backed by AI Core binding.");
    } else {
      MockAICoreServiceImpl mockService =
          new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime, multiTenancyEnabled);
      configurer.service(mockService);
      logger.info("Registered MockAICoreService (no AI Core binding found).");
    }
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();

    AICoreService registered =
        runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);

    if (registered instanceof AICoreServiceImpl service) {
      configurer.eventHandler(new ResourceGroupHandler(service));
      configurer.eventHandler(new DeploymentHandler(service));
      configurer.eventHandler(new ConfigurationHandler(service));
      configurer.eventHandler(new ActionHandler(service));
      logger.debug("Registered Prod AI-Core Implementation");

      if (service.isMultiTenancyEnabled()) {
        configurer.eventHandler(new AICoreSetupHandler(service));
        logger.debug("Registered AI-Core Setup Handler for MTX subscribe/unsubscribe.");
      }
    } else if (registered instanceof MockAICoreServiceImpl mockService) {
      configurer.eventHandler(new MockEntityHandler());
      if (mockService.isMultiTenancyEnabled()) {
        configurer.eventHandler(new MockAICoreSetupHandler(mockService));
        logger.debug("Registered Mock AI-Core Setup Handler for MTX subscribe/unsubscribe.");
      }
      logger.debug("Registered Mock AI-Core Implementation");
    }
  }
}
