/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.feature.aicore.generated.cds4j.aicore.AICore_;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.environment.CdsProperties.Remote.RemoteServiceConfig;

/** Shared test utilities for handler tests that boot a CDS runtime with the AICore model. */
final class HandlerTestUtils {

  private HandlerTestUtils() {}

  /** Creates CdsProperties with the AICore RemoteService configured. */
  static CdsProperties aicoreProperties() {
    CdsProperties props = new CdsProperties();
    RemoteServiceConfig rsConfig = new RemoteServiceConfig(AICore_.CDS_NAME);
    rsConfig.setModel(AICore_.CDS_NAME);
    props.getRemote().getServices().put(AICore_.CDS_NAME, rsConfig);
    return props;
  }
}
