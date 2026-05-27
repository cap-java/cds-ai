/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import com.sap.cds.feature.recommendation.RptModelSpec;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.cds.CqnService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ResourceGroupCleanupExtension.class)
class ActionTest extends BaseIntegrationTest {

  @BeforeAll
  void ensureResourceGroupReady() {
    ensureResourceGroupProvisioned(getAICoreCqnService(), getAICoreServiceImpl().getDefaultResourceGroup());
  }

  @Test
  void resourceGroupForTenant_singleTenancy_returnsDefault() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeFalse(service.isMultiTenancyEnabled(), "Multi-tenancy is enabled");
    String result = service.resourceGroupForTenant("any-tenant-id");
    assertThat(result).isEqualTo(service.getDefaultResourceGroup());
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsGroup() {
    AbstractAICoreService service = getAICoreServiceImpl();
    assumeTrue(service.isMultiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantId = "itest-action-tenant-" + System.currentTimeMillis();
    try {
      String resourceGroupId = service.resourceGroupForTenant(tenantId);
      assertThat(resourceGroupId).startsWith(service.getResourceGroupPrefix());
      assertThat(resourceGroupId).contains(tenantId);
    } finally {
      service.clearTenantCache(tenantId);
    }
  }

  @Test
  void deploymentId_returnsValidDeployment() {
    AbstractAICoreService service = getAICoreServiceImpl();
    String resourceGroup = service.getDefaultResourceGroup();

    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(deploymentId).isNotNull().isNotBlank();
  }

  @Test
  void deploymentId_cachedOnSecondCall() {
    AbstractAICoreService service = getAICoreServiceImpl();
    String resourceGroup = service.getDefaultResourceGroup();

    String first = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    String second = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(second).isEqualTo(first);
  }

  @Disabled("Stops the shared RPT deployment needed by subsequent Recommendation tests; "
      + "re-enable once test creates its own isolated deployment")
  @Test
  void stop_deployment_changesTargetStatus() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();

    Result deployments =
        service.run(
            Select.from("AICore.deployments")
                .where(d -> d.get("resourceGroup_resourceGroupId").eq(resourceGroup)));

    String deploymentId = null;
    for (Row row : deployments) {
      if ("RUNNING".equals(row.get("targetStatus"))) {
        deploymentId = (String) row.get("id");
        break;
      }
    }

    assumeFalse(deploymentId == null, "No running deployment available");

    final String targetId = deploymentId;

    service.run(
        Update.entity("AICore.deployments")
            .where(d -> d.get("id").eq(targetId))
            .data(Map.of("targetStatus", "STOPPED", "resourceGroup_resourceGroupId", resourceGroup)));

    Result readResult =
        service.run(
            Select.from("AICore.deployments")
                .where(
                    d ->
                        d.get("id")
                            .eq(targetId)
                            .and(d.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    assertThat(readResult.list()).hasSize(1);
    Row row = readResult.single();
    assertThat(row.get("targetStatus")).isIn("STOPPED", "STOPPING");
  }

  @Test
  void resolveResourceGroupFromKeys_directKey() {
    AbstractAICoreService service = getAICoreServiceImpl();
    Map<String, Object> keys = Map.of("resourceGroup_resourceGroupId", "my-rg");
    String resolved = service.resolveResourceGroupFromKeys(keys);
    assertThat(resolved).isEqualTo("my-rg");
  }

  @Test
  void resolveResourceGroupFromKeys_nestedMap() {
    AbstractAICoreService service = getAICoreServiceImpl();
    Map<String, Object> keys = Map.of("resourceGroup", Map.of("resourceGroupId", "nested-rg"));
    String resolved = service.resolveResourceGroupFromKeys(keys);
    assertThat(resolved).isEqualTo("nested-rg");
  }
}
