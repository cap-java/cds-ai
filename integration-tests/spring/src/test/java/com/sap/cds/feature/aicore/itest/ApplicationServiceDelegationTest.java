/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

class ApplicationServiceDelegationTest extends BaseIntegrationTest {

  @Test
  @WithMockUser(username = "test-user")
  void readConfigurations_viaApplicationService() throws Exception {
    mockMvc
        .perform(get("/odata/v4/TestService/Configurations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").isArray());
  }

  @Test
  @WithMockUser(username = "test-user")
  void readDeployments_viaApplicationService() throws Exception {
    mockMvc
        .perform(get("/odata/v4/TestService/Deployments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").isArray());
  }

  @Test
  @WithMockUser(username = "test-user")
  void readResourceGroups_viaApplicationService() throws Exception {
    mockMvc
        .perform(get("/odata/v4/TestService/ResourceGroups"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").isArray());
  }
}
