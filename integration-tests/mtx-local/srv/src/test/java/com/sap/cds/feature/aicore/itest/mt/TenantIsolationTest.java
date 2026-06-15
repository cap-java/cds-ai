/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.itest.mt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreConfig;
import com.sap.cds.feature.aicore.itest.mt.utils.SubscriptionEndpointClient;
import com.sap.cds.services.environment.CdsProperties;
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
    AICoreConfig config = getConfig();
    assertThat(config.multiTenancyEnabled()).isTrue();
  }

  @Test
  void differentTenants_getDifferentResourceGroups() throws Exception {
    AICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-1");
    subscriptionEndpointClient.subscribeTenant("tenant-2");

    String rg1 = service.resourceGroupForTenant("tenant-1");
    String rg2 = service.resourceGroupForTenant("tenant-2");

    assertThat(rg1).isNotNull();
    assertThat(rg2).isNotNull();
    assertThat(rg1).isNotEqualTo(rg2);
  }

  @Test
  void resourceGroupPrefix_applied() throws Exception {
    AICoreConfig config = getConfig();
    AICoreService service = getService();

    subscriptionEndpointClient.subscribeTenant("tenant-1");
    String rg = service.resourceGroupForTenant("tenant-1");

    assertThat(rg).startsWith(config.resourceGroupPrefix());
  }

  private AICoreService getService() {
    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  private AICoreConfig getConfig() {
    CdsProperties props = runtime.getEnvironment().getCdsProperties();
    String sidecarUrl = props.getMultiTenancy().getSidecar().getUrl();
    boolean mt = sidecarUrl != null && !sidecarUrl.isBlank();
    return AICoreConfig.from(runtime.getEnvironment(), mt);
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
