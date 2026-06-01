/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MultiTenancyTest extends BaseIntegrationTest {

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanup() {
    AbstractAICoreService service = getAICoreServiceImpl();
    if (tenantA != null) {
      service.clearTenantCache(tenantA);
      tenantA = null;
    }
    if (tenantB != null) {
      service.clearTenantCache(tenantB);
      tenantB = null;
    }
  }

  @Test
  void differentTenants_getDifferentResourceGroups() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeTrue(service.isMultiTenancyEnabled(), "Multi-tenancy is not enabled");
    tenantA = "itest-mt-a-" + System.currentTimeMillis();
    tenantB = "itest-mt-b-" + System.currentTimeMillis();

    String rgA = service.resourceGroupForTenant(tenantA);
    String rgB = service.resourceGroupForTenant(tenantB);

    assertThat(rgA).isNotEqualTo(rgB);
    assertThat(rgA).contains(tenantA);
    assertThat(rgB).contains(tenantB);
  }

  @Test
  void resourceGroupPrefix_appliedCorrectly() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeTrue(service.isMultiTenancyEnabled(), "Multi-tenancy is not enabled");
    tenantA = "itest-prefix-" + System.currentTimeMillis();

    String rg = service.resourceGroupForTenant(tenantA);
    assertThat(rg).startsWith(service.getResourceGroupPrefix());
  }

  @Test
  void cacheIsolation_perTenant() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeTrue(service.isMultiTenancyEnabled(), "Multi-tenancy is not enabled");
    tenantA = "itest-cache-a-" + System.currentTimeMillis();
    tenantB = "itest-cache-b-" + System.currentTimeMillis();

    String rgA = service.resourceGroupForTenant(tenantA);
    String rgB = service.resourceGroupForTenant(tenantB);

    assertThat(service.getTenantResourceGroupCache()).containsEntry(tenantA, rgA);
    assertThat(service.getTenantResourceGroupCache()).containsEntry(tenantB, rgB);
  }

  @Test
  void clearTenantCache_onlyAffectsTargetTenant() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeTrue(service.isMultiTenancyEnabled(), "Multi-tenancy is not enabled");
    tenantA = "itest-clear-a-" + System.currentTimeMillis();
    tenantB = "itest-clear-b-" + System.currentTimeMillis();

    service.resourceGroupForTenant(tenantA);
    String rgB = service.resourceGroupForTenant(tenantB);

    service.clearTenantCache(tenantA);

    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey(tenantA);
    assertThat(service.getTenantResourceGroupCache()).containsEntry(tenantB, rgB);
  }

  @Test
  void singleTenancy_alwaysReturnsDefault() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeFalse(service.isMultiTenancyEnabled(), "Multi-tenancy is enabled");
    String rg1 = service.resourceGroupForTenant("tenant-x");
    String rg2 = service.resourceGroupForTenant("tenant-y");

    assertThat(rg1).isEqualTo(service.getDefaultResourceGroup());
    assertThat(rg2).isEqualTo(service.getDefaultResourceGroup());
  }
}
