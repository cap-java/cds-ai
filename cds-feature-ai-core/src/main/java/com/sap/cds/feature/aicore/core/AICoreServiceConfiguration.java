/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.cds.feature.aicore.api.AICore;
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
import com.sap.cds.services.environment.CdsProperties.Remote.RemoteServiceConfig;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CdsRuntimeConfiguration} that wires the {@code AICore} remote service and its event
 * handlers into the CAP Java runtime.
 *
 * <p>In the {@link #environment} phase, a {@link RemoteServiceConfig} entry for "AICore" is
 * injected into the runtime properties so the framework's {@code RemoteServiceConfiguration}
 * auto-creates the service instance from the CDS model. This follows the same pattern used by
 * {@code cds-feature-notifications}.
 *
 * <p>In the {@link #eventHandlers} phase, production or mock handlers are registered depending on
 * whether an AI Core service binding is present.
 */
public class AICoreServiceConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AICoreServiceConfiguration.class);

  private AICoreConfig config;
  private AICoreClients clients;
  private DeploymentResolver resolver;

  /**
   * Injects a {@link RemoteServiceConfig} for "AICore" into the runtime properties. This runs
   * before all {@code services()} methods, ensuring the framework's {@code
   * RemoteServiceConfiguration} will auto-create a {@code RemoteServiceImpl} for the AICore CDS
   * service definition.
   */
  @Override
  public void environment(CdsRuntimeConfigurer configurer) {
    RemoteServiceConfig remoteConfig = new RemoteServiceConfig(AICore.SERVICE_NAME);
    remoteConfig.setModel(AICore.SERVICE_NAME);
    configurer
        .getCdsRuntime()
        .getEnvironment()
        .getCdsProperties()
        .getRemote()
        .getServices()
        .putIfAbsent(AICore.SERVICE_NAME, remoteConfig);
  }

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();

    if (!hasAICoreModel(runtime)) {
      logger.debug("AICore CDS model not found in runtime model - skipping handler setup.");
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
      logger.info("AI Core binding detected - production handlers will be registered.");
    } else {
      logger.info("No AI Core binding found - mock handlers will be registered.");
    }
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    if (config == null) {
      return; // No AICore model - services() skipped
    }

    if (clients != null) {
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

  private static boolean hasAICoreModel(CdsRuntime runtime) {
    return runtime.getCdsModel().findService(AICore.SERVICE_NAME).isPresent();
  }

  private static boolean hasAICoreBinding(CdsRuntime runtime) {
    return runtime
        .getEnvironment()
        .getServiceBindings()
        .filter(b -> ServiceBindingUtils.matches(b, "aicore"))
        .findFirst()
        .isPresent();
  }

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
}
