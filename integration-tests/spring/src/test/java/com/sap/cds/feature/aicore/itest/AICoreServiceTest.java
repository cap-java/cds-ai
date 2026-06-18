/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.services.cds.RemoteService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AICoreServiceTest extends BaseIntegrationTest {

  @BeforeAll
  void prepareDeployment() {
    ensureRptDeploymentReady();
  }

  @Test
  void service_isRegistered() {
    assertThat(getAICoreService()).isNotNull();
    assertThat(getAICoreService()).isInstanceOf(RemoteService.class);
  }

  @Test
  void resourceGroupForTenant_singleTenancy_returnsDefault() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    if (!config.multiTenancyEnabled()) {
      ResourceGroupContext rgCtx = ResourceGroupContext.create();
      rgCtx.setTenantId("any-tenant");
      service.emit(rgCtx);
      String result = rgCtx.getResult();
      assertThat(result).isEqualTo(config.defaultResourceGroup());
    }
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsOrFindsGroup() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    if (config.multiTenancyEnabled()) {
      String tenantId = "itest-svc-tenant-" + System.currentTimeMillis();
      ResourceGroupContext rgCtx = ResourceGroupContext.create();
      rgCtx.setTenantId(tenantId);
      service.emit(rgCtx);
      String resourceGroupId = rgCtx.getResult();
      assertThat(resourceGroupId).startsWith(config.resourceGroupPrefix());
      assertThat(resourceGroupId).contains(tenantId);

      // Second call should return cached value
      ResourceGroupContext rgCtx2 = ResourceGroupContext.create();
      rgCtx2.setTenantId(tenantId);
      service.emit(rgCtx2);
      String cached = rgCtx2.getResult();
      assertThat(cached).isEqualTo(resourceGroupId);
    }
  }

  @Test
  void deploymentId_returnsDeploymentId() {
    RemoteService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(resourceGroup);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);
    String deploymentId = depCtx.getResult();
    assertThat(deploymentId).isNotNull().isNotBlank();

    // Second call should use cache
    DeploymentIdContext depCtx2 = DeploymentIdContext.create();
    depCtx2.setResourceGroupId(resourceGroup);
    depCtx2.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx2);
    String cached = depCtx2.getResult();
    assertThat(cached).isEqualTo(deploymentId);
  }

  @Test
  void configProperties_areApplied() {
    AICoreConfig config = getAICoreConfig();
    assertThat(config.defaultResourceGroup()).isNotBlank();
    assertThat(config.resourceGroupPrefix()).isNotBlank();
  }
}
