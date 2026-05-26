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
import java.util.Set;
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

  private static final Set<String> ALIVE_DEPLOYMENT_STATUSES =
      Set.of("RUNNING", "PENDING", "UNKNOWN", "INITIAL");

  private static final int POLL_MAX_ATTEMPTS = 18;
  private static final long POLL_INITIAL_DELAY_MS = 300L;
  private static final long POLL_MAX_DELAY_MS = 30_000L;

  private static final ConcurrentMap<String, String> CACHED_DEPLOYMENT_IDS =
      new ConcurrentHashMap<>();

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

  protected String getOrCreateRptDeployment(CqnService service, String resourceGroup) {
    String configId = getOrCreateRptConfig(service, resourceGroup);

    Result existing =
        service.run(
            Select.from("AICore.deployments")
                .where(d -> d.get("resourceGroup_resourceGroupId").eq(resourceGroup)));

    String deploymentId = null;
    for (Row row : existing) {
      if (configId.equals(row.get("configurationId"))) {
        String status = (String) row.get("status");
        if (status == null || ALIVE_DEPLOYMENT_STATUSES.contains(status)) {
          deploymentId = (String) row.get("id");
          break;
        }
      }
    }

    if (deploymentId == null) {
      Result created =
          service.run(
              Insert.into("AICore.deployments")
                  .entry(
                      Map.of(
                          "configurationId",
                          configId,
                          "resourceGroup_resourceGroupId",
                          resourceGroup)));
      deploymentId = (String) created.single().get("id");
      logger.info(
          "Created RPT-1 deployment {} in resource group {}, polling for RUNNING",
          deploymentId,
          resourceGroup);
    }

    return waitForDeploymentRunning(service, resourceGroup, deploymentId);
  }

  protected String ensureRptDeploymentReady() {
    String resourceGroup = getAICoreService().getDefaultResourceGroup();
    return CACHED_DEPLOYMENT_IDS.computeIfAbsent(
        resourceGroup,
        rg -> {
          CqnService service = getAICoreCqnService();
          ensureResourceGroupProvisioned(service, rg);
          return getOrCreateRptDeployment(service, rg);
        });
  }

  private void ensureResourceGroupProvisioned(CqnService service, String resourceGroup) {
    if (!resourceGroupExists(service, resourceGroup)) {
      logger.info("Creating resource group {}", resourceGroup);
      service.run(
          Insert.into("AICore.resourceGroups").entry(Map.of("resourceGroupId", resourceGroup)));
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

  private String waitForDeploymentRunning(
      CqnService service, String resourceGroup, String deploymentId) {
    for (int i = 0; i < POLL_MAX_ATTEMPTS; i++) {
      Result result =
          service.run(
              Select.from("AICore.deployments")
                  .where(
                      d ->
                          d.get("resourceGroup_resourceGroupId")
                              .eq(resourceGroup)
                              .and(d.get("id").eq(deploymentId))));
      if (!result.list().isEmpty()) {
        String status = (String) result.single().get("status");
        if ("RUNNING".equals(status)) {
          return deploymentId;
        }
        logger.info(
            "Deployment {} status {}, retry {}/{}", deploymentId, status, i + 1, POLL_MAX_ATTEMPTS);
      }
      long delay = Math.min(POLL_INITIAL_DELAY_MS * (1L << i), POLL_MAX_DELAY_MS);
      sleepQuietly(delay);
    }
    throw new IllegalStateException(
        "Deployment "
            + deploymentId
            + " in resource group "
            + resourceGroup
            + " did not reach RUNNING status after "
            + POLL_MAX_ATTEMPTS
            + " attempts");
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
