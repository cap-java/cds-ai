/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiParameterArgumentBinding;
import java.util.List;
import java.util.function.Predicate;

/**
 * Describes a target AI Core deployment used by {@link AICoreService#deploymentId(String,
 * ModelDeploymentSpec)} to look up or create a deployment inside a resource group.
 *
 * <p>The spec carries the AI Core scenario/executable identification, the human-readable
 * configuration name (used as a stable key for caching and idempotent reuse), the parameter
 * bindings to apply when a configuration must be created, and a predicate that decides whether an
 * already existing deployment is acceptable for reuse.
 *
 * @param scenarioId AI Core scenario ID (e.g. {@code "foundation-models"})
 * @param executableId AI Core executable ID inside the scenario
 * @param configurationName human-readable configuration name; doubles as cache key per resource
 *     group
 * @param parameterBindings parameter bindings applied when a new configuration must be created;
 *     ignored if a configuration with the same {@code configurationName} already exists
 * @param matchesExisting predicate that returns {@code true} for an existing {@link AiDeployment}
 *     considered equivalent to this spec; typically checks model and version
 */
public record ModelDeploymentSpec(
    String scenarioId,
    String executableId,
    String configurationName,
    List<AiParameterArgumentBinding> parameterBindings,
    Predicate<AiDeployment> matchesExisting) {}
