/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.sap.ai.sdk.core.model.AiConfiguration;
import com.sap.ai.sdk.core.model.AiConfigurationBaseData;
import com.sap.ai.sdk.core.model.AiConfigurationCreationResponse;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration-style tests for {@link ConfigurationHandler} using a real CDS runtime. Only the SDK
 * API clients are mocked since they talk to a remote AI Core service.
 */
class ConfigurationHandlerTest {

  private static CdsRuntime runtime;
  private static AICoreServiceImpl service;
  private static ConfigurationApi configurationApi;
  private static ResourceGroupApi resourceGroupApi;

  @BeforeAll
  static void bootRuntime() {
    configurationApi = mock(ConfigurationApi.class);
    resourceGroupApi = mock(ResourceGroupApi.class);
    DeploymentApi deploymentApi = mock(DeploymentApi.class);

    var configurer =
        CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()));
    configurer.cdsModel("edmx/csn.json");
    runtime = configurer.getCdsRuntime();

    service =
        new AICoreServiceImpl(
            AICoreService.DEFAULT_NAME,
            runtime,
            /* multiTenancy */ false,
            deploymentApi,
            configurationApi,
            resourceGroupApi,
            mock(AiCoreService.class));
    configurer.service(service);
    configurer.eventHandler(new AICoreApiHandler());
    configurer.eventHandler(new ConfigurationHandler(configurationApi, resourceGroupApi));
    configurer.complete();
  }

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(configurationApi, resourceGroupApi);
  }

  @Test
  void onRead_returnsConfigurationsForResourceGroup() {
    AiConfiguration cfg = mock(AiConfiguration.class);
    when(cfg.getId()).thenReturn("cfg-1");
    when(cfg.getName()).thenReturn("my-config");
    when(cfg.getExecutableId()).thenReturn("exec-1");
    when(cfg.getScenarioId()).thenReturn("foundation-models");

    AiConfigurationList list = mock(AiConfigurationList.class);
    when(list.getResources()).thenReturn(List.of(cfg));
    when(configurationApi.query(eq("default"), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(list);

    Result result =
        runtime
            .requestContext()
            .run(
                (Function<RequestContext, Result>)
                    ctx ->
                        service.run(
                            Select.from("AICore.configurations")
                                .where(
                                    c ->
                                        c.get("resourceGroup_resourceGroupId").eq("default"))));

    verify(configurationApi).query(eq("default"), any(), any(), any(), any(), any(), any(), any());
    assertThat(result.list()).hasSize(1);
    assertThat(result.single().get("id")).isEqualTo("cfg-1");
    assertThat(result.single().get("name")).isEqualTo("my-config");
  }

  @Test
  void onRead_nullResources_returnsEmptyList() {
    AiConfigurationList listWithNullResources = mock(AiConfigurationList.class);
    when(listWithNullResources.getResources()).thenReturn(null);
    when(configurationApi.query(eq("default"), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(listWithNullResources);

    Result result =
        runtime
            .requestContext()
            .run(
                (Function<RequestContext, Result>)
                    ctx ->
                        service.run(
                            Select.from("AICore.configurations")
                                .where(
                                    c ->
                                        c.get("resourceGroup_resourceGroupId").eq("default"))));

    assertThat(result.list()).isEmpty();
  }

  @Test
  void onCreate_createsConfiguration() {
    AiConfigurationCreationResponse response = mock(AiConfigurationCreationResponse.class);
    when(response.getId()).thenReturn("new-cfg-id");
    when(configurationApi.create(eq("default"), any(AiConfigurationBaseData.class)))
        .thenReturn(response);

    Result result =
        runtime
            .requestContext()
            .run(
                (Function<RequestContext, Result>)
                    ctx ->
                        service.run(
                            Insert.into("AICore.configurations")
                                .entry(
                                    Map.of(
                                        "name", "test-config",
                                        "executableId", "exec-1",
                                        "scenarioId", "foundation-models",
                                        "resourceGroup_resourceGroupId", "default"))));

    ArgumentCaptor<AiConfigurationBaseData> captor =
        ArgumentCaptor.forClass(AiConfigurationBaseData.class);
    verify(configurationApi).create(eq("default"), captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("test-config");
    assertThat(captor.getValue().getExecutableId()).isEqualTo("exec-1");
    assertThat(captor.getValue().getScenarioId()).isEqualTo("foundation-models");
    assertThat(result.single().get("id")).isEqualTo("new-cfg-id");
  }
}
