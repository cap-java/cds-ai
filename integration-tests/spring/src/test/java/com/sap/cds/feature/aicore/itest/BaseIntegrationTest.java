/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  @Autowired protected MockMvc mockMvc;

  @Autowired protected CdsRuntime runtime;

  protected AICoreService getAICoreService() {
    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  protected CqnService getAICoreCqnService() {
    return (CqnService) getAICoreService();
  }

  protected String getOrCreateRptConfig(CqnService service, String resourceGroup) {
    Result configs =
        service.run(
            Select.from("AICore.configurations")
                .where(
                    c ->
                        c.get("scenarioId")
                            .eq("foundation-models")
                            .and(c.get("resourceGroup_resourceGroupId").eq(resourceGroup))));

    for (Row row : configs) {
      if ("sap-rpt-1-small".equals(row.get("name"))) {
        return (String) row.get("id");
      }
    }

    Result created =
        service.run(
            Insert.into("AICore.configurations")
                .entry(
                    Map.of(
                        "name", "sap-rpt-1-small",
                        "executableId", "aicore-sap",
                        "scenarioId", "foundation-models",
                        "resourceGroup_resourceGroupId", resourceGroup,
                        "parameterBindings",
                            List.of(
                                Map.of("key", "modelName", "value", "sap-rpt-1-small"),
                                Map.of("key", "modelVersion", "value", "latest")))));

    return (String) created.single().get("id");
  }
}
