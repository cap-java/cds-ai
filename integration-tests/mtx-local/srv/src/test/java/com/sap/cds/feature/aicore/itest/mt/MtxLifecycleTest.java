/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest.mt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

/** Verifies the {@code AICoreSetupHandler} lifecycle is idempotent across repeated invocations. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-with-tenants")
class MtxLifecycleTest {

  private static final String TENANT = "tenant-3";

  @Autowired MockMvc client;
  @Autowired ObjectMapper objectMapper;
  @Autowired CdsRuntime runtime;

  SubscriptionEndpointClient subscriptionEndpointClient;

  @BeforeEach
  void setup() {
    subscriptionEndpointClient = new SubscriptionEndpointClient(objectMapper, client);
  }

  @AfterEach
  void tearDown() {
    try {
      subscriptionEndpointClient.unsubscribeTenant(TENANT);
    } catch (Exception ignored) {
    }
  }

  @Test
  void unsubscribe_isIdempotent() throws Exception {
    AbstractAICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant(TENANT);
    subscriptionEndpointClient.unsubscribeTenant(TENANT);

    assertThatCode(() -> subscriptionEndpointClient.unsubscribeTenant(TENANT))
        .doesNotThrowAnyException();
    assertThat(service.getTenantResourceGroupCache()).doesNotContainKey(TENANT);
  }

  @Test
  void subscribeUnsubscribe_repeatedTwice_completesCleanly() throws Exception {
    AbstractAICoreService service = getService();

    for (int i = 0; i < 2; i++) {
      subscriptionEndpointClient.subscribeTenant(TENANT);
      assertThat(service.getTenantResourceGroupCache()).containsKey(TENANT);

      subscriptionEndpointClient.unsubscribeTenant(TENANT);
      assertThat(service.getTenantResourceGroupCache()).doesNotContainKey(TENANT);
    }
  }

  private AbstractAICoreService getService() {
    return (AbstractAICoreService) runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }
}
