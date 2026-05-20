/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.feature.aicore.core.AICoreService;

@FunctionalInterface
interface RecommendationClientResolver {

  RecommendationClient resolve(AICoreService aiCoreService, String tenantId);
}
