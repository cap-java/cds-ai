/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
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
    assertThat(getAICoreService()).isInstanceOf(AICoreService.class);
  }

  @Test
  void resourceGroupForTenant_singleTenancy_returnsDefault() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    if (!config.multiTenancyEnabled()) {
      String result = service.resourceGroupForTenant("any-tenant");
      assertThat(result).isEqualTo(config.defaultResourceGroup());
    }
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsOrFindsGroup() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    if (config.multiTenancyEnabled()) {
      String tenantId = "itest-svc-tenant-" + System.currentTimeMillis();
      String resourceGroupId = service.resourceGroupForTenant(tenantId);
      assertThat(resourceGroupId).startsWith(config.resourceGroupPrefix());
      assertThat(resourceGroupId).contains(tenantId);

      // Second call should return cached value
      String cached = service.resourceGroupForTenant(tenantId);
      assertThat(cached).isEqualTo(resourceGroupId);
    }
  }

  @Test
  void deploymentId_returnsDeploymentId() {
    AICoreService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(deploymentId).isNotNull().isNotBlank();

    // Second call should use cache
    String cached = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(cached).isEqualTo(deploymentId);
  }

  @Test
  void configProperties_areApplied() {
    AICoreConfig config = getAICoreConfig();
    assertThat(config.defaultResourceGroup()).isNotBlank();
    assertThat(config.resourceGroupPrefix()).isNotBlank();
  }
}
