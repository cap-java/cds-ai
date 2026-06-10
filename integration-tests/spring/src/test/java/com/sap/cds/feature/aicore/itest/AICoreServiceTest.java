/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
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
    AbstractAICoreService service = getAICoreServiceImpl();
    if (!service.isMultiTenancyEnabled()) {
      String result = service.resourceGroupForTenant("any-tenant");
      assertThat(result).isEqualTo(service.getDefaultResourceGroup());
    }
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsOrFindsGroup() {
    AbstractAICoreService service = getAICoreServiceImpl();
    if (service.isMultiTenancyEnabled()) {
      String tenantId = "itest-svc-tenant-" + System.currentTimeMillis();
      try {
        String resourceGroupId = service.resourceGroupForTenant(tenantId);
        assertThat(resourceGroupId).startsWith(service.getResourceGroupPrefix());
        assertThat(resourceGroupId).contains(tenantId);

        // Second call should return cached value
        String cached = service.resourceGroupForTenant(tenantId);
        assertThat(cached).isEqualTo(resourceGroupId);
      } finally {
        service.clearTenantCache(tenantId);
      }
    }
  }

  @Test
  void deploymentId_returnsDeploymentId() {
    AbstractAICoreService service = getAICoreServiceImpl();
    String resourceGroup = service.getDefaultResourceGroup();

    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(deploymentId).isNotNull().isNotBlank();

    // Second call should use cache
    String cached = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(cached).isEqualTo(deploymentId);
  }

  @Test
  void clearTenantCache_removesEntries() {
    AbstractAICoreService service = getAICoreServiceImpl();
    String tenantId = "itest-cache-tenant";
    String fakeRg = "fake-rg";
    String fakeKey = fakeRg + "::" + RptModelSpec.CONFIG_NAME;
    service.getTenantResourceGroupCache().put(tenantId, fakeRg);
    service.getResourceGroupDeploymentCache().put(fakeKey, "fake-deployment");

    service.clearTenantCache(tenantId);

    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey(tenantId);
    assertThat(service.getResourceGroupDeploymentCache()).doesNotContainKey(fakeKey);
  }

  @Test
  void configProperties_areApplied() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assertThat(service.getRetry()).isNotNull();
    assertThat(service.getDefaultResourceGroup()).isNotBlank();
    assertThat(service.getResourceGroupPrefix()).isNotBlank();
  }
}
