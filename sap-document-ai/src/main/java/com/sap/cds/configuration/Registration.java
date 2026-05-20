package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.orchestrator.ExtractionOrchestrator;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class Registration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        ExtractionOrchestrator extractionOrchestrator = new ExtractionOrchestrator();
        configurer.eventHandler(new AttachmentEventHandler(extractionOrchestrator));
    }
}
