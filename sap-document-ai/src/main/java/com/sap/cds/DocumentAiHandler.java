package com.sap.cds;

import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ServiceName(value = "*", type = com.sap.cds.services.Service.class)
public class DocumentAiHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentAiHandler.class);

    @Before(event = CqnService.EVENT_READ)
    public void beforeRead() {
        log.info("[sap-document-ai] Before READ event triggered");
    }
}
