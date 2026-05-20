/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiParameterArgumentBinding;
import java.util.List;
import java.util.function.Predicate;

public record ModelDeploymentSpec(
    String scenarioId,
    String executableId,
    String configurationName,
    List<AiParameterArgumentBinding> parameterBindings,
    Predicate<AiDeployment> matchesExisting) {}
