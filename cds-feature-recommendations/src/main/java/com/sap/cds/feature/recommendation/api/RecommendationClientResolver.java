/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import com.sap.cds.feature.aicore.api.AICoreService;

@FunctionalInterface
public interface RecommendationClientResolver {

  RecommendationClient resolve(AICoreService aiCoreService, String tenantId);
}
