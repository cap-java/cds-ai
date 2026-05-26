/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.recommendation.RptModelSpec;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.runtime.CdsRuntime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

  static final String ITEST_OWNER_LABEL_KEY = "ext.ai.sap.com/CDS_FEATURE_AI_ITEST_OWNER";

  private static final ConcurrentMap<String, String> CACHED_DEPLOYMENT_IDS =
      new ConcurrentHashMap<>();

  /** Clears the cached deployment IDs (used by cleanup extension after resource groups are deleted). */
  static void clearDeploymentIdCache() {
    CACHED_DEPLOYMENT_IDS.clear();
  }

  @Autowired protected MockMvc mockMvc;

  @Autowired protected CdsRuntime runtime;

  protected AICoreService getAICoreService() {
    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  protected CqnService getAICoreCqnService() {
    return (CqnService) getAICoreService();
  }

  protected String ensureRptDeploymentReady() {
    String resourceGroup = getAICoreService().getDefaultResourceGroup();
    return CACHED_DEPLOYMENT_IDS.computeIfAbsent(
        resourceGroup,
        rg -> {
          ensureResourceGroupProvisioned(getAICoreCqnService(), rg);
          return getAICoreService().deploymentId(rg, RptModelSpec.rpt1());
        });
  }

  protected void ensureResourceGroupProvisioned(CqnService service, String resourceGroup) {
    if (!resourceGroupExists(service, resourceGroup)) {
      logger.info("Creating resource group {} with itest owner label", resourceGroup);
      service.run(
          Insert.into("AICore.resourceGroups")
              .entry(
                  Map.of(
                      "resourceGroupId",
                      resourceGroup,
                      "labels",
                      List.of(Map.of("key", ITEST_OWNER_LABEL_KEY, "value", resourceGroup)))));
    }
    waitForResourceGroupProvisioned(service, resourceGroup);
  }

  private boolean resourceGroupExists(CqnService service, String resourceGroup) {
    Result all = service.run(Select.from("AICore.resourceGroups"));
    for (Row row : all) {
      if (resourceGroup.equals(row.get("resourceGroupId"))) {
        return true;
      }
    }
    return false;
  }

  private void waitForResourceGroupProvisioned(CqnService service, String resourceGroup) {
    for (int i = 0; i < 30; i++) {
      Result all = service.run(Select.from("AICore.resourceGroups"));
      for (Row row : all) {
        if (resourceGroup.equals(row.get("resourceGroupId"))) {
          String status = (String) row.get("status");
          if ("PROVISIONED".equals(status)) {
            return;
          }
          break;
        }
      }
      sleepQuietly(2000L);
    }
    throw new IllegalStateException(
        "Resource group " + resourceGroup + " did not reach PROVISIONED status");
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting", e);
    }
  }
}
