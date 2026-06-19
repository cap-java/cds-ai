/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;

/**
 * Holder for the AI Core SDK API clients, built once from the service binding at startup.
 *
 * @param deploymentApi client for deployment CRUD operations
 * @param configurationApi client for configuration CRUD operations
 * @param resourceGroupApi client for resource-group CRUD operations
 * @param sdkService the AI Core SDK service for inference destination resolution
 */
public record AICoreClients(
    DeploymentApi deploymentApi,
    ConfigurationApi configurationApi,
    ResourceGroupApi resourceGroupApi,
    AiCoreService sdkService) {}
