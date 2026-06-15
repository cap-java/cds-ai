/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
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
    ensureResourceGroupProvisioned(getAICoreCqnService(), getAICoreConfig().defaultResourceGroup());
  }

  @Test
  void resourceGroupForTenant_singleTenancy_returnsDefault() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    assumeFalse(config.multiTenancyEnabled(), "Multi-tenancy is enabled");
    String result = service.resourceGroupForTenant("any-tenant-id");
    assertThat(result).isEqualTo(config.defaultResourceGroup());
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsGroup() {
    AICoreConfig config = getAICoreConfig();
    AICoreService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantId = "itest-action-tenant-" + System.currentTimeMillis();
    String resourceGroupId = service.resourceGroupForTenant(tenantId);
    assertThat(resourceGroupId).startsWith(config.resourceGroupPrefix());
    assertThat(resourceGroupId).contains(tenantId);
  }

  @Test
  void deploymentId_returnsValidDeployment() {
    AICoreService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    String deploymentId = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(deploymentId).isNotNull().isNotBlank();
  }

  @Test
  void deploymentId_cachedOnSecondCall() {
    AICoreService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    String first = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    String second = service.deploymentId(resourceGroup, RptModelSpec.rpt1());
    assertThat(second).isEqualTo(first);
  }

  @Disabled(
      "Stops the shared RPT deployment needed by subsequent Recommendation tests; "
          + "re-enable once test creates its own isolated deployment")
  @Test
  void stop_deployment_changesTargetStatus() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

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
            .data(
                Map.of("targetStatus", "STOPPED", "resourceGroup_resourceGroupId", resourceGroup)));

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
}
