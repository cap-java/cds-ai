/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation.itest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sap.cds.feature.aicore.itest.BaseIntegrationTest;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecommendationTest extends BaseIntegrationTest {

  private static final String SERVICE_PATH = "/odata/v4/RecommendationTestService";
  private static final String TASKS_URL = SERVICE_PATH + "/Tasks";

  private static final List<Integer> CATEGORY_IDS = List.of(1, 2, 3);
  private static final List<String> PRIORITY_CODES = List.of("HIGH", "MED", "LOW");

  @BeforeAll
  void setupContextData() {
    PersistenceService db =
        runtime
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

    db.run(
        Insert.into("itest.Categories")
            .entries(
                List.of(
                    Map.of("ID", 1, "name", "Development"),
                    Map.of("ID", 2, "name", "Testing"),
                    Map.of("ID", 3, "name", "Documentation"))));

    db.run(
        Insert.into("itest.Priorities")
            .entries(
                List.of(
                    Map.of("code", "HIGH", "name", "High Priority"),
                    Map.of("code", "MED", "name", "Medium Priority"),
                    Map.of("code", "LOW", "name", "Low Priority"))));

    db.run(
        Insert.into("itest.Tasks")
            .entries(
                List.of(
                    Map.of(
                        "ID",
                        UUID.randomUUID().toString(),
                        "title",
                        "Implement login",
                        "description",
                        "Add OAuth login flow",
                        "effort",
                        8,
                        "category_ID",
                        1,
                        "priority_code",
                        "HIGH"),
                    Map.of(
                        "ID",
                        UUID.randomUUID().toString(),
                        "title",
                        "Write unit tests",
                        "description",
                        "Cover auth module",
                        "effort",
                        5,
                        "category_ID",
                        2,
                        "priority_code",
                        "MED"),
                    Map.of(
                        "ID",
                        UUID.randomUUID().toString(),
                        "title",
                        "Update API docs",
                        "description",
                        "Document new endpoints",
                        "effort",
                        3,
                        "category_ID",
                        3,
                        "priority_code",
                        "LOW"))));
  }

  @Test
  @WithMockUser(username = "test-user")
  void readDraft_returnsSapRecommendations() throws Exception {
    String draftId = createDraft("{}");

    mockMvc
        .perform(get(draftUrl(draftId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").exists())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isArray())
        .andExpect(jsonPath("$.SAP_Recommendations.priority_code").isArray());

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void readActiveEntity_noRecommendations() throws Exception {
    String draftId =
        createDraft("{\"title\":\"Active test\",\"category_ID\":1,\"priority_code\":\"HIGH\"}");
    activateDraft(draftId);

    mockMvc
        .perform(get(activeUrl(draftId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").doesNotExist());
  }

  @Test
  @WithMockUser(username = "test-user")
  void readMultipleRows_noRecommendations() throws Exception {
    mockMvc
        .perform(get(TASKS_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").isArray())
        .andExpect(jsonPath("$.value[0].SAP_Recommendations").doesNotExist());
  }

  @Test
  @WithMockUser(username = "test-user")
  void readNonDraftEntity_noRecommendations() throws Exception {
    mockMvc
        .perform(get("/odata/v4/TestService/Products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value[0].SAP_Recommendations").doesNotExist());
  }

  @Test
  @WithMockUser(username = "test-user")
  void readDraft_allColumnsFilled_noRecommendations() throws Exception {
    String draftId = createDraft("{\"category_ID\":1,\"priority_code\":\"HIGH\"}");

    mockMvc
        .perform(get(draftUrl(draftId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").doesNotExist());

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void readDraft_someColumnsFilled_returnsPartialRecommendations() throws Exception {
    String draftId = createDraft("{\"category_ID\":2}");

    mockMvc
        .perform(get(draftUrl(draftId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").exists())
        .andExpect(jsonPath("$.SAP_Recommendations.priority_code").isArray())
        .andExpect(jsonPath("$.SAP_Recommendations.priority_code").isNotEmpty())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isEmpty());

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void recommendations_haveCorrectStructure() throws Exception {
    String draftId = createDraft("{}");

    MvcResult result =
        mockMvc
            .perform(get(draftUrl(draftId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.SAP_Recommendations").exists())
            .andReturn();

    String body = result.getResponse().getContentAsString();

    List<Map<String, Object>> categoryRecs =
        JsonPath.read(body, "$.SAP_Recommendations.category_ID");
    assertThat(categoryRecs).hasSize(1);
    Map<String, Object> rec = categoryRecs.get(0);
    assertThat(rec).containsKey("RecommendedFieldValue");
    assertThat(rec).containsKey("RecommendedFieldDescription");
    assertThat(rec).containsEntry("RecommendedFieldScoreValue", 0.5);
    assertThat(rec).containsEntry("RecommendedFieldIsSuggestion", true);

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void recommendations_integerValueParsedCorrectly() throws Exception {
    String draftId = createDraft("{}");

    MvcResult result =
        mockMvc.perform(get(draftUrl(draftId))).andExpect(status().isOk()).andReturn();

    String body = result.getResponse().getContentAsString();
    List<Map<String, Object>> categoryRecs =
        JsonPath.read(body, "$.SAP_Recommendations.category_ID");
    assertThat(categoryRecs).isNotEmpty();

    Object value = categoryRecs.get(0).get("RecommendedFieldValue");
    assertThat(value).isInstanceOf(Integer.class);
    assertThat(CATEGORY_IDS).contains((Integer) value);

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void recommendations_stringValueParsedCorrectly() throws Exception {
    String draftId = createDraft("{}");

    MvcResult result =
        mockMvc.perform(get(draftUrl(draftId))).andExpect(status().isOk()).andReturn();

    String body = result.getResponse().getContentAsString();
    List<Map<String, Object>> priorityRecs =
        JsonPath.read(body, "$.SAP_Recommendations.priority_code");
    assertThat(priorityRecs).isNotEmpty();

    Object value = priorityRecs.get(0).get("RecommendedFieldValue");
    assertThat(value).isInstanceOf(String.class);
    assertThat(PRIORITY_CODES).contains((String) value);

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void recommendations_textResolution_resolvesDescription() throws Exception {
    String draftId = createDraft("{}");

    MvcResult result =
        mockMvc.perform(get(draftUrl(draftId))).andExpect(status().isOk()).andReturn();

    String body = result.getResponse().getContentAsString();
    List<Map<String, Object>> categoryRecs =
        JsonPath.read(body, "$.SAP_Recommendations.category_ID");
    assertThat(categoryRecs).isNotEmpty();

    String description = (String) categoryRecs.get(0).get("RecommendedFieldDescription");
    assertThat(description).isIn("Development", "Testing", "Documentation");

    deleteDraft(draftId);
  }

  @Test
  @WithMockUser(username = "test-user")
  void notEnoughContextRows_noRecommendations() throws Exception {
    PersistenceService db =
        runtime
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

    db.run(Delete.from("itest.Tasks"));

    try {
      String draftId = createDraft("{}");

      mockMvc
          .perform(get(draftUrl(draftId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.SAP_Recommendations").doesNotExist());

      deleteDraft(draftId);
    } finally {
      db.run(
          Insert.into("itest.Tasks")
              .entries(
                  List.of(
                      Map.of(
                          "ID",
                          UUID.randomUUID().toString(),
                          "title",
                          "Implement login",
                          "description",
                          "Add OAuth login flow",
                          "effort",
                          8,
                          "category_ID",
                          1,
                          "priority_code",
                          "HIGH"),
                      Map.of(
                          "ID",
                          UUID.randomUUID().toString(),
                          "title",
                          "Write unit tests",
                          "description",
                          "Cover auth module",
                          "effort",
                          5,
                          "category_ID",
                          2,
                          "priority_code",
                          "MED"),
                      Map.of(
                          "ID",
                          UUID.randomUUID().toString(),
                          "title",
                          "Update API docs",
                          "description",
                          "Document new endpoints",
                          "effort",
                          3,
                          "category_ID",
                          3,
                          "priority_code",
                          "LOW"))));
    }
  }

  private String createDraft(String body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post(TASKS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.ID");
  }

  private void activateDraft(String id) throws Exception {
    mockMvc
        .perform(
            post(draftUrl(id) + "/RecommendationTestService.draftActivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  private void deleteDraft(String id) throws Exception {
    mockMvc.perform(delete(draftUrl(id))).andExpect(status().isNoContent());
  }

  private String draftUrl(String id) {
    return TASKS_URL + "(ID=" + id + ",IsActiveEntity=false)";
  }

  private String activeUrl(String id) {
    return TASKS_URL + "(ID=" + id + ",IsActiveEntity=true)";
  }
}
