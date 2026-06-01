/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest.mt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
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
class SubscribeUnsubscribeTest {

  private static final String PRODUCTS_URL = "/odata/v4/MtTestService/Products";

  @Autowired MockMvc client;
  @Autowired ObjectMapper objectMapper;
  @Autowired CdsRuntime runtime;

  SubscriptionEndpointClient subscriptionEndpointClient;

  @BeforeEach
  void setup() {
    subscriptionEndpointClient = new SubscriptionEndpointClient(objectMapper, client);
  }

  @Test
  void subscribeTenant_thenServiceIsReachable() throws Exception {
    subscriptionEndpointClient.subscribeTenant("tenant-3");

    client
        .perform(get(PRODUCTS_URL).with(httpBasic("user-in-tenant-3", "")))
        .andExpect(status().isOk());
  }

  @Test
  void subscribeTenant_createsResourceGroup() throws Exception {
    AbstractAICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-3");

    assertThat(service.isMultiTenancyEnabled()).isTrue();
    assertThat(service.getTenantResourceGroupCache()).containsKey("tenant-3");
  }

  @Test
  void unsubscribeTenant_clearsCaches() throws Exception {
    AbstractAICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-3");

    assertThat(service.getTenantResourceGroupCache()).containsKey("tenant-3");

    subscriptionEndpointClient.unsubscribeTenant("tenant-3");

    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey("tenant-3");
  }

  @Test
  void unsubscribeTenant_thenServiceFails() throws Exception {
    subscriptionEndpointClient.subscribeTenant("tenant-3");

    client
        .perform(get(PRODUCTS_URL).with(httpBasic("user-in-tenant-3", "")))
        .andExpect(status().isOk());

    subscriptionEndpointClient.unsubscribeTenant("tenant-3");

    client
        .perform(get(PRODUCTS_URL).with(httpBasic("user-in-tenant-3", "")))
        .andExpect(status().isInternalServerError());
  }

  @AfterEach
  void tearDown() {
    try {
      subscriptionEndpointClient.unsubscribeTenant("tenant-3");
    } catch (Exception ignored) {
    }
  }

  private AbstractAICoreService getService() {
    return (AbstractAICoreService) runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }
}
