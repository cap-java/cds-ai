/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.cds.CqnService;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class DeploymentTest extends BaseIntegrationTest {

  @Test
  void readAll_returnsDeployments() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();
    Result result =
        service.run(
            Select.from("AICore.deployments")
                .where(d -> d.get("resourceGroup_resourceGroupId").eq(resourceGroup)));

    assertThat(result.list()).isNotNull();
  }

  @Test
  void readSingle_returnsDeploymentDetails() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();
    Result all =
        service.run(
            Select.from("AICore.deployments")
                .where(d -> d.get("resourceGroup_resourceGroupId").eq(resourceGroup)));

    assumeFalse(all.list().isEmpty(), "No deployments available");

    String id = (String) all.list().get(0).get("id");
    Result single =
        service.run(
            Select.from("AICore.deployments")
                .where(
                    d ->
                        d.get("id")
                            .eq(id)
                            .and(d.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    assertThat(single.list()).hasSize(1);
    Row row = single.single();
    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("configurationId")).isNotNull();
    assertThat(row.get("status")).isNotNull();
  }

  @Disabled("Stops the shared RPT deployment needed by subsequent Recommendation tests; "
      + "re-enable once test creates its own isolated deployment")
  @Test
  void update_targetStatus_stopsRunningDeployment() {
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

    assumeFalse(deploymentId == null, "No running deployment available to test");

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
}
