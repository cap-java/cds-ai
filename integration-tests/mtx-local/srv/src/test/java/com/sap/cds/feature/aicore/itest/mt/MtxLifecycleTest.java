/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest.mt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.itest.mt.utils.SubscriptionEndpointClient;
import com.sap.cds.services.cds.RemoteService;
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
    subscriptionEndpointClient.subscribeTenant(TENANT);
    subscriptionEndpointClient.unsubscribeTenant(TENANT);

    assertThatCode(() -> subscriptionEndpointClient.unsubscribeTenant(TENANT))
        .doesNotThrowAnyException();
  }

  @Test
  void subscribeUnsubscribe_repeatedTwice_completesCleanly() throws Exception {
    RemoteService service = getService();

    for (int i = 0; i < 2; i++) {
      subscriptionEndpointClient.subscribeTenant(TENANT);
      // After subscribe, the service should resolve a resource group for this tenant
      ResourceGroupContext rgCtx = ResourceGroupContext.create();
      rgCtx.setTenantId(TENANT);
      service.emit(rgCtx);
      String rg = rgCtx.getResult();
      assertThat(rg).isNotNull().isNotBlank();

      subscriptionEndpointClient.unsubscribeTenant(TENANT);
    }
  }

  private RemoteService getService() {
    return runtime.getServiceCatalog().getService(RemoteService.class, AICore.SERVICE_NAME);
  }
}
