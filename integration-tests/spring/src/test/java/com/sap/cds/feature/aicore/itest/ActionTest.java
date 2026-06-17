/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.cds.RemoteService;
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
    RemoteService service = getAICoreService();
    assumeFalse(config.multiTenancyEnabled(), "Multi-tenancy is enabled");
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    rgCtx.setTenantId("any-tenant-id");
    service.emit(rgCtx);
    String result = rgCtx.getResult();
    assertThat(result).isEqualTo(config.defaultResourceGroup());
  }

  @Test
  void resourceGroupForTenant_multiTenancy_createsGroup() {
    AICoreConfig config = getAICoreConfig();
    RemoteService service = getAICoreService();
    assumeTrue(config.multiTenancyEnabled(), "Multi-tenancy is not enabled");
    String tenantId = "itest-action-tenant-" + System.currentTimeMillis();
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    rgCtx.setTenantId(tenantId);
    service.emit(rgCtx);
    String resourceGroupId = rgCtx.getResult();
    assertThat(resourceGroupId).startsWith(config.resourceGroupPrefix());
    assertThat(resourceGroupId).contains(tenantId);
  }

  @Test
  void deploymentId_returnsValidDeployment() {
    RemoteService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(resourceGroup);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);
    String deploymentId = depCtx.getResult();
    assertThat(deploymentId).isNotNull().isNotBlank();
  }

  @Test
  void deploymentId_cachedOnSecondCall() {
    RemoteService service = getAICoreService();
    String resourceGroup = getAICoreConfig().defaultResourceGroup();

    DeploymentIdContext depCtx1 = DeploymentIdContext.create();
    depCtx1.setResourceGroupId(resourceGroup);
    depCtx1.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx1);
    String first = depCtx1.getResult();

    DeploymentIdContext depCtx2 = DeploymentIdContext.create();
    depCtx2.setResourceGroupId(resourceGroup);
    depCtx2.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx2);
    String second = depCtx2.getResult();
    assertThat(second).isEqualTo(first);
  }

  @Disabled(
      "Stops the shared RPT deployment needed by subsequent Recommendation tests; "
          + "re-enable once test creates its own isolated deployment")
  @Test
  void stop_deployment_changesTargetStatus() {
    RemoteService service = getAICoreCqnService();
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
