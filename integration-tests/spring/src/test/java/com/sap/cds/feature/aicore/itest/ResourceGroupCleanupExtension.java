/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that cleans up leaked {@code itest-rg-*} resource groups created by {@link
 * ResourceGroupTest}. The main test resource group ({@code itest-<run_id>-...}) is cleaned up by a
 * dedicated CI job after ALL parallel integration test jobs complete, to avoid one job's cleanup
 * affecting another job's deployment sharing the same AI Core model infrastructure.
 */
public class ResourceGroupCleanupExtension implements AfterAllCallback {

  private static final Logger logger = LoggerFactory.getLogger(ResourceGroupCleanupExtension.class);

  private static final String TEST_RG_PREFIX = "itest-rg-";

  @Override
  public void afterAll(ExtensionContext context) {
    ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    store.getOrComputeIfAbsent(
        "resourceGroupCleanupShutdownHook",
        k -> (ExtensionContext.Store.CloseableResource) this::deleteResourceGroupsByPrefix);
  }

  private void deleteResourceGroupsByPrefix() {
    try {
      ResourceGroupApi rgApi = new ResourceGroupApi();
      BckndResourceGroupList all = rgApi.getAll(null, null, null, null, null, null, null);
      for (BckndResourceGroup rg : all.getResources()) {
        String id = rg.getResourceGroupId();
        if (id != null && id.startsWith(TEST_RG_PREFIX)) {
          try {
            rgApi.delete(id);
            logger.info("Cleaned up leaked test resource group: {}", id);
          } catch (Exception e) {
            logger.warn("Failed to delete test resource group {}: {}", id, e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Prefix-based resource group cleanup failed: {}", e.getMessage());
    }
  }
}
