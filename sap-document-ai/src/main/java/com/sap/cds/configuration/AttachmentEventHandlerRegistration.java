/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.configuration;

import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentAiService_;
import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.handlers.DocumentSubmissionHandler;
import com.sap.cds.service.DefaultDocumentAiProcessingService;
import com.sap.cds.service.DocumentAiProcessingService;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.service.documentai.client.DefaultDocumentAiClient;
import com.sap.cds.service.documentai.client.DocumentAiClient;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.*;
import java.util.Optional;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachmentEventHandlerRegistration implements CdsRuntimeConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(AttachmentEventHandlerRegistration.class);

  private ExtractionServiceImpl extractionService;

  static {
    OAuth2ServiceBindingDestinationLoader.registerPropertySupplier(
        options ->
            ServiceBindingUtils.matches(
                options.getServiceBinding(),
                DefaultDocumentAiProcessingService.SAP_DOCUMENT_AI_SERVICE_LABEL),
        DefaultOAuth2PropertySupplier::new);
  }

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    extractionService = new ExtractionServiceImpl();
    configurer.service(extractionService);
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    CdsRuntime runtime = configurer.getCdsRuntime();
    ServiceCatalog serviceCatalog = runtime.getServiceCatalog();

    // framework-managed dependency
    PersistenceService persistenceService =
        serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    DocumentAiService documentAiService =
        serviceCatalog.getService(DocumentAiService.class, DocumentAiService_.CDS_NAME);

    // internal
    DocumentAiClient documentAiClient = buildDocumentAi(runtime.getEnvironment());
    DocumentAiProcessingService documentAiProcessingService =
        new DefaultDocumentAiProcessingService(documentAiClient);

    extractionService.init(persistenceService, documentAiProcessingService);

    OutboxService outboxService =
        serviceCatalog.getService(OutboxService.class, OutboxService.PERSISTENT_UNORDERED_NAME);

    ExtractionService outboxedExtractionService;

    if (outboxService != null) {
      outboxedExtractionService = outboxService.outboxed(extractionService);
    } else {
      outboxedExtractionService = extractionService;
      logger.warn(
          "OutboxService '{}' is not available. AttachmentService will not be outboxed.",
          OutboxService.PERSISTENT_UNORDERED_NAME);
    }

    // register event handler with CAP runtime
    if (isAttachmentsPluginPresent()) {
      configurer.eventHandler(new AttachmentEventHandler(outboxedExtractionService));
      logger.info(
          "[sap-document-ai] cds-feature-attachments detected, attachment handler registered");
    } else {
      logger.info(
          "[sap-document-ai] cds-feature-attachments not found, attachment handler skipped");
    }

    configurer.eventHandler(new DocumentSubmissionHandler(extractionService, documentAiService));
  }

  private static boolean isAttachmentsPluginPresent() {
    try {
      Class.forName("com.sap.cds.feature.attachments.service.AttachmentService");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

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
      HttpClient httpClient = HttpClientAccessor.getHttpClient(httpDestination);
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
}
