/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceGroupCleanupExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Logger logger = LoggerFactory.getLogger(ResourceGroupCleanupExtension.class);

  @Override
  public void beforeAll(ExtensionContext context) {
    cleanupOnce(context, "resourceGroupCleanupBeforeDone");
  }

  @Override
  public void afterAll(ExtensionContext context) {
    cleanupOnce(context, "resourceGroupCleanupAfterDone");
  }

  private void cleanupOnce(ExtensionContext context, String storeKey) {
    ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
    if (store.get(storeKey) != null) {
      return;
    }
    store.put(storeKey, true);
    deleteTestResourceGroups();
  }

  private void deleteTestResourceGroups() {
    String envKey = System.getenv("AICORE_SERVICE_KEY");
    if (envKey == null || envKey.isBlank()) {
      logger.debug("No AI Core binding available, skipping resource group cleanup.");
      return;
    }

    try {
      ResourceGroupApi api = new ResourceGroupApi();
      BckndResourceGroupList list = api.getAll(null, null, null, null, null, null, null);

      for (BckndResourceGroup rg : list.getResources()) {
        String id = rg.getResourceGroupId();
        if (id != null && !"default".equals(id)) {
          try {
            api.delete(id);
            logger.info("Cleaned up integration test resource group: {}", id);
          } catch (Exception e) {
            logger.warn("Failed to delete resource group {}: {}", id, e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Resource group cleanup failed: {}", e.getMessage());
    }
  }
}
