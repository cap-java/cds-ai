/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest.mt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.itest.mt.utils.SubscriptionEndpointClient;
import com.sap.cds.services.runtime.CdsRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-with-tenants")
class TenantIsolationTest {

  @Autowired MockMvc client;
  @Autowired ObjectMapper objectMapper;
  @Autowired CdsRuntime runtime;

  SubscriptionEndpointClient subscriptionEndpointClient;

  @BeforeEach
  void setup() {
    subscriptionEndpointClient = new SubscriptionEndpointClient(objectMapper, client);
  }

  @Test
  void multiTenancyEnabled() {
    AICoreService service = getService();
    assertThat(service.isMultiTenancyEnabled()).isTrue();
  }

  @Test
  void differentTenants_getDifferentResourceGroups() throws Exception {
    AICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-1");
    subscriptionEndpointClient.subscribeTenant("tenant-2");

    String rg1 = service.getTenantResourceGroupCache().get("tenant-1");
    String rg2 = service.getTenantResourceGroupCache().get("tenant-2");

    assertThat(rg1).isNotNull();
    assertThat(rg2).isNotNull();
    assertThat(rg1).isNotEqualTo(rg2);
  }

  @Test
  void resourceGroupPrefix_applied() throws Exception {
    AICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-1");
    String rg = service.getTenantResourceGroupCache().get("tenant-1");

    assertThat(rg).startsWith(service.getResourceGroupPrefix());
  }

  @Test
  void clearTenantCache_onlyAffectsTarget() throws Exception {
    AICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-1");
    subscriptionEndpointClient.subscribeTenant("tenant-2");

    String rg2 = service.getTenantResourceGroupCache().get("tenant-2");

    service.clearTenantCache("tenant-1");

    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey("tenant-1");
    assertThat(service.getTenantResourceGroupCache()).containsEntry("tenant-2", rg2);
  }

  private AICoreService getService() {
    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  @AfterEach
  void tearDown() {
    try {
      subscriptionEndpointClient.unsubscribeTenant("tenant-1");
    } catch (Throwable ignored) {
    }
    try {
      subscriptionEndpointClient.unsubscribeTenant("tenant-2");
    } catch (Throwable ignored) {
    }
    try {
      subscriptionEndpointClient.unsubscribeTenant("tenant-3");
    } catch (Throwable ignored) {
    }
  }
}
