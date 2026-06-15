/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentCreationResponse;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiExecutionStatus;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.core.DeploymentResolver;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration-style tests for {@link DeploymentHandler} using a real CDS runtime. Only the SDK API
 * clients (DeploymentApi, ResourceGroupApi, ConfigurationApi) are mocked since they talk to a
 * remote AI Core service.
 */
class DeploymentHandlerTest {

  private static CdsRuntime runtime;
  private static AICoreServiceImpl service;
  private static DeploymentApi deploymentApi;
  private static ResourceGroupApi resourceGroupApi;
  private static ConfigurationApi configurationApi;

  @BeforeAll
  static void bootRuntime() {
    deploymentApi = mock(DeploymentApi.class);
    resourceGroupApi = mock(ResourceGroupApi.class);
    configurationApi = mock(ConfigurationApi.class);

    var configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()));
    configurer.cdsModel("edmx/csn.json");
    runtime = configurer.getCdsRuntime();

    AICoreConfig config = new AICoreConfig("default", "cds-", 10, 300, false);
    AICoreClients clients =
        new AICoreClients(
            deploymentApi, configurationApi, resourceGroupApi, mock(AiCoreService.class));
    DeploymentResolver resolver = new DeploymentResolver(config, deploymentApi, resourceGroupApi);

    service = new AICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime);
    configurer.service(service);
    configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
    configurer.eventHandler(new DeploymentHandler(config, clients, resolver));
    configurer.complete();
  }

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(deploymentApi, resourceGroupApi, configurationApi);
  }

  @Test
  void onCreate_createsDeploymentWithConfigurationId() {
    AiDeploymentCreationResponse response = mock(AiDeploymentCreationResponse.class);
    when(response.getId()).thenReturn("new-dep-id");
    when(response.getStatus()).thenReturn(AiExecutionStatus.UNKNOWN);
    when(deploymentApi.create(eq("default"), any(AiDeploymentCreationRequest.class)))
        .thenReturn(response);

    Result result =
        runtime
            .requestContext()
            .run(
                (Function<RequestContext, Result>)
                    ctx ->
                        service.run(
                            Insert.into("AICore.deployments")
                                .entry(
                                    Map.of(
                                        "configurationId", "cfg-1",
                                        "resourceGroup_resourceGroupId", "default"))));

    ArgumentCaptor<AiDeploymentCreationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentCreationRequest.class);
    verify(deploymentApi).create(eq("default"), captor.capture());
    assertThat(captor.getValue().getConfigurationId()).isEqualTo("cfg-1");
    assertThat(result.single().get("id")).isEqualTo("new-dep-id");
  }

  @Test
  void onCreate_withTtl_setsTtlOnRequest() {
    AiDeploymentCreationResponse response = mock(AiDeploymentCreationResponse.class);
    when(response.getId()).thenReturn("dep-ttl");
    when(response.getStatus()).thenReturn(AiExecutionStatus.UNKNOWN);
    when(deploymentApi.create(eq("default"), any(AiDeploymentCreationRequest.class)))
        .thenReturn(response);

    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Insert.into("AICore.deployments")
                            .entry(
                                Map.of(
                                    "configurationId", "cfg-2",
                                    "ttl", "PT24H",
                                    "resourceGroup_resourceGroupId", "default"))));

    ArgumentCaptor<AiDeploymentCreationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentCreationRequest.class);
    verify(deploymentApi).create(eq("default"), captor.capture());
    assertThat(captor.getValue().getTtl()).isEqualTo("PT24H");
  }

  @Test
  void onUpdate_withTargetStatus_callsModifyWithTargetStatus() {
    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Update.entity("AICore.deployments")
                            .where(d -> d.get("id").eq("dep-123"))
                            .data("targetStatus", "STOPPED")));

    ArgumentCaptor<AiDeploymentModificationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentModificationRequest.class);
    verify(deploymentApi).modify(eq("default"), eq("dep-123"), captor.capture());
    assertThat(captor.getValue().getTargetStatus().getValue()).isEqualTo("STOPPED");
  }

  @Test
  void onUpdate_withConfigurationId_callsModifyWithConfigurationId() {
    runtime
        .requestContext()
        .run(
            (Function<RequestContext, Result>)
                ctx ->
                    service.run(
                        Update.entity("AICore.deployments")
                            .where(d -> d.get("id").eq("dep-789"))
                            .data("configurationId", "config-456")));

    ArgumentCaptor<AiDeploymentModificationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentModificationRequest.class);
    verify(deploymentApi).modify(eq("default"), eq("dep-789"), captor.capture());
    assertThat(captor.getValue().getConfigurationId()).isEqualTo("config-456");
  }

  @Test
  void onUpdate_withoutTargetStatusOrConfigurationId_throwsBadRequest() {
    assertThatThrownBy(
            () ->
                runtime
                    .requestContext()
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Update.entity("AICore.deployments")
                                        .where(d -> d.get("id").eq("dep-x"))
                                        .data("ttl", "1d"))))
        .isInstanceOfSatisfying(
            ServiceException.class,
            e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatuses.BAD_REQUEST))
        .hasMessageContaining("targetStatus")
        .hasMessageContaining("configurationId");
  }
}
