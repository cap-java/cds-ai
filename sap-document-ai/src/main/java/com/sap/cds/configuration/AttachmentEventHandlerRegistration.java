package com.sap.cds.configuration;

import com.sap.cds.handlers.AttachmentEventHandler;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class AttachmentEventHandlerRegistration implements CdsRuntimeConfiguration {
    @Override
    public void eventHandlers(CdsRuntimeConfigurer configurer) {
        configurer.eventHandler(new AttachmentEventHandler());
    }
}
