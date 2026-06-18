/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.environment.CdsProperties.Remote.RemoteServiceConfig;

/** Shared test utilities for handler tests that boot a CDS runtime with the AICore model. */
final class HandlerTestUtils {

  private HandlerTestUtils() {}

  /** Creates CdsProperties with the AICore RemoteService configured. */
  static CdsProperties aicoreProperties() {
    CdsProperties props = new CdsProperties();
    RemoteServiceConfig rsConfig = new RemoteServiceConfig(AICore.SERVICE_NAME);
    rsConfig.setModel(AICore.SERVICE_NAME);
    props.getRemote().getServices().put(AICore.SERVICE_NAME, rsConfig);
    return props;
  }
}
