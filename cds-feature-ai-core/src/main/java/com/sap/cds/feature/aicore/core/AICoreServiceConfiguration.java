/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.handler.AICoreApiHandler;
import com.sap.cds.feature.aicore.core.handler.AICoreSetupHandler;
import com.sap.cds.feature.aicore.core.handler.ActionHandler;
import com.sap.cds.feature.aicore.core.handler.ConfigurationHandler;
import com.sap.cds.feature.aicore.core.handler.DeploymentHandler;
import com.sap.cds.feature.aicore.core.handler.MockAICoreApiHandler;
import com.sap.cds.feature.aicore.core.handler.MockAICoreSetupHandler;
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
 * the appropriate handlers. Picked up automatically through {@code ServiceLoader}; applications do
 * not need to instantiate this class directly.
 */
public class AICoreServiceConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AICoreServiceConfiguration.class);

  private AICoreConfig config;
  private AICoreClients clients;
  private DeploymentResolver resolver;

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
    return runtime
            .getServiceCatalog()
            .getService(DeploymentService.class, DeploymentService.DEFAULT_NAME)
        != null;
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

    this.config = AICoreConfig.from(runtime.getEnvironment(), multiTenancyEnabled);

    if (hasBinding) {
      DeploymentApi deploymentApi = new DeploymentApi();
      ConfigurationApi configurationApi = new ConfigurationApi();
      ResourceGroupApi resourceGroupApi = new ResourceGroupApi();
      AiCoreService sdkService = new AiCoreService();

      this.clients =
          new AICoreClients(deploymentApi, configurationApi, resourceGroupApi, sdkService);
      this.resolver = new DeploymentResolver(config, deploymentApi, resourceGroupApi);
      logger.info("Registered AICoreService backed by AI Core binding.");
    } else {
      logger.info(
          "Registered AICoreService (no AI Core binding found — mock handlers will be used).");
    }

    configurer.service(new AICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime));
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    if (config == null) {
      return; // No AICore model — services() skipped registration
    }

    if (clients != null) {
      // Production path: real AI Core binding
      configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
      configurer.eventHandler(new ResourceGroupHandler(config, clients, resolver));
      configurer.eventHandler(new DeploymentHandler(config, clients, resolver));
      configurer.eventHandler(new ConfigurationHandler(config, clients, resolver));
      configurer.eventHandler(new ActionHandler(config, clients, resolver));
      logger.debug("Registered production AI Core event handlers.");

      if (config.multiTenancyEnabled()) {
        configurer.eventHandler(new AICoreSetupHandler(clients, resolver));
        logger.debug("Registered AI Core setup handler for MTX subscribe/unsubscribe.");
      }
    } else {
      // Mock path: no AI Core binding
      MockAICoreApiHandler mockApiHandler = new MockAICoreApiHandler(config);
      configurer.eventHandler(new MockEntityHandler());
      configurer.eventHandler(mockApiHandler);
      logger.debug("Registered mock AI Core event handlers.");

      if (config.multiTenancyEnabled()) {
        configurer.eventHandler(new MockAICoreSetupHandler(mockApiHandler));
        logger.debug("Registered mock AI Core setup handler for MTX subscribe/unsubscribe.");
      }
    }
  }
}
