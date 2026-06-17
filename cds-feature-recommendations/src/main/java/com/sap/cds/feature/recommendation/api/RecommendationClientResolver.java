/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import com.sap.cds.services.cds.RemoteService;

// The annotation @FunctionalInterface ensures this interface has only one method, such that
// callers can supply a custom client by providing this one method e.g. via a lambda.
@FunctionalInterface
public interface RecommendationClientResolver {

  RecommendationClient resolve(RemoteService aiCoreService);
}
