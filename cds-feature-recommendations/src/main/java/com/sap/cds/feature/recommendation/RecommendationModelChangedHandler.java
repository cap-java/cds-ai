/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.ExtensibilityService;
import com.sap.cds.services.mt.ModelChangedEventContext;

// TODO Integration test needed for the cache-invalidation behaviour.
//   The proper E2E pattern (cf. cds-services ExtendViaSidecarTest) requires:
//     - extensibility-enabled mtx-local sidecar (/-/cds/extensibility/set)
//     - an extension JSON adding a prediction column to a draft-enabled entity
//     - per-tenant SQLite schema that survives the model mutation
//     - assert that an OData read returns SAP_Recommendations only AFTER the
//       extension is applied AND EVENT_MODEL_CHANGED has been emitted.
//   The unit test in FioriRecommendationHandlerTest.invalidateTenant_*
//   already covers the cache-invalidation logic in isolation; what is missing
//   is the wiring + observable-effect assertion through MockMvc.
@ServiceName(value = ExtensibilityService.DEFAULT_NAME, type = ExtensibilityService.class)
class RecommendationModelChangedHandler implements EventHandler {

  private final FioriRecommendationHandler recommendationHandler;

  RecommendationModelChangedHandler(FioriRecommendationHandler recommendationHandler) {
    this.recommendationHandler = recommendationHandler;
  }

  @On(event = ExtensibilityService.EVENT_MODEL_CHANGED)
  public void onModelChanged(ModelChangedEventContext context) {
    String tenantId = context.getUserInfo().getTenant();
    recommendationHandler.invalidateTenant(tenantId);
  }
}
