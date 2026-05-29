package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.service.DefaultDocumentAiProcessingService;
import com.sap.cds.service.DocumentAiProcessingService;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class AttachmentEventHandlerRegistration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        CdsRuntime runtime = configurer.getCdsRuntime();
        ServiceCatalog serviceCatalog = runtime.getServiceCatalog();

        // framework-managed dependency
        PersistenceService persistenceService =
                serviceCatalog.getService(
                        PersistenceService.class,
                        PersistenceService.DEFAULT_NAME);

        // internal
        DocumentAiProcessingService documentAiProcessingService =
                new DefaultDocumentAiProcessingService();

        ExtractionService extractionService =
                new ExtractionServiceImpl(
                        persistenceService,
                        documentAiProcessingService);

        // register event handler with CAP runtime
        configurer.eventHandler(
                new AttachmentEventHandler(extractionService));
    }
}
