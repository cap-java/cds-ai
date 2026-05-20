/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

class ODataTest extends BaseIntegrationTest {

  @Test
  @WithMockUser(username = "test-user")
  void getProducts_returnsODataResponse() throws Exception {
    mockMvc
        .perform(get("/odata/v4/TestService/Products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").isArray());
  }

  @Test
  void getProducts_unauthenticated_returnsOk() throws Exception {
    mockMvc.perform(get("/odata/v4/TestService/Products")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "test-user")
  void getServiceMetadata_returnsMetadata() throws Exception {
    mockMvc.perform(get("/odata/v4/TestService/$metadata")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "test-user")
  void appContext_startsWithAICoreService() {
    assertThat(runtime).isNotNull();
    assertThat(getAICoreService()).isNotNull();
  }
}
