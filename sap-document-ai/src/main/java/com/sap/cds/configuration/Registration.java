package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentExtractionHandler;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class Registration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        configurer.eventHandler(new AttachmentExtractionHandler());
    }
}
