package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.orchestrator.ExtractionOrchestrator;
import com.sap.cds.orchestrator.ExtractionService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class Registration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        PersistenceService persistenceService = configurer.getCdsRuntime()
                .getServiceCatalog()
                .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

        ExtractionService extractionService = new ExtractionOrchestrator(persistenceService);
        configurer.eventHandler(new AttachmentEventHandler(extractionService));
    }
}
