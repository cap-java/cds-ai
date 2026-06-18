/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.cds.feature.aicore.core.handler.AICoreSetupHandler;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AICoreSetupHandlerTest {

  private static final String TENANT = "tenant-1";
  private static final String RG_ID = "cds-tenant-1";

  @Mock private ResourceGroupApi resourceGroupApi;
  @Mock private UnsubscribeEventContext unsubscribeContext;

  private DeploymentResolver resolver;
  private AICoreClients clients;
  private AICoreSetupHandler cut;

  @BeforeEach
  void setUp() {
    AICoreConfig config = new AICoreConfig("default", "cds-", 10, 300, true);
    DeploymentApi deploymentApi = mock(DeploymentApi.class);
    clients =
        new AICoreClients(
            deploymentApi,
            mock(ConfigurationApi.class),
            resourceGroupApi,
            mock(AiCoreService.class));
    resolver = new DeploymentResolver(config, deploymentApi, resourceGroupApi);
    when(unsubscribeContext.getTenant()).thenReturn(TENANT);
    cut = new AICoreSetupHandler(clients, resolver);
  }

  @Test
  void cacheHit_deletesAndClears() throws Exception {
    putInTenantCache(resolver, TENANT, RG_ID);

    cut.beforeUnsubscribe(unsubscribeContext);

    verify(resourceGroupApi).delete(RG_ID);
    verify(resourceGroupApi, never()).getAll(any(), any(), any(), any(), any(), any(), any());
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void cacheMiss_fallsBackToApiAndDeletes() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getResourceGroupId()).thenReturn(RG_ID);
    BckndResourceGroupList list = listOf(List.of(rg));
    ArgumentCaptor<List<String>> labelCaptor = labelSelectorCaptor();
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), labelCaptor.capture()))
        .thenReturn(list);

    cut.beforeUnsubscribe(unsubscribeContext);

    assertThat(labelCaptor.getValue())
        .containsExactly(AICoreConfig.TENANT_LABEL_KEY + "=" + TENANT);
    verify(resourceGroupApi).delete(RG_ID);
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void cacheMissAndApiReturnsEmpty_isNoOp() {
    BckndResourceGroupList empty = listOf(List.of());
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(empty);

    cut.beforeUnsubscribe(unsubscribeContext);

    verify(resourceGroupApi, never()).delete(any());
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void cacheMissAndApiReturnsNullResources_isNoOp() {
    BckndResourceGroupList list = mock(BckndResourceGroupList.class);
    when(list.getResources()).thenReturn(null);
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any())).thenReturn(list);

    cut.beforeUnsubscribe(unsubscribeContext);

    verify(resourceGroupApi, never()).delete(any());
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void deleteReturns404_treatedAsSuccess() throws Exception {
    putInTenantCache(resolver, TENANT, RG_ID);
    OpenApiRequestException notFound = new OpenApiRequestException("not found").statusCode(404);
    when(resourceGroupApi.delete(RG_ID)).thenThrow(notFound);

    cut.beforeUnsubscribe(unsubscribeContext);

    verify(resourceGroupApi).delete(RG_ID);
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void deleteReturnsOther5xx_propagatesAsServiceException() throws Exception {
    putInTenantCache(resolver, TENANT, RG_ID);
    OpenApiRequestException serverError = new OpenApiRequestException("boom").statusCode(500);
    when(resourceGroupApi.delete(RG_ID)).thenThrow(serverError);

    assertThatThrownBy(() -> cut.beforeUnsubscribe(unsubscribeContext))
        .isInstanceOf(ServiceException.class)
        .hasCauseReference(serverError);
    // Cache still cleared in finally.
    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(TENANT);
  }

  @Test
  void unsubscribeTwice_secondCallIsNoOp() throws Exception {
    putInTenantCache(resolver, TENANT, RG_ID);

    cut.beforeUnsubscribe(unsubscribeContext);

    // Second call: cache empty → fallback → API returns empty → no-op.
    BckndResourceGroupList empty = listOf(List.of());
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(empty);

    cut.beforeUnsubscribe(unsubscribeContext);

    verify(resourceGroupApi, times(1)).delete(RG_ID);
    verify(resourceGroupApi, times(1)).getAll(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void getAllThrows_wrappedInServiceException() {
    OpenApiRequestException boom = new OpenApiRequestException("boom").statusCode(503);
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any())).thenThrow(boom);

    assertThatThrownBy(() -> cut.beforeUnsubscribe(unsubscribeContext))
        .isInstanceOf(ServiceException.class)
        .hasCauseReference(boom);
    verify(resourceGroupApi, never()).delete(any());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<List<String>> labelSelectorCaptor() {
    return ArgumentCaptor.forClass((Class) List.class);
  }

  private static BckndResourceGroupList listOf(List<BckndResourceGroup> resources) {
    BckndResourceGroupList list = mock(BckndResourceGroupList.class);
    when(list.getResources()).thenReturn(resources);
    return list;
  }

  @SuppressWarnings("unchecked")
  private static void putInTenantCache(DeploymentResolver resolver, String key, String value)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("tenantResourceGroupCache");
    field.setAccessible(true);
    ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver))
        .put(key, value);
  }
}
