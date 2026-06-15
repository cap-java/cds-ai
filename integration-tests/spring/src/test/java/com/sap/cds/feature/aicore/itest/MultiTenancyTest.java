/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import org.junit.jupiter.api.Test;

class MultiTenancyTest extends BaseIntegrationTest {

  @Test
  void differentTenants_getDifferentResourceGroups() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantA = "itest-mt-a-" + System.currentTimeMillis();
    String tenantB = "itest-mt-b-" + System.currentTimeMillis();

    String rgA = service.resourceGroupForTenant(tenantA);
    String rgB = service.resourceGroupForTenant(tenantB);

    assertThat(rgA).isNotEqualTo(rgB);
    assertThat(rgA).contains(tenantA);
    assertThat(rgB).contains(tenantB);
  }

  @Test
  void resourceGroupPrefix_appliedCorrectly() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantA = "itest-prefix-" + System.currentTimeMillis();

    String rg = service.resourceGroupForTenant(tenantA);
    assertThat(rg).startsWith(config.resourceGroupPrefix());
  }

  @Test
  void singleTenancy_alwaysReturnsDefault() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    assumeFalse(config.multiTenancyEnabled(), "Multi-tenancy is enabled");
    String rg1 = service.resourceGroupForTenant("tenant-x");
    String rg2 = service.resourceGroupForTenant("tenant-y");

    assertThat(rg1).isEqualTo(config.defaultResourceGroup());
    assertThat(rg2).isEqualTo(config.defaultResourceGroup());
  }
}
