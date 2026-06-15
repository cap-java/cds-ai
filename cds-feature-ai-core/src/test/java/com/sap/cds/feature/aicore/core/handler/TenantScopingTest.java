/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreClients;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.core.DeploymentResolver;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration-style tests for tenant-scoping logic through actual CQN READ operations. Verifies
 * that {@code ensureResourceGroupAccessible} (used by DeploymentHandler and ConfigurationHandler)
 * correctly enforces tenant isolation when multi-tenancy is enabled.
 */
class TenantScopingTest {

  private static CdsRuntime runtime;
  private static AICoreServiceImpl service;
  private static DeploymentApi deploymentApi;
  private static ResourceGroupApi resourceGroupApi;

  @BeforeAll
  static void bootRuntime() {
    deploymentApi = mock(DeploymentApi.class);
    resourceGroupApi = mock(ResourceGroupApi.class);
    ConfigurationApi configurationApi = mock(ConfigurationApi.class);

    var configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(new CdsProperties()));
    configurer.cdsModel("edmx/csn.json");
    runtime = configurer.getCdsRuntime();

    AICoreConfig config = new AICoreConfig("default", "cds-", 10, 300, true);
    AICoreClients clients =
        new AICoreClients(
            deploymentApi, configurationApi, resourceGroupApi, mock(AiCoreService.class));
    DeploymentResolver resolver = new DeploymentResolver(config, deploymentApi);

    service = new AICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime);
    configurer.service(service);
    configurer.eventHandler(new AICoreApiHandler(config, clients, resolver));
    configurer.eventHandler(new DeploymentHandler(config, clients));
    configurer.complete();
  }

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(deploymentApi, resourceGroupApi);
  }

  @Test
  void matchingTenant_allowsAccess() {
    stubResourceGroupWithTenant("rg-a", "tenant-A");
    stubDeploymentQuery();

    assertThatCode(
            () ->
                runtime
                    .requestContext()
                    .modifyUser(
                        u ->
                            u.setTenant("tenant-A")
                                .setIsSystemUser(false)
                                .setIsInternalUser(false)
                                .setName("user-a")
                                .setIsAuthenticated(true))
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-a")))))
        .doesNotThrowAnyException();
  }

  @Test
  void nonMatchingTenant_throws404() {
    stubResourceGroupWithTenant("rg-b", "tenant-A");

    assertThatThrownBy(
            () ->
                runtime
                    .requestContext()
                    .modifyUser(
                        u ->
                            u.setTenant("tenant-B")
                                .setIsSystemUser(false)
                                .setIsInternalUser(false)
                                .setName("user-b")
                                .setIsAuthenticated(true))
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-b")))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void providerUser_bypassesTenantCheck() {
    stubResourceGroupWithTenant("rg-c", "tenant-X");
    stubDeploymentQuery();

    // System user (provider) should bypass tenant check regardless of tenant label
    assertThatCode(
            () ->
                runtime
                    .requestContext()
                    .systemUser()
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-c")))))
        .doesNotThrowAnyException();
  }

  @Test
  void nullTenantUser_bypassesTenantCheck() {
    stubDeploymentQuery();

    // Non-system user with null tenant bypasses check (currentTenantId() returns null)
    assertThatCode(
            () ->
                runtime
                    .requestContext()
                    .modifyUser(
                        u ->
                            u.setTenant(null)
                                .setIsSystemUser(false)
                                .setIsInternalUser(false)
                                .setName("no-tenant-user")
                                .setIsAuthenticated(true))
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-d")))))
        .doesNotThrowAnyException();
  }

  @Test
  void noLabelsOnResourceGroup_throws404() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getLabels()).thenReturn(null);
    when(resourceGroupApi.get("rg-no-labels")).thenReturn(rg);

    assertThatThrownBy(
            () ->
                runtime
                    .requestContext()
                    .modifyUser(
                        u ->
                            u.setTenant("tenant-A")
                                .setIsSystemUser(false)
                                .setIsInternalUser(false)
                                .setName("user-labels")
                                .setIsAuthenticated(true))
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-no-labels")))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void emptyLabelsOnResourceGroup_throws404() {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getLabels()).thenReturn(List.of());
    when(resourceGroupApi.get("rg-empty-labels")).thenReturn(rg);

    assertThatThrownBy(
            () ->
                runtime
                    .requestContext()
                    .modifyUser(
                        u ->
                            u.setTenant("tenant-A")
                                .setIsSystemUser(false)
                                .setIsInternalUser(false)
                                .setName("user-empty")
                                .setIsAuthenticated(true))
                    .run(
                        (Function<RequestContext, Result>)
                            ctx ->
                                service.run(
                                    Select.from("AICore.deployments")
                                        .where(
                                            d ->
                                                d.get("resourceGroup_resourceGroupId")
                                                    .eq("rg-empty-labels")))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }

  private void stubResourceGroupWithTenant(String rgId, String tenantId) {
    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
    when(label.getKey()).thenReturn(AICoreConfig.TENANT_LABEL_KEY);
    when(label.getValue()).thenReturn(tenantId);
    when(rg.getLabels()).thenReturn(List.of(label));
    when(resourceGroupApi.get(rgId)).thenReturn(rg);
  }

  private void stubDeploymentQuery() {
    AiDeploymentList emptyList = mock(AiDeploymentList.class);
    when(emptyList.getResources()).thenReturn(List.of());
    when(deploymentApi.query(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(emptyList);
  }
}
