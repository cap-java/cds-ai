/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.documentai.configuration;

import com.sap.cds.feature.documentai.handlers.DocumentAiSetupHandler;
import com.sap.cds.feature.documentai.handlers.DocumentSubmissionHandler;
import com.sap.cds.feature.documentai.handlers.ExtractionPollingHandler;
import com.sap.cds.feature.documentai.service.DefaultDocumentAiProcessingService;
import com.sap.cds.feature.documentai.service.DocumentAiProcessingService;
import com.sap.cds.feature.documentai.service.ExtractionServiceImpl;
import com.sap.cds.feature.documentai.service.client.DefaultDocumentAiClient;
import com.sap.cds.feature.documentai.service.client.DocumentAiClient;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.mt.DeploymentService;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.*;
import java.time.Duration;
import java.util.Optional;
import org.apache.hc.client5.http.classic.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDS plugin configuration that wires up all Document AI services and event handlers at runtime.
 *
 * <p>Implements {@link CdsRuntimeConfiguration} so it is picked up automatically by the CDS runtime
 * via the Java {@code ServiceLoader} mechanism (declared in {@code
 * META-INF/services/com.sap.cds.services.runtime.CdsRuntimeConfiguration}).
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Registers {@link ExtractionServiceImpl} as a CDS service.
 *   <li>Resolves the DIE service binding from the environment and builds an authenticated {@link
 *       DefaultDocumentAiClient} via the SAP Cloud SDK destination API.
 *   <li>Wires all dependencies into {@link ExtractionServiceImpl} and registers the {@link
 *       DocumentSubmissionHandler} and (when a binding is present) the {@link
 *       ExtractionPollingHandler}.
 * </ul>
 */
public class DocumentAiServiceConfiguration implements CdsRuntimeConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentAiServiceConfiguration.class);

  private ExtractionServiceImpl extractionService;

  static {
    OAuth2ServiceBindingDestinationLoader.registerPropertySupplier(
        options ->
            ServiceBindingUtils.matches(
                options.getServiceBinding(),
                DefaultDocumentAiProcessingService.SAP_DOCUMENT_AI_SERVICE_LABEL),
        DefaultOAuth2PropertySupplier::new);
  }

  /**
   * Registers {@link ExtractionServiceImpl} as a CDS service so it is available in the service
   * catalog for injection into event handlers.
   */
  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    extractionService = new ExtractionServiceImpl();
    configurer.service(extractionService);
  }

  /**
   * Resolves runtime dependencies and registers all plugin event handlers.
   *
   * <p>{@link DocumentSubmissionHandler} is always registered. {@link ExtractionPollingHandler} is
   * only registered when a DIE service binding is found and a {@link DocumentAiClient} can be
   * built; without a binding the plugin accepts extraction events but leaves jobs as {@code
   * PENDING}.
   */
  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();
    ServiceCatalog serviceCatalog = runtime.getServiceCatalog();
    boolean multiTenancyEnabled = detectMultiTenancy(runtime);

    // framework-managed dependency
    PersistenceService persistenceService =
        serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

    // internal
    DocumentAiClient documentAiClient = buildDocumentAi(runtime.getEnvironment());
    DocumentAiProcessingService documentAiProcessingService =
        new DefaultDocumentAiProcessingService(documentAiClient);

    OutboxService outboxService =
        serviceCatalog.getService(OutboxService.class, OutboxService.PERSISTENT_UNORDERED_NAME);

    if (outboxService == null) {
      logger.warn(
          "[sap-document-ai] Persistent outbox not available — polling scheduler disabled. Ensure cds.outbox.persistent is configured.");
    }

    int intervalSeconds =
        runtime
            .getEnvironment()
            .getProperty(
                "cds.document-ai.polling.interval-seconds",
                Integer.class,
                ExtractionPollingHandler.DEFAULT_POLL_INTERVAL_SECONDS);
    Duration pollDelay = Duration.ofSeconds(intervalSeconds);

    extractionService.init(
        persistenceService, documentAiProcessingService, outboxService, pollDelay);

    configurer.eventHandler(new DocumentSubmissionHandler(extractionService));

    if (multiTenancyEnabled) {
      configurer.eventHandler(new DocumentAiSetupHandler(persistenceService));
      logger.debug(
          "[sap-document-ai] Registered DocumentAiSetupHandler for MTX subscribe/unsubscribe.");
    }

    // polling handler — only registered when a DIE binding is present
    if (documentAiClient != null) {
      configurer.eventHandler(
          new ExtractionPollingHandler(
              persistenceService,
              extractionService,
              documentAiClient,
              outboxService,
              runtime,
              pollDelay,
              multiTenancyEnabled));
    }
  }

  /**
   * Attempts to build a {@link DocumentAiClient} from the first DIE service binding found in the
   * environment.
   *
   * <p>If no binding is present, or if the Cloud SDK destination cannot be constructed, {@code
   * null} is returned and extraction is effectively disabled until a binding becomes available.
   *
   * @param environment the CDS runtime environment used to look up service bindings
   * @return a configured {@link DocumentAiClient}, or {@code null} if unavailable
   */
  static DocumentAiClient buildDocumentAi(CdsEnvironment environment) {
    Optional<ServiceBinding> optionalBinding =
        environment
            .getServiceBindings()
            .filter(
                b ->
                    ServiceBindingUtils.matches(
                        b, DefaultDocumentAiProcessingService.SAP_DOCUMENT_AI_SERVICE_LABEL))
            .findFirst();

    if (optionalBinding.isEmpty()) {
      logger.warn("[sap-document-ai] No Document AI service binding found, extraction disabled.");
      return null;
    }

    ServiceBinding binding = optionalBinding.get();
    logger.info(
        "[sap-document-ai] Using Document AI binding '{}', plan '{}'",
        binding.getName().orElse("unknown"),
        binding.getServicePlan().orElse("unknown"));

    try {
      HttpDestination httpDestination =
          ServiceBindingDestinationLoader.defaultLoaderChain()
              .getDestination(
                  ServiceBindingDestinationOptions.forService(binding)
                      .onBehalfOf(OnBehalfOf.TECHNICAL_USER_CURRENT_TENANT)
                      .build());
      HttpClient httpClient = ApacheHttpClient5Accessor.getHttpClient(httpDestination);
      logger.info(
          "[sap-document-ai] Document AI destination created successfully, url={}",
          httpDestination.getUri());
      return new DefaultDocumentAiClient(httpDestination, httpClient);
    } catch (Exception e) {
      logger.warn(
          "[sap-document-ai] Failed to create Document AI destination, extraction disabled.", e);
      return null;
    }
  }

  static boolean detectMultiTenancy(CdsRuntime runtime) {
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
