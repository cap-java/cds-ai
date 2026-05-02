/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.ai.client.AICoreClient;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.mt.SubscribeEventContext;
import com.sap.cds.services.mt.UnsubscribeEventContext;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test verifying the full AICoreSetup lifecycle against a real AI Core instance.
 * Requires an AI Core service instance bound to the app (VCAP_SERVICES).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AICoreSetupHandlerTest {

  private static final String TEST_TENANT = "it-test-tenant-" + System.currentTimeMillis();
  private static final String TENANT_LABEL_KEY = "ext.ai.sap.com/CDS_TENANT_ID";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger logger = LoggerFactory.getLogger(AICoreSetupHandlerTest.class);

  private AICoreSetupHandler cut;
  private AICoreClient client;
  private Map<String, Object> credentials;
  private String aiApiUrl;

  @BeforeAll
  void setup() throws Exception {
    String vcap = System.getenv("VCAP_SERVICES");
    String serviceKey = null;
    if (vcap != null) {
      Map<String, Object> vcapServices = MAPPER.readValue(vcap, new TypeReference<>() {});
      List<Map<String, Object>> aicoreBindings =
          MAPPER.convertValue(vcapServices.get("aicore"), new TypeReference<>() {});
      if (aicoreBindings != null && !aicoreBindings.isEmpty()) {
        serviceKey = MAPPER.writeValueAsString(aicoreBindings.get(0).get("credentials"));
      }
    }
    assumeTrue(
        serviceKey != null, "Skipping integration test: VCAP_SERVICES with aicore binding not set");

    credentials = MAPPER.readValue(serviceKey, new TypeReference<>() {});
    aiApiUrl = ((Map<?, ?>) credentials.get("serviceurls")).get("AI_API_URL").toString();

    System.setProperty("cds.multitenancy.enabled", "true");
    CdsEnvironment environment = mock(CdsEnvironment.class);
    cut = new AICoreSetupHandler(environment);
    client = new AICoreClient(cut);
  }

  @AfterAll
  void tearDown() {
    System.clearProperty("cds.multitenancy.enabled");
  }

  /**
   * Full lifecycle: 1. Subscribe → resource group created online + in local cache 2.
   * fetchPredictions → RPT-1 deployment created online + in local cache 3. Unsubscribe → resource
   * group deleted online + removed from both caches
   */
  @Test
  void lifecycle_subscribeCreatesPredictCreatesDeploymentUnsubscribeDeletesAll() throws Exception {

    // ── 1. Subscribe ──────────────────────────────────────────────────────
    SubscribeEventContext subCtx = mock(SubscribeEventContext.class);
    when(subCtx.getTenant()).thenReturn(TEST_TENANT);
    cut.afterSubscribe(subCtx);

    String token = fetchToken();
    String resourceGroupId = findResourceGroupByTenant(token, TEST_TENANT);
    assertThat(resourceGroupId)
        .as("Resource group should exist online after subscribe")
        .isNotNull();
    assertThat(cut.getTenantResourceGroupCache())
        .as("Resource group should be cached after subscribe")
        .containsKey(TEST_TENANT);

    // ── 2. fetchPredictions ───────────────────────────────────────────────
    // This triggers RPT-1 deployment creation + polling until RUNNING.
    // The inference call itself may fail (due to no meaningful data) but the deployment
    // must be RUNNING before inference is attempted.
    try {
      client.fetchPredictions(
          List.of(Map.of("ID", "1", "title", "test book")), List.of("genre"), TEST_TENANT);
    } catch (RuntimeException e) {
      // Inference errors are acceptable; we only assert the deployment was created.
    }

    token = fetchToken();
    String deploymentId = findDeploymentForResourceGroup(token, resourceGroupId);
    assertThat(deploymentId)
        .as("RPT-1 deployment should exist online after fetchPredictions")
        .isNotNull();
    assertThat(cut.getResourceGroupDeploymentCache())
        .as("Deployment should be cached after fetchPredictions")
        .containsEntry(resourceGroupId, deploymentId);

    // ── 3. Unsubscribe ────────────────────────────────────────────────────
    UnsubscribeEventContext unsubCtx = mock(UnsubscribeEventContext.class);
    when(unsubCtx.getTenant()).thenReturn(TEST_TENANT);
    cut.beforeUnsubscribe(unsubCtx);

    // Caches are cleared synchronously
    assertThat(cut.getTenantResourceGroupCache())
        .as("Resource group should be removed from cache after unsubscribe")
        .doesNotContainKey(TEST_TENANT);
    assertThat(cut.getResourceGroupDeploymentCache())
        .as("Deployment should be removed from cache after unsubscribe")
        .doesNotContainKey(resourceGroupId);

    // AI Core deletes resource groups asynchronously — poll until gone
    waitUntilResourceGroupGone(TEST_TENANT);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void waitUntilResourceGroupGone(String tenant) throws Exception {
    long delay = 500;
    int maxRetries = 10;
    for (int i = 0; i < maxRetries; i++) {
      Thread.sleep(delay);
      String id = findResourceGroupByTenant(fetchToken(), tenant);
      if (id == null) return;
      logger.debug(
          "Resource group still present after unsubscribe, retrying in {} ms ({}/{})",
          delay * 2,
          i + 1,
          maxRetries);
      delay *= 2;
    }
    assertThat(findResourceGroupByTenant(fetchToken(), tenant))
        .as("Resource group should be gone online after unsubscribe")
        .isNull();
  }

  private String findResourceGroupByTenant(String token, String tenant) throws Exception {
    String encoded = URLEncoder.encode(TENANT_LABEL_KEY + "=" + tenant, "UTF-8");
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder()
                    .uri(URI.create(aiApiUrl + "/v2/admin/resourceGroups?labelSelector=" + encoded))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    Map<String, Object> result = MAPPER.readValue(response.body(), new TypeReference<>() {});
    List<Map<String, Object>> resources =
        MAPPER.convertValue(result.get("resources"), new TypeReference<>() {});
    if (resources == null || resources.isEmpty()) return null;
    return resources.get(0).get("resourceGroupId").toString();
  }

  private String findDeploymentForResourceGroup(String token, String resourceGroupId)
      throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder()
                    .uri(URI.create(aiApiUrl + "/v2/lm/deployments"))
                    .header("Authorization", "Bearer " + token)
                    .header("AI-Resource-Group", resourceGroupId)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    Map<String, Object> result = MAPPER.readValue(response.body(), new TypeReference<>() {});
    List<Map<String, Object>> resources =
        MAPPER.convertValue(result.get("resources"), new TypeReference<>() {});
    if (resources == null || resources.isEmpty()) return null;
    return resources.stream()
        .filter(d -> "RUNNING".equals(d.get("status")) || "PENDING".equals(d.get("status")))
        .findFirst()
        .map(d -> d.get("id").toString())
        .orElse(null);
  }

  private String fetchToken() throws Exception {
    String body =
        "grant_type=client_credentials"
            + "&client_id="
            + URLEncoder.encode(credentials.get("clientid").toString(), "UTF-8")
            + "&client_secret="
            + URLEncoder.encode(credentials.get("clientsecret").toString(), "UTF-8");
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder()
                    .uri(URI.create(credentials.get("url") + "/oauth/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    Map<String, Object> data = MAPPER.readValue(response.body(), new TypeReference<>() {});
    return data.get("access_token").toString();
  }
}
