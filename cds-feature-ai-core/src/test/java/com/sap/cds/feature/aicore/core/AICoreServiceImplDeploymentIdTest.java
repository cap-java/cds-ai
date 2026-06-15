/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.api.ModelDeploymentSpec;
import com.sap.cds.feature.aicore.core.handler.AICoreApiHandler;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the happy paths of deployment ID resolution: cache hit on a RUNNING deployment,
 * stale-cache invalidation when the cached deployment is gone, and reuse of an existing matching
 * deployment found via query.
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
  private DeploymentResolver resolver;

  private final ModelDeploymentSpec spec =
      new ModelDeploymentSpec(SCENARIO, "exec", CONFIG_NAME, List.of(), d -> true);

  private String cacheKey() {
    return DeploymentResolver.deploymentCacheKey(RG, spec);
  }

  /**
   * Creates an {@link AICoreServiceImpl} properly registered with a CDS runtime and the {@link
   * AICoreApiHandler} so that {@code emit()} dispatches to the handler.
   */
  private AICoreServiceImpl createService(boolean multiTenancy) {
    TestPropertiesProvider props = new TestPropertiesProvider();
    props.setProperty("cds.ai.core.maxRetries", 1);
    props.setProperty("cds.ai.core.initialDelayMs", 1L);

    CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(props);
    configurer.cdsModel("edmx/csn.json");
    CdsRuntime runtime = configurer.getCdsRuntime();

    AICoreConfig config = new AICoreConfig("default", "cds-", 1, 1L, multiTenancy);
    AICoreClients clients =
        new AICoreClients(
            deploymentApi, configurationApi, resourceGroupApi, mock(AiCoreService.class));
    resolver = new DeploymentResolver(config, deploymentApi);

    AICoreServiceImpl svc = new AICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime);
    configurer.service(svc);
    configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
    configurer.complete();
    return svc;
  }

  @BeforeEach
  void setUp() {
    deploymentApi = mock(DeploymentApi.class);
    configurationApi = mock(ConfigurationApi.class);
    resourceGroupApi = mock(ResourceGroupApi.class);
    service = createService(false);
  }

  @Test
  void cacheHit_runningDeployment_returnsCachedIdWithoutQuery() throws Exception {
    putInDeploymentCache(resolver, cacheKey(), DEPLOYMENT_ID);

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
  void cacheStale_404OnGet_invalidatesAndReturnsExistingFromQuery() throws Exception {
    String otherDeployment = "dep-456";
    putInDeploymentCache(resolver, cacheKey(), "stale-id");

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
    assertThat(getDeploymentCache(resolver)).containsEntry(cacheKey(), otherDeployment);
    verify(deploymentApi, never()).create(any(), any());
  }

  @Test
  void cacheStale_5xxOnGet_propagatesAndPreservesCacheEntry() throws Exception {
    putInDeploymentCache(resolver, cacheKey(), "still-valid-id");

    OpenApiRequestException serverError = new OpenApiRequestException("boom").statusCode(503);
    when(deploymentApi.get(RG, "still-valid-id")).thenThrow(serverError);

    assertThatThrownBy(() -> service.deploymentId(RG, spec)).rootCause().isSameAs(serverError);

    assertThat(getDeploymentCache(resolver)).containsEntry(cacheKey(), "still-valid-id");
    verify(deploymentApi, never()).query(any(), any(), any(), any(), any(), any(), any(), any());
    verify(deploymentApi, never()).create(any(), any());
  }

  @Test
  void noCache_existingMatchingDeployment_isReusedAndCached() throws Exception {
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
    assertThat(getDeploymentCache(resolver)).containsEntry(cacheKey(), DEPLOYMENT_ID);
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
    verify(deploymentApi, times(1))
        .query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any());
    verify(deploymentApi, times(1)).get(RG, DEPLOYMENT_ID);
  }

  @Test
  void resourceGroupForTenant_nullTenantId_returnsDefault() {
    AICoreServiceImpl mtService = createService(true);

    String result = mtService.resourceGroupForTenant(null);
    assertThat(result).isEqualTo("default");
  }

  @Test
  void resourceGroupForTenant_multiTenancyDisabled_returnsDefault() {
    String result = service.resourceGroupForTenant("any-tenant");
    assertThat(result).isEqualTo("default");
  }

  @Test
  void noCacheNoExistingDeployment_createsNewDeploymentWhenConfigExists() throws Exception {
    AiDeploymentList emptyList = mock(AiDeploymentList.class);
    when(emptyList.getResources()).thenReturn(List.of());
    when(deploymentApi.query(eq(RG), any(), any(), eq(SCENARIO), any(), any(), any(), any()))
        .thenReturn(emptyList);

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
    assertThat(getDeploymentCache(resolver)).containsEntry(cacheKey(), DEPLOYMENT_ID);
    verify(configurationApi, never()).create(any(), any());
    verify(deploymentApi).create(eq(RG), any());
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static void putInDeploymentCache(DeploymentResolver resolver, String key, String value)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentCache");
    field.setAccessible(true);
    ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver))
        .put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> getDeploymentCache(DeploymentResolver resolver)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentCache");
    field.setAccessible(true);
    return ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver)).asMap();
  }

  /** Properties provider that allows overriding specific keys for test configuration. */
  private static class TestPropertiesProvider extends SimplePropertiesProvider {
    private final Map<String, Object> properties = new HashMap<>();

    TestPropertiesProvider() {
      super(new CdsProperties());
    }

    void setProperty(String key, Object value) {
      properties.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> asClazz, T defaultValue) {
      Object value = properties.get(key);
      if (value != null && asClazz.isInstance(value)) {
        return (T) value;
      }
      return defaultValue;
    }
  }
}
