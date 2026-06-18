/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

// A single-method interface so callers can supply a custom client via lambda.
// @FunctionalInterface enforces this and causes a compile error if a second method is ever added.
// The type parameter T allows the resolver to receive any context the client might need (e.g. key
// names).
@FunctionalInterface
public interface RecommendationClientResolver<T> {

  RecommendationClient resolve(T context);
}
