/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ResourceGroupCleanupExtension.class)
class ResourceGroupTest extends BaseIntegrationTest {

  private static final String TEST_RG_PREFIX = "itest-rg-";
  private String createdResourceGroupId;

  @AfterEach
  void cleanup() {
    if (createdResourceGroupId != null) {
      try {
        getAICoreCqnService()
            .run(
                Delete.from("AICore.resourceGroups")
                    .where(r -> r.get("resourceGroupId").eq(createdResourceGroupId)));
      } catch (Exception ignored) {
      }
      createdResourceGroupId = null;
    }
  }

  @Test
  @Disabled("#24: requires AI Core tenant-admin scopes for /v2/admin/resourceGroups; CI key is read-only")
  void create_andRead_resourceGroup() {
    createdResourceGroupId = TEST_RG_PREFIX + System.currentTimeMillis();
    CqnService service = getAICoreCqnService();

    service.run(
        Insert.into("AICore.resourceGroups")
            .entry(Map.of("resourceGroupId", createdResourceGroupId)));

    Result result =
        service.run(
            Select.from("AICore.resourceGroups")
                .where(r -> r.get("resourceGroupId").eq(createdResourceGroupId)));

    assertThat(result.list()).hasSize(1);
    Row row = result.single();
    assertThat(row.get("resourceGroupId")).isEqualTo(createdResourceGroupId);
    assertThat(row.get("status")).isNotNull();
  }

  @Test
  @Disabled("#24: requires AI Core tenant-admin scopes for /v2/admin/resourceGroups; CI key is read-only")
  void create_withTenantLabel_andFilterByTenant() {
    String tenantId = "itest-tenant-" + System.currentTimeMillis();
    createdResourceGroupId = TEST_RG_PREFIX + tenantId;
    CqnService service = getAICoreCqnService();

    service.run(
        Insert.into("AICore.resourceGroups")
            .entry(Map.of("resourceGroupId", createdResourceGroupId, "tenantId", tenantId)));

    Result result =
        service.run(
            Select.from("AICore.resourceGroups").where(r -> r.get("tenantId").eq(tenantId)));

    assertThat(result.list()).isNotEmpty();
    Row row = result.first().orElseThrow();
    assertThat(row.get("resourceGroupId")).isEqualTo(createdResourceGroupId);
  }

  @Test
  void readAll_returnsResourceGroups() {
    CqnService service = getAICoreCqnService();
    Result result = service.run(Select.from("AICore.resourceGroups"));
    assertThat(result.list()).isNotNull();
  }

  @Test
  @Disabled("#24: requires AI Core tenant-admin scopes for /v2/admin/resourceGroups; CI key is read-only")
  void create_withLabels() {
    createdResourceGroupId = TEST_RG_PREFIX + "labels-" + System.currentTimeMillis();
    CqnService service = getAICoreCqnService();

    service.run(
        Insert.into("AICore.resourceGroups")
            .entry(
                Map.of(
                    "resourceGroupId",
                    createdResourceGroupId,
                    "labels",
                    List.of(
                        Map.of(
                            "key", "ext.ai.sap.com/itest-key",
                            "value", "itest-value")))));

    Result result =
        service.run(
            Select.from("AICore.resourceGroups")
                .where(r -> r.get("resourceGroupId").eq(createdResourceGroupId)));

    assertThat(result.list()).hasSize(1);
    Row row = result.single();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> labels = (List<Map<String, Object>>) row.get("labels");
    assertThat(labels).isNotEmpty();
  }

  @Test
  @Disabled("#24: requires AI Core tenant-admin scopes for /v2/admin/resourceGroups; CI key is read-only")
  void delete_resourceGroup() throws InterruptedException {
    String rgId = TEST_RG_PREFIX + "del-" + System.currentTimeMillis();
    CqnService service = getAICoreCqnService();

    service.run(Insert.into("AICore.resourceGroups").entry(Map.of("resourceGroupId", rgId)));

    waitForResourceGroupProvisioned(service, rgId);

    service.run(Delete.from("AICore.resourceGroups").where(r -> r.get("resourceGroupId").eq(rgId)));

    createdResourceGroupId = null; // already deleted
  }

  private void waitForResourceGroupProvisioned(CqnService service, String rgId)
      throws InterruptedException {
    for (int i = 0; i < 30; i++) {
      Result result =
          service.run(
              Select.from("AICore.resourceGroups").where(r -> r.get("resourceGroupId").eq(rgId)));
      if (!result.list().isEmpty()) {
        String status = (String) result.single().get("status");
        if ("PROVISIONED".equals(status)) {
          return;
        }
      }
      Thread.sleep(2000);
    }
  }
}
