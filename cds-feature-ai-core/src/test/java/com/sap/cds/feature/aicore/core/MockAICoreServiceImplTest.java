/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsService;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.runtime.CdsRuntime;
import org.junit.jupiter.api.Test;

class MockAICoreServiceImplTest {

  private MockAICoreServiceImpl createService(boolean multiTenancyEnabled) {
    CdsRuntime runtime = mock(CdsRuntime.class);
    CdsEnvironment env = mock(CdsEnvironment.class);
    when(runtime.getEnvironment()).thenReturn(env);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    when(env.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("test-rg");
    when(env.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("prefix-");
    return new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime, multiTenancyEnabled);
  }

  @Test
  void defaultConstructor_setsMultiTenancyFalse() {
    CdsRuntime runtime = mock(CdsRuntime.class);
    CdsEnvironment env = mock(CdsEnvironment.class);
    when(runtime.getEnvironment()).thenReturn(env);
    CdsModel cdsModel = mock(CdsModel.class);
    when(runtime.getCdsModel()).thenReturn(cdsModel);
    when(cdsModel.getService("AICore")).thenReturn(mock(CdsService.class));
    when(env.getProperty(eq("cds.ai.core.resourceGroup"), eq(String.class), any()))
        .thenReturn("default");
    when(env.getProperty(eq("cds.ai.core.resourceGroupPrefix"), eq(String.class), any()))
        .thenReturn("cds-");

    MockAICoreServiceImpl service = new MockAICoreServiceImpl(AICoreService.DEFAULT_NAME, runtime);
    assertThat(service.isMultiTenancyEnabled()).isFalse();
  }

  @Test
  void mtConstructor_setsMultiTenancyTrue() {
    MockAICoreServiceImpl service = createService(true);
    assertThat(service.isMultiTenancyEnabled()).isTrue();
  }

  @Test
  void resourceGroupForTenant_mtDisabled_alwaysReturnsDefault() {
    MockAICoreServiceImpl service = createService(false);
    assertThat(service.resourceGroupForTenant("tenant-x")).isEqualTo("test-rg");
    assertThat(service.resourceGroupForTenant("tenant-y")).isEqualTo("test-rg");
  }

  @Test
  void resourceGroupForTenant_mtEnabled_returnsPrefixedTenantId() {
    MockAICoreServiceImpl service = createService(true);
    String rg = service.resourceGroupForTenant("my-tenant");
    assertThat(rg).isEqualTo("prefix-my-tenant");
  }

  @Test
  void resourceGroupForTenant_mtEnabled_cachesResult() {
    MockAICoreServiceImpl service = createService(true);
    String first = service.resourceGroupForTenant("t1");
    String second = service.resourceGroupForTenant("t1");
    assertThat(first).isSameAs(second);
    assertThat(service.getTenantResourceGroupCache()).containsKey("t1");
  }

  @Test
  void clearTenantCache_removesCorrectEntries() {
    MockAICoreServiceImpl service = createService(true);
    service.resourceGroupForTenant("t1");
    service.resourceGroupForTenant("t2");
    var spec = new com.sap.cds.feature.aicore.api.ModelDeploymentSpec(
        "scenario", "exec", "cfg1", java.util.List.of(), d -> true);
    service.deploymentId("prefix-t1", spec);

    service.clearTenantCache("t1");

    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey("t1");
    assertThat(service.getTenantResourceGroupCache()).containsKey("t2");
    assertThat(service.getResourceGroupDeploymentCache()).doesNotContainKeys("prefix-t1::cfg1");
  }

  @Test
  void getRetry_returnsNonNull() {
    MockAICoreServiceImpl service = createService(false);
    assertThat(service.getRetry()).isNotNull();
  }

  @Test
  void getDefaultResourceGroup_readsFromConfig() {
    MockAICoreServiceImpl service = createService(false);
    assertThat(service.getDefaultResourceGroup()).isEqualTo("test-rg");
  }

  @Test
  void getResourceGroupPrefix_readsFromConfig() {
    MockAICoreServiceImpl service = createService(false);
    assertThat(service.getResourceGroupPrefix()).isEqualTo("prefix-");
  }
}
