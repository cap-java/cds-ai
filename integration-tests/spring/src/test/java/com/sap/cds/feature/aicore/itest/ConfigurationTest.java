/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationTest extends BaseIntegrationTest {

  @Test
  void readAll_returnsConfigurations() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();
    Result result =
        service.run(
            Select.from("AICore.configurations")
                .where(c -> c.get("resourceGroup_resourceGroupId").eq(resourceGroup)));

    assertThat(result.list()).isNotNull();
  }

  @Test
  void readAll_filterByScenario() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();
    Result result =
        service.run(
            Select.from("AICore.configurations")
                .where(
                    c ->
                        c.get("scenarioId")
                            .eq("foundation-models")
                            .and(c.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    assertThat(result.list()).isNotNull();
  }

  @Test
  void create_andReadById() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();

    String configName = "itest-config-" + System.currentTimeMillis();
    Result created =
        service.run(
            Insert.into("AICore.configurations")
                .entry(
                    Map.of(
                        "name",
                        configName,
                        "executableId",
                        "aicore-sap",
                        "scenarioId",
                        "foundation-models",
                        "resourceGroup_resourceGroupId",
                        resourceGroup,
                        "parameterBindings",
                        List.of(
                            Map.of("key", "modelName", "value", "sap-rpt-1-small"),
                            Map.of("key", "modelVersion", "value", "latest")))));

    assertThat(created.list()).hasSize(1);
    String configId = (String) created.single().get("id");
    assertThat(configId).isNotNull();

    // Read back by ID
    Result readResult =
        service.run(
            Select.from("AICore.configurations")
                .where(
                    c ->
                        c.get("id")
                            .eq(configId)
                            .and(c.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    assertThat(readResult.list()).hasSize(1);
    Row row = readResult.single();
    assertThat(row.get("name")).isEqualTo(configName);
    assertThat(row.get("executableId")).isEqualTo("aicore-sap");
    assertThat(row.get("scenarioId")).isEqualTo("foundation-models");
  }

  @Test
  void create_withParameterBindings_mapsCorrectly() {
    CqnService service = getAICoreCqnService();
    String resourceGroup = getAICoreServiceImpl().getDefaultResourceGroup();

    String configName = "itest-params-" + System.currentTimeMillis();
    Result created =
        service.run(
            Insert.into("AICore.configurations")
                .entry(
                    Map.of(
                        "name",
                        configName,
                        "executableId",
                        "aicore-sap",
                        "scenarioId",
                        "foundation-models",
                        "resourceGroup_resourceGroupId",
                        resourceGroup,
                        "parameterBindings",
                        List.of(
                            Map.of("key", "param1", "value", "value1"),
                            Map.of("key", "param2", "value", "value2")))));

    String configId = (String) created.single().get("id");

    Result readResult =
        service.run(
            Select.from("AICore.configurations")
                .where(
                    c ->
                        c.get("id")
                            .eq(configId)
                            .and(c.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    Row row = readResult.single();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> bindings = (List<Map<String, Object>>) row.get("parameterBindings");
    assertThat(bindings).hasSize(2);
    assertThat(bindings)
        .anyMatch(b -> "param1".equals(b.get("key")) && "value1".equals(b.get("value")));
    assertThat(bindings)
        .anyMatch(b -> "param2".equals(b.get("key")) && "value2".equals(b.get("value")));
  }
}
