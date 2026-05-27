package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.service.ExtractionServiceImpl;
import com.sap.cds.service.ExtractionService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class AttachmentEventHandlerRegistration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        PersistenceService persistenceService = configurer.getCdsRuntime()
                .getServiceCatalog()
                .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

        ExtractionService extractionService = new ExtractionServiceImpl(persistenceService);
        configurer.eventHandler(new AttachmentEventHandler(extractionService));
    }
}
