/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.AiDeployment;
import com.sap.ai.sdk.core.model.AiDeploymentList;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentStatus;
import com.sap.ai.sdk.core.model.AiDeploymentTargetStatus;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceGroupCleanupExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Logger logger = LoggerFactory.getLogger(ResourceGroupCleanupExtension.class);

  private static final String OWNER_ENV_VAR = "CDS_AICORE_TEST_RESOURCE_GROUP";
  private static final String LOCAL_DEV_RG = "cap-java-ai-default";

  private static final Set<AiDeploymentStatus> TERMINAL_STATUSES =
      Set.of(AiDeploymentStatus.STOPPED, AiDeploymentStatus.DEAD, AiDeploymentStatus.COMPLETED);

  private static final int STOP_POLL_MAX_ATTEMPTS = 30;
  private static final long STOP_POLL_INTERVAL_MS = 2_000L;

  @Override
  public void beforeAll(ExtensionContext context) {
    cleanupOnce(context, "resourceGroupCleanupBeforeDone");
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Defer cleanup to the very end of the test suite using CloseableResource.
    // JUnit 5 invokes close() on CloseableResource entries in the global store only
    // after ALL test classes have completed, preventing premature resource group deletion.
    ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    store.getOrComputeIfAbsent(
        "resourceGroupCleanupShutdownHook",
        k -> (ExtensionContext.Store.CloseableResource) this::deleteOwnedResourceGroups);
  }

  private void cleanupOnce(ExtensionContext context, String storeKey) {
    ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    if (store.get(storeKey) != null) {
      return;
    }
    store.put(storeKey, true);
    deleteOwnedResourceGroups();
  }

  private void deleteOwnedResourceGroups() {
    String owner = System.getenv(OWNER_ENV_VAR);
    if (owner == null || owner.isBlank() || LOCAL_DEV_RG.equals(owner)) {
      logger.info(
          "Skipping resource group cleanup: {}={}, local-dev RGs persist across runs",
          OWNER_ENV_VAR,
          owner);
      return;
    }

    String labelSelector = BaseIntegrationTest.ITEST_OWNER_LABEL_KEY + "=" + owner;
    try {
      ResourceGroupApi rgApi = new ResourceGroupApi();
      DeploymentApi deploymentApi = new DeploymentApi();
      BckndResourceGroupList list =
          rgApi.getAll(null, null, null, null, null, null, List.of(labelSelector));

      for (BckndResourceGroup rg : list.getResources()) {
        String id = rg.getResourceGroupId();
        if (id == null || !ownsResourceGroup(rg, owner)) {
          logger.warn(
              "Server-side label filter returned RG {} that is not owned by {}; skipping",
              id,
              owner);
          continue;
        }
        try {
          stopDeploymentsInResourceGroup(deploymentApi, id);
          rgApi.delete(id);
          logger.info("Cleaned up integration test resource group: {}", id);
        } catch (Exception e) {
          logger.warn("Failed to delete resource group {}: {}", id, e.getMessage());
        }
      }
    } catch (Exception e) {
      logger.warn("Resource group cleanup failed: {}", e.getMessage());
    }
    // Invalidate cached deployment IDs so subsequent tests don't reference deleted resources.
    BaseIntegrationTest.clearDeploymentIdCache();
  }

  private boolean ownsResourceGroup(BckndResourceGroup rg, String owner) {
    return rg.getLabels().stream()
        .anyMatch(
            l ->
                BaseIntegrationTest.ITEST_OWNER_LABEL_KEY.equals(l.getKey())
                    && owner.equals(l.getValue()));
  }

  private void stopDeploymentsInResourceGroup(DeploymentApi api, String resourceGroupId) {
    AiDeploymentList list;
    try {
      list = api.query(resourceGroupId);
    } catch (Exception e) {
      logger.warn(
          "Failed to list deployments in resource group {}: {}", resourceGroupId, e.getMessage());
      return;
    }

    for (AiDeployment deployment : list.getResources()) {
      AiDeploymentStatus status = deployment.getStatus();
      if (status != null && TERMINAL_STATUSES.contains(status)) {
        continue;
      }
      try {
        api.modify(
            resourceGroupId,
            deployment.getId(),
            AiDeploymentModificationRequest.create()
                .targetStatus(AiDeploymentTargetStatus.STOPPED));
      } catch (Exception e) {
        logger.warn(
            "Failed to stop deployment {} in resource group {}: {}",
            deployment.getId(),
            resourceGroupId,
            e.getMessage());
      }
    }

    waitForDeploymentsStopped(api, resourceGroupId);
  }

  private void waitForDeploymentsStopped(DeploymentApi api, String resourceGroupId) {
    for (int i = 0; i < STOP_POLL_MAX_ATTEMPTS; i++) {
      AiDeploymentList list;
      try {
        list = api.query(resourceGroupId);
      } catch (Exception e) {
        return;
      }
      boolean allStopped =
          list.getResources().stream()
              .map(AiDeployment::getStatus)
              .allMatch(s -> s == null || TERMINAL_STATUSES.contains(s));
      if (allStopped) {
        return;
      }
      try {
        Thread.sleep(STOP_POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    logger.warn(
        "Deployments in resource group {} did not all reach STOPPED in time", resourceGroupId);
  }
}
