/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.feature.ai.client.setup.AICoreSetupHandler;
import com.sap.cds.services.environment.CdsEnvironment;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

/**
 * Integration test verifying the full prediction flow against a real AI Core instance.
 *
 * <p>Required environment variables: AICORE_SERVICE_KEY – Full AI Core service key JSON: {
 * "clientid": "...", "clientsecret": "...", "url": "...", "serviceurls": { "AI_API_URL": "..." } }
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AICoreClientPredictionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AICoreClient cut;
  private Map<String, Object> credentials;
  private String resourceGroup;

  @BeforeAll
  void setup() throws Exception {
    String serviceKey = System.getenv("AICORE_SERVICE_KEY");
    assumeTrue(serviceKey != null, "Skipping integration test: AICORE_SERVICE_KEY env var not set");

    credentials = MAPPER.readValue(serviceKey, new TypeReference<>() {});
    resourceGroup = "default";
    CdsEnvironment environment = Mockito.mock(CdsEnvironment.class);
    cut = new AICoreClient(new AICoreSetupHandler(environment));
  }

  /**
   * Full prediction flow: 1. Check deployments in the resource group 2. If no RUNNING rpt-1
   * deployment exists, create one and wait for it to reach RUNNING 3. Call fetchPredictions with a
   * small context row and a [PREDICT] row 4. Verify predictions are returned
   */
  @Test
  void prediction_deploymentMissingOrPresent_returnsPredictions() throws Exception {
    String aiApiUrl = ((Map<?, ?>) credentials.get("serviceurls")).get("AI_API_URL").toString();
    String token = fetchToken();

    // Step 1+2: ensure a RUNNING rpt-1 deployment exists (create if missing)
    ensureRunningRpt1Deployment(aiApiUrl, token);

    // Step 3: build rows — two context rows + one [PREDICT] row
    Map<String, Object> contextRow1 = new HashMap<>();
    contextRow1.put("ID", "ctx-1");
    contextRow1.put("genre_ID", 10);
    contextRow1.put("title", "Eleonora");

    Map<String, Object> contextRow2 = new HashMap<>();
    contextRow2.put("ID", "ctx-2");
    contextRow2.put("genre_ID", 20);
    contextRow2.put("title", "Another Book");

    Map<String, Object> predictRow = new HashMap<>();
    predictRow.put("ID", "predict-1");
    predictRow.put("genre_ID", "[PREDICT]");
    predictRow.put("title", "Eleonora");

    List<Map<String, Object>> predictions =
        cut.fetchPredictions(
            List.of(contextRow1, contextRow2, predictRow), List.of("genre_ID"), null);

    // Step 4: verify
    assertThat(predictions)
        .as("Should return at least one prediction result")
        .isNotNull()
        .isNotEmpty();

    Map<String, Object> prediction = predictions.get(0);
    assertThat(prediction).containsKey("genre_ID");
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Checks whether a RUNNING rpt-1 deployment exists. If not, creates one and polls until it
   * reaches RUNNING (up to ~5 minutes with exponential back-off).
   */
  private void ensureRunningRpt1Deployment(String aiApiUrl, String token) throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // Check existing deployments
    HttpResponse<String> listResponse =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(aiApiUrl + "/v2/lm/deployments"))
                .header("Authorization", "Bearer " + token)
                .header("AI-Resource-Group", resourceGroup)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    Map<String, Object> listResult =
        MAPPER.readValue(listResponse.body(), new TypeReference<>() {});
    List<Map<String, Object>> deployments =
        MAPPER.convertValue(listResult.get("resources"), new TypeReference<>() {});

    boolean hasRunning =
        deployments != null
            && deployments.stream()
                .anyMatch(
                    d ->
                        d.get("configurationName") != null
                            && d.get("configurationName").toString().contains("rpt-1")
                            && "RUNNING".equals(d.get("status")));

    if (hasRunning) {
      return; // nothing to do
    }

    // Find the rpt-1 configuration
    HttpResponse<String> cfgResponse =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(aiApiUrl + "/v2/lm/configurations"))
                .header("Authorization", "Bearer " + token)
                .header("AI-Resource-Group", resourceGroup)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    Map<String, Object> cfgResult = MAPPER.readValue(cfgResponse.body(), new TypeReference<>() {});
    List<Map<String, Object>> configurations =
        MAPPER.convertValue(cfgResult.get("resources"), new TypeReference<>() {});
    assertThat(configurations)
        .as(
            "No rpt-1 configuration found in resource group "
                + resourceGroup
                + " — cannot create a deployment. Ensure the configuration exists.")
        .isNotNull()
        .isNotEmpty();

    String configurationId =
        configurations.stream()
            .filter(c -> c.get("name") != null && c.get("name").toString().contains("rpt-1"))
            .map(c -> c.get("id").toString())
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "No rpt-1 configuration found in resource group: " + resourceGroup));

    // Create the deployment
    String createBody = MAPPER.writeValueAsString(Map.of("configurationId", configurationId));
    HttpResponse<String> createResponse =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(aiApiUrl + "/v2/lm/deployments"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("AI-Resource-Group", resourceGroup)
                .POST(HttpRequest.BodyPublishers.ofString(createBody))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    Map<String, Object> created = MAPPER.readValue(createResponse.body(), new TypeReference<>() {});
    String deploymentId = created.get("id").toString();

    // Poll until RUNNING
    for (int i = 0; i < 10; i++) {
      long delay = 300L * (1L << i);
      System.out.printf(
          "Waiting %dms for deployment %s to reach RUNNING (attempt %d/10)%n",
          delay, deploymentId, i + 1);
      Thread.sleep(delay);

      token = fetchToken();
      HttpResponse<String> statusResponse =
          client.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(aiApiUrl + "/v2/lm/deployments/" + deploymentId))
                  .header("Authorization", "Bearer " + token)
                  .header("AI-Resource-Group", resourceGroup)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      Map<String, Object> status =
          MAPPER.readValue(statusResponse.body(), new TypeReference<>() {});
      if ("RUNNING".equals(status.get("status"))) {
        return;
      }
    }
    throw new AssertionError(
        "Deployment " + deploymentId + " did not reach RUNNING within timeout");
  }

  /** Fetches a fresh OAuth token using service key credentials. */
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
