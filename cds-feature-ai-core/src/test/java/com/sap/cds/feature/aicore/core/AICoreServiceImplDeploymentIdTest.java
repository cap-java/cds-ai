/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentCreationResponse;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentResponseWithDetails;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the happy paths of {@link AICoreServiceImpl#deploymentId(String,
 * ModelDeploymentSpec)}: cache hit on a RUNNING deployment, stale-cache invalidation when the
 * cached deployment is gone, and reuse of an existing matching deployment found via query.
 */
class AICoreServiceImplDeploymentIdTest {

  private static final String RG = "rg-1";
  private static final String CONFIG_NAME = "rpt1-config";
  private static final String SCENARIO = "foundation-models";
  private static final String DEPLOYMENT_ID = "dep-123";

  private DeploymentApi deploymentApi;
  private ConfigurationApi configurationApi;
  private ResourceGroupApi resourceGroupApi;
  private AICoreServiceImpl service;

  private final ModelDeploymentSpec spec =
      new ModelDeploymentSpec(SCENARIO, "exec", CONFIG_NAME, List.of(), d -> true);

  private String cacheKey() {
    // Derive via the production helper rather than hardcoding RG + "::" + CONFIG_NAME so a
    // change to the cache-key format is caught here instead of silently passing wrong-path.
    return AICoreServiceImpl.deploymentCacheKey(RG, spec);
  }

  @BeforeEach
  void setUp() {
    deploymentApi = mock(DeploymentApi.class);
    configurationApi = mock(ConfigurationApi.class);
    resourceGroupApi = mock(ResourceGroupApi.class);
    AiCoreService sdkService = mock(AiCoreService.class);

    CdsRuntime runtime = mock(CdsRuntime.class);
    CdsEnvironment env = mock(CdsEnvironment.class);
    when(runtime.getEnvironment()).thenReturn(env);
    // Use small retry counts so failures don't slow tests.
    when(env.getProperty(eq("cds.requires.AICore.maxRetries"), eq(Integer.class), any()))
        .thenReturn(1);
    when(env.getProperty(eq("cds.requires.AICore.initialDelayMs"), eq(Long.class), any()))
        .thenReturn(1L);
    when(env.getProperty(eq("cds.requires.AICore.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    when(env.getProperty(eq("cds.requires.AICore.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    service =
        new AICoreServiceImpl(
            AICoreService.DEFAULT_NAME,
            runtime,
            false,
            deploymentApi,
            configurationApi,
            resourceGroupApi,
            sdkService);
  }

  @Test
  void cacheHit_runningDeployment_returnsCachedIdWithoutQuery() {
    service.getResourceGroupDeploymentCache().put(cacheKey(), DEPLOYMENT_ID);

    AiDeploymentResponseWithDetails running = mock(AiDeploymentResponseWithDetails.class);
    when(running.getStatus()).thenReturn(AiDeploymentStatus.RUNNING);
    when(deploymentApi.get(RG, DEPLOYMENT_ID)).thenReturn(running);

    String result = service.deploymentId(RG, spec);

    assertThat(result).isEqualTo(DEPLOYMENT_ID);
    verify(deploymentApi).get(RG, DEPLOYMENT_ID);
    verify(deploymentApi, never()).query(any(), any(), any(), any(), any(), any(), any(), any());
    verify(deploymentApi, never()).create(any(), any());
  }

  @Test
  void cacheStale_404OnGet_invalidatesAndReturnsExistingFromQuery() {
    String otherDeployment = "dep-456";
    service.getResourceGroupDeploymentCache().put(cacheKey(), "stale-id");

    OpenApiRequestException notFound = new OpenApiRequestException("gone").statusCode(404);
    when(deploymentApi.get(RG, "stale-id")).thenThrow(notFound);

    AiDeployment existing = mock(AiDeployment.class);
    when(existing.getId()).thenReturn(otherDeployment);
    when(existing.getConfigurationName()).thenReturn(CONFIG_NAME);
    when(existing.getStatus()).thenReturn(AiDeploymentStatus.RUNNING);
    AiDeploymentList list = mock(AiDeploymentList.class);
    when(list.getResources()).thenReturn(List.of(existing));
    when(deploymentApi.query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any()))
        .thenReturn(list);

    String result = service.deploymentId(RG, spec);

    assertThat(result).isEqualTo(otherDeployment);
    assertThat(service.getResourceGroupDeploymentCache())
        .containsEntry(cacheKey(), otherDeployment);
    verify(deploymentApi, never()).create(any(), any());
  }

  @Test
  void cacheStale_5xxOnGet_propagatesAndPreservesCacheEntry() {
    // Transient 5xx must NOT invalidate a potentially valid cache entry. The exception is
    // propagated so the caller's retry/backoff policy can handle it.
    service.getResourceGroupDeploymentCache().put(cacheKey(), "still-valid-id");

    OpenApiRequestException serverError = new OpenApiRequestException("boom").statusCode(503);
    when(deploymentApi.get(RG, "still-valid-id")).thenThrow(serverError);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deploymentId(RG, spec))
        .isSameAs(serverError);

    assertThat(service.getResourceGroupDeploymentCache())
        .containsEntry(cacheKey(), "still-valid-id");
    verify(deploymentApi, never()).query(any(), any(), any(), any(), any(), any(), any(), any());
    verify(deploymentApi, never()).create(any(), any());
  }

  @Test
  void noCache_existingMatchingDeployment_isReusedAndCached() {
    AiDeployment existing = mock(AiDeployment.class);
    when(existing.getId()).thenReturn(DEPLOYMENT_ID);
    when(existing.getConfigurationName()).thenReturn(CONFIG_NAME);
    when(existing.getStatus()).thenReturn(AiDeploymentStatus.PENDING);
    AiDeploymentList list = mock(AiDeploymentList.class);
    when(list.getResources()).thenReturn(List.of(existing));
    when(deploymentApi.query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any()))
        .thenReturn(list);

    String result = service.deploymentId(RG, spec);

    assertThat(result).isEqualTo(DEPLOYMENT_ID);
    assertThat(service.getResourceGroupDeploymentCache()).containsEntry(cacheKey(), DEPLOYMENT_ID);
    verify(deploymentApi, never()).create(any(), any());
    verify(deploymentApi, never()).get(any(), any());
  }

  @Test
  void secondCallUsesCachedResult_singleQueryToApi() {
    AiDeployment existing = mock(AiDeployment.class);
    when(existing.getId()).thenReturn(DEPLOYMENT_ID);
    when(existing.getConfigurationName()).thenReturn(CONFIG_NAME);
    when(existing.getStatus()).thenReturn(AiDeploymentStatus.RUNNING);
    AiDeploymentList list = mock(AiDeploymentList.class);
    when(list.getResources()).thenReturn(List.of(existing));
    when(deploymentApi.query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any()))
        .thenReturn(list);

    AiDeploymentResponseWithDetails running = mock(AiDeploymentResponseWithDetails.class);
    when(running.getStatus()).thenReturn(AiDeploymentStatus.RUNNING);
    when(deploymentApi.get(RG, DEPLOYMENT_ID)).thenReturn(running);

    String first = service.deploymentId(RG, spec);
    String second = service.deploymentId(RG, spec);

    assertThat(first).isEqualTo(DEPLOYMENT_ID);
    assertThat(second).isEqualTo(DEPLOYMENT_ID);
    // First call queries, second call hits the cache and only verifies via get.
    verify(deploymentApi, times(1))
        .query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any());
    verify(deploymentApi, times(1)).get(RG, DEPLOYMENT_ID);
  }

  @Test
  void noCacheNoExistingDeployment_createsNewDeploymentWhenConfigExists() {
    // Empty deployment list → falls through to create path.
    AiDeploymentList emptyList = mock(AiDeploymentList.class);
    when(emptyList.getResources()).thenReturn(List.of());
    when(deploymentApi.query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any()))
        .thenReturn(emptyList);

    // Existing config with the same name, so createConfiguration is skipped.
    AiConfigurationList configList = mock(AiConfigurationList.class);
    var existingConfig = mock(com.sap.ai.sdk.core.model.AiConfiguration.class);
    when(existingConfig.getId()).thenReturn("cfg-1");
    when(existingConfig.getName()).thenReturn(CONFIG_NAME);
    when(configList.getResources()).thenReturn(List.of(existingConfig));
    when(configurationApi.query(eq(RG), eq(SCENARIO), any(), any(), any(), any(), any(), any()))
        .thenReturn(configList);

    AiDeploymentCreationResponse created = mock(AiDeploymentCreationResponse.class);
    when(created.getId()).thenReturn(DEPLOYMENT_ID);
    when(deploymentApi.create(eq(RG), any())).thenReturn(created);

    AiDeploymentResponseWithDetails runningPoll = mock(AiDeploymentResponseWithDetails.class);
    when(runningPoll.getStatus()).thenReturn(AiDeploymentStatus.RUNNING);
    when(deploymentApi.get(RG, DEPLOYMENT_ID)).thenReturn(runningPoll);

    String result = service.deploymentId(RG, spec);

    assertThat(result).isEqualTo(DEPLOYMENT_ID);
    assertThat(service.getResourceGroupDeploymentCache()).containsEntry(cacheKey(), DEPLOYMENT_ID);
    verify(configurationApi, never()).create(any(), any());
    verify(deploymentApi).create(eq(RG), any());
  }
}
