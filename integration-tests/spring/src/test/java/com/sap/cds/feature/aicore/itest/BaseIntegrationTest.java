/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.environment.CdsProperties;
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

  @Autowired protected MockMvc mockMvc;

  @Autowired protected CdsRuntime runtime;

  protected RemoteService getAICoreService() {
    return runtime.getServiceCatalog().getService(RemoteService.class, AICore.SERVICE_NAME);
  }

  protected AICoreConfig getAICoreConfig() {
    CdsProperties props = runtime.getEnvironment().getCdsProperties();
    String sidecarUrl = props.getMultiTenancy().getSidecar().getUrl();
    boolean mt = sidecarUrl != null && !sidecarUrl.isBlank();
    return AICoreConfig.from(runtime.getEnvironment(), mt);
  }

  protected RemoteService getAICoreRemoteService() {
    return getAICoreService();
  }

  protected String ensureRptDeploymentReady() {
    String resourceGroup = getAICoreConfig().defaultResourceGroup();
    return CACHED_DEPLOYMENT_IDS.computeIfAbsent(
        resourceGroup,
        rg -> {
          ensureResourceGroupProvisioned(getAICoreRemoteService(), rg);
          RemoteService service = getAICoreService();
          DeploymentIdContext depCtx = DeploymentIdContext.create();
          depCtx.setResourceGroupId(rg);
          depCtx.setSpec(RptModelSpec.rpt1());
          service.emit(depCtx);
          return depCtx.getResult();
        });
  }

  protected void ensureResourceGroupProvisioned(RemoteService service, String resourceGroup) {
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

  private boolean resourceGroupExists(RemoteService service, String resourceGroup) {
    Result all = service.run(Select.from("AICore.resourceGroups"));
    for (Row row : all) {
      if (resourceGroup.equals(row.get("resourceGroupId"))) {
        return true;
      }
    }
    return false;
  }

  private void waitForResourceGroupProvisioned(RemoteService service, String resourceGroup) {
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
