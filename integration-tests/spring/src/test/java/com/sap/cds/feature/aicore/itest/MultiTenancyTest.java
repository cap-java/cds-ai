/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.services.cds.RemoteService;
import org.junit.jupiter.api.Test;

class MultiTenancyTest extends BaseIntegrationTest {

  @Test
  void differentTenants_getDifferentResourceGroups() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantA = "itest-mt-a-" + System.currentTimeMillis();
    String tenantB = "itest-mt-b-" + System.currentTimeMillis();

    ResourceGroupContext rgCtxA = ResourceGroupContext.create();
    rgCtxA.setTenantId(tenantA);
    service.emit(rgCtxA);
    String rgA = rgCtxA.getResult();

    ResourceGroupContext rgCtxB = ResourceGroupContext.create();
    rgCtxB.setTenantId(tenantB);
    service.emit(rgCtxB);
    String rgB = rgCtxB.getResult();

    assertThat(rgA).isNotEqualTo(rgB);
    assertThat(rgA).contains(tenantA);
    assertThat(rgB).contains(tenantB);
  }

  @Test
  void resourceGroupPrefix_appliedCorrectly() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantA = "itest-prefix-" + System.currentTimeMillis();

    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    rgCtx.setTenantId(tenantA);
    service.emit(rgCtx);
    String rg = rgCtx.getResult();
    assertThat(rg).startsWith(config.resourceGroupPrefix());
  }

  @Test
  void singleTenancy_alwaysReturnsDefault() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    assumeFalse(config.multiTenancyEnabled(), "Multi-tenancy is enabled");

    ResourceGroupContext rgCtx1 = ResourceGroupContext.create();
    rgCtx1.setTenantId("tenant-x");
    service.emit(rgCtx1);
    String rg1 = rgCtx1.getResult();

    ResourceGroupContext rgCtx2 = ResourceGroupContext.create();
    rgCtx2.setTenantId("tenant-y");
    service.emit(rgCtx2);
    String rg2 = rgCtx2.getResult();

    assertThat(rg1).isEqualTo(config.defaultResourceGroup());
    assertThat(rg2).isEqualTo(config.defaultResourceGroup());
  }
}
