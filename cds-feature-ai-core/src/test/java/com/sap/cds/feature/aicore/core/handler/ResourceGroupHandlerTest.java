/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupPatchRequest;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.core.DeploymentResolver;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.environment.CdsProperties.Remote.RemoteServiceConfig;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration-style tests for {@link ResourceGroupHandler} using a real CDS runtime. Only the SDK
 * API clients are mocked since they talk to a remote AI Core service.
 */
class ResourceGroupHandlerTest {

  private static CdsRuntime runtime;
  private static RemoteService service;
  private static ResourceGroupApi resourceGroupApi;

  @BeforeAll
  static void bootRuntime() {
    resourceGroupApi = mock(ResourceGroupApi.class);
    DeploymentApi deploymentApi = mock(DeploymentApi.class);
    ConfigurationApi configurationApi = mock(ConfigurationApi.class);

    CdsProperties props = new CdsProperties();
    RemoteServiceConfig rsConfig = new RemoteServiceConfig(AICore.SERVICE_NAME);
    rsConfig.setModel(AICore.SERVICE_NAME);
    props.getRemote().getServices().put(AICore.SERVICE_NAME, rsConfig);

    var configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(props));
    configurer.cdsModel("edmx/csn.json");
    configurer.serviceConfigurations();
    runtime = configurer.getCdsRuntime();

    AICoreConfig config = new AICoreConfig("default", "cds-", 10, 300, false);
    AICoreClients clients =
        new AICoreClients(
            deploymentApi, configurationApi, resourceGroupApi, mock(AiCoreService.class));
    DeploymentResolver resolver = new DeploymentResolver(config, deploymentApi, resourceGroupApi);

    configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
    configurer.eventHandler(new ResourceGroupHandler(config, clients, resolver));
    configurer.complete();

    service = runtime.getServiceCatalog().getService(RemoteService.class, AICore.SERVICE_NAME);
  }

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(resourceGroupApi);
  }

  @Test
  void onRead_returnsAllResourceGroups() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getResourceGroupId()).thenReturn("rg-1");
    when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);

    BckndResourceGroupList list = mock(BckndResourceGroupList.class);
    when(list.getResources()).thenReturn(List.of(rg));
    when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any())).thenReturn(list);

    Result result =
        runtime
            .requestContext()
            .run(
                (Function<RequestContext, Result>)
                    ctx -> service.run(Select.from("AICore.resourceGroups")));

    verify(resourceGroupApi).getAll(any(), any(), any(), any(), any(), any(), any());
    assertThat(result.list()).hasSize(1);
    assertThat(result.single().get("resourceGroupId")).isEqualTo("rg-1");
  }

  @Test
  void onCreate_createsResourceGroup() {
    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Insert.into("AICore.resourceGroups")
                            .entry(Map.of("resourceGroupId", "rg-new"))));

    ArgumentCaptor<BckndResourceGroupsPostRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupsPostRequest.class);
    verify(resourceGroupApi).create(captor.capture());
    assertThat(captor.getValue().getResourceGroupId()).isEqualTo("rg-new");
  }

  @Test
  void onCreate_withTenantId_setsTenantLabel() {
    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Insert.into("AICore.resourceGroups")
                            .entry(
                                Map.of(
                                    "resourceGroupId", "rg-tenant",
                                    "tenantId", "tenant-a"))));

    ArgumentCaptor<BckndResourceGroupsPostRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupsPostRequest.class);
    verify(resourceGroupApi).create(captor.capture());
    assertThat(captor.getValue().getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple(AICoreConfig.TENANT_LABEL_KEY, "tenant-a"));
  }

  @Test
  void onUpdate_withLabels_callsPatchWithLabels() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getResourceGroupId()).thenReturn("rg-upd");
    when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);
    when(resourceGroupApi.get("rg-upd")).thenReturn(rg);

    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Update.entity("AICore.resourceGroups")
                            .where(d -> d.get("resourceGroupId").eq("rg-upd"))
                            .data("labels", List.of(Map.of("key", "env", "value", "staging")))));

    ArgumentCaptor<BckndResourceGroupPatchRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupPatchRequest.class);
    verify(resourceGroupApi).patch(eq("rg-upd"), captor.capture());
    assertThat(captor.getValue().getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple("env", "staging"));
  }

  @Test
  void onUpdate_withoutLabels_callsPatchWithoutLabels() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getResourceGroupId()).thenReturn("rg-nolabel");
    when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);
    when(resourceGroupApi.get("rg-nolabel")).thenReturn(rg);

    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Update.entity("AICore.resourceGroups")
                            .where(d -> d.get("resourceGroupId").eq("rg-nolabel"))
                            .data("statusMessage", "updated")));

    ArgumentCaptor<BckndResourceGroupPatchRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupPatchRequest.class);
    verify(resourceGroupApi).patch(eq("rg-nolabel"), captor.capture());
    assertThat(captor.getValue().getLabels()).isNullOrEmpty();
  }

  /**
   * Multi-tenancy tests use a separate runtime with MT enabled to verify tenant-scoped label
   * selectors.
   */
  @Nested
  class MultiTenancyTests {

    private static CdsRuntime mtRuntime;
    private static RemoteService mtService;
    private static ResourceGroupApi mtResourceGroupApi;

    @BeforeAll
    static void bootMtRuntime() {
      mtResourceGroupApi = mock(ResourceGroupApi.class);
      DeploymentApi deploymentApi = mock(DeploymentApi.class);
      ConfigurationApi configurationApi = mock(ConfigurationApi.class);

      CdsProperties props = new CdsProperties();
      RemoteServiceConfig rsConfig = new RemoteServiceConfig(AICore.SERVICE_NAME);
      rsConfig.setModel(AICore.SERVICE_NAME);
      props.getRemote().getServices().put(AICore.SERVICE_NAME, rsConfig);

      var configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(props));
      configurer.cdsModel("edmx/csn.json");
      configurer.serviceConfigurations();
      mtRuntime = configurer.getCdsRuntime();

      AICoreConfig config = new AICoreConfig("default", "cds-", 10, 300, true);
      AICoreClients clients =
          new AICoreClients(
              deploymentApi, configurationApi, mtResourceGroupApi, mock(AiCoreService.class));
      DeploymentResolver resolver =
          new DeploymentResolver(config, deploymentApi, mtResourceGroupApi);

      configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
      configurer.eventHandler(new ResourceGroupHandler(config, clients, resolver));
      configurer.complete();

      mtService =
          mtRuntime.getServiceCatalog().getService(RemoteService.class, AICore.SERVICE_NAME);
    }

    @BeforeEach
    void clearMtMockInvocations() {
      clearInvocations(mtResourceGroupApi);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readAll_multiTenancy_nonProviderUser_restrictsByCurrentTenant() {
      BckndResourceGroupList list = mock(BckndResourceGroupList.class);
      when(list.getResources()).thenReturn(List.of());
      when(mtResourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(list);

      mtRuntime
          .requestContext()
          .modifyUser(
              u ->
                  u.setTenant("current-tenant")
                      .setIsSystemUser(false)
                      .setIsInternalUser(false)
                      .setName("test-user")
                      .setIsAuthenticated(true))
          .run(
              (Function<RequestContext, Result>)
                  ctx -> mtService.run(Select.from("AICore.resourceGroups")));

      ArgumentCaptor<List<String>> selectorCaptor = ArgumentCaptor.forClass(List.class);
      verify(mtResourceGroupApi)
          .getAll(any(), any(), any(), any(), any(), any(), selectorCaptor.capture());
      assertThat(selectorCaptor.getValue())
          .containsExactly(AICoreConfig.TENANT_LABEL_KEY + "=current-tenant");
    }
  }
}
