/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation.itest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sap.cds.feature.Application;
import com.sap.cds.feature.aicore.itest.BaseIntegrationTest;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = Application.class)
class NonStandardKeyRecommendationTest extends BaseIntegrationTest {

  private static final String SERVICE_PATH = "/odata/v4/RecommendationTestService";
  private static final String BOOKS_URL = SERVICE_PATH + "/BooksWithCustomKey";
  private static final String ORDER_ITEMS_URL = SERVICE_PATH + "/OrderItems";

  @BeforeAll
  void setupContextData() {
    ensureRptDeploymentReady();

    PersistenceService db =
        runtime
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);

    db.run(
        Insert.into("itest.Categories")
            .entries(
                List.of(
                    Map.of("ID", 10, "name", "Fiction"),
                    Map.of("ID", 20, "name", "Science"),
                    Map.of("ID", 30, "name", "History"))));

    db.run(
        Insert.into("itest.BooksWithCustomKey")
            .entries(
                List.of(
                    Map.of("isbn", "978-0-01", "title", "Book A", "price", 10, "category_ID", 10),
                    Map.of("isbn", "978-0-02", "title", "Book B", "price", 20, "category_ID", 20),
                    Map.of(
                        "isbn", "978-0-03", "title", "Book C", "price", 30, "category_ID", 30))));

    db.run(
        Insert.into("itest.OrderItems")
            .entries(
                List.of(
                    Map.of(
                        "order_no",
                        1,
                        "item_no",
                        1,
                        "product",
                        "Widget",
                        "quantity",
                        5,
                        "category_ID",
                        10),
                    Map.of(
                        "order_no",
                        1,
                        "item_no",
                        2,
                        "product",
                        "Gadget",
                        "quantity",
                        3,
                        "category_ID",
                        20),
                    Map.of(
                        "order_no",
                        2,
                        "item_no",
                        1,
                        "product",
                        "Doohickey",
                        "quantity",
                        7,
                        "category_ID",
                        30))));
  }

  @Test
  @WithMockUser(username = "test-user")
  void customKey_readDraft_returnsSapRecommendations() throws Exception {
    String isbn = createBookDraft("{\"isbn\":\"978-TEST-01\"}");

    mockMvc
        .perform(get(bookDraftUrl(isbn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").exists())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isArray())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isNotEmpty());

    deleteBookDraft(isbn);
  }

  @Test
  @WithMockUser(username = "test-user")
  void composedKey_readDraft_returnsSapRecommendations() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post(ORDER_ITEMS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"order_no\":99,\"item_no\":99}"))
            .andExpect(status().isCreated())
            .andReturn();

    String body = createResult.getResponse().getContentAsString();
    int orderNo = JsonPath.read(body, "$.order_no");
    int itemNo = JsonPath.read(body, "$.item_no");

    mockMvc
        .perform(get(orderItemDraftUrl(orderNo, itemNo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SAP_Recommendations").exists())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isArray())
        .andExpect(jsonPath("$.SAP_Recommendations.category_ID").isNotEmpty());

    mockMvc.perform(delete(orderItemDraftUrl(orderNo, itemNo))).andExpect(status().isNoContent());
  }

  private String createBookDraft(String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post(BOOKS_URL).contentType(MediaType.APPLICATION_JSON).content(content))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.isbn");
  }

  private void deleteBookDraft(String isbn) throws Exception {
    mockMvc.perform(delete(bookDraftUrl(isbn))).andExpect(status().isNoContent());
  }

  private String bookDraftUrl(String isbn) {
    return BOOKS_URL + "(isbn='" + isbn + "',IsActiveEntity=false)";
  }

  private String orderItemDraftUrl(int orderNo, int itemNo) {
    return ORDER_ITEMS_URL
        + "(order_no="
        + orderNo
        + ",item_no="
        + itemNo
        + ",IsActiveEntity=false)";
  }
}
