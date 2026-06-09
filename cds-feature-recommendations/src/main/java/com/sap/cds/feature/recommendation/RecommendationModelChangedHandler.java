/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.mt.ExtensibilityService;
import com.sap.cds.services.mt.ModelChangedEventContext;

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
