/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.ResultBuilder;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.Service;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.impl.utils.CdsServiceUtils;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.RequestContext;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FioriRecommendationHandlerTest {

  private static CdsRuntime runtime;
  private static PersistenceService db;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private AICoreService aiCoreService;

  private FioriRecommendationHandler cut;
  private RecommendationClient predictionClient;

  @BeforeAll
  static void bootRuntime() {
    db = mock(PersistenceService.class);
    when(db.getName()).thenReturn(PersistenceService.DEFAULT_NAME);
    runtime =
        CdsRuntimeConfigurer.create()
            .cdsModel("model/csn.json")
            .serviceConfigurations()
            .service(db)
            .complete();
  }

  @BeforeEach
  void setup() {
    reset(db);
    when(db.getName()).thenReturn(PersistenceService.DEFAULT_NAME);
    predictionClient = randomPickClient();
    cut = new FioriRecommendationHandler(aiCoreService, (service, tenantId) -> predictionClient);
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void emptyRows_returnsEarlyWithoutPredictions() {
    runIn(
        () -> {
          CdsReadEventContext ctx = readContext("test.Books", List.of());
          cut.afterRead(ctx, List.of());
          verifyNoInteractions(db);
        });
  }

  @Test
  void multipleRows_returnsEarlyWithoutPredictions() {
    runIn(
        () -> {
          List<Map<String, Object>> rows =
              List.of(
                  Map.of("ID", "1", "IsActiveEntity", false),
                  Map.of("ID", "2", "IsActiveEntity", false));
          CdsReadEventContext ctx = readContext("test.Books", rows);
          cut.afterRead(ctx, dataList(rows));
          verifyNoInteractions(db);
        });
  }

  @Test
  void activeEntity_returnsEarlyWithoutPredictions() {
    runIn(
        () -> {
          List<Map<String, Object>> rows = List.of(Map.of("ID", "1", "IsActiveEntity", true));
          CdsReadEventContext ctx = readContext("test.Books", rows);
          cut.afterRead(ctx, dataList(rows));
          verifyNoInteractions(db);
        });
  }

  @Test
  void noPredictionColumns_returnsEarlyWithoutPredictions() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("title", "foo");
          CdsReadEventContext ctx = readContext("test.PlainEntity", List.of(row));
          cut.afterRead(ctx, dataList(row));
          verifyNoInteractions(db);
        });
  }

  @Test
  void notEnoughContextRows_returnsEarlyWithoutRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("genre_ID", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class)))
              .thenReturn(
                  ResultBuilder.selectedRows(List.of(Map.of("ID", "x1", "genre_ID", 12))).result());
          cut.afterRead(ctx, dataList(row));
          assertThat(row).doesNotContainKey("SAP_Recommendations");
        });
  }

  @Test
  void allColumnsAlreadyFilled_returnsEarlyWithoutRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("genre_ID", 16);
          row.put("currency_code", "USD");
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
          cut.afterRead(ctx, dataList(row));
          assertThat(row).doesNotContainKey("SAP_Recommendations");
        });
  }

  @Test
  void emptyPredictions_returnsEarlyWithoutRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("genre_ID", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
          predictionClient = (rows, cols, idx) -> List.of();
          cut.afterRead(ctx, dataList(row));
          assertThat(row).doesNotContainKey("SAP_Recommendations");
        });
  }

  @Test
  void multiplePredictions_returnsEarlyWithoutRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("genre_ID", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
          predictionClient =
              (rows, cols, idx) ->
                  List.of(
                      CdsData.create(Map.of("ID", "id-1")), CdsData.create(Map.of("ID", "id-2")));
          cut.afterRead(ctx, dataList(row));
          assertThat(row).doesNotContainKey("SAP_Recommendations");
        });
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void draftRow_withGenreAndCurrency_addsSapRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = new HashMap<>();
          row.put("ID", "a009c640-434a-4542-ac68-51b400c880ec");
          row.put("IsActiveEntity", false);
          row.put("genre_ID", null);
          row.put("currency_code", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class)))
              .thenReturn(
                  ResultBuilder.selectedRows(
                          new ArrayList<>(
                              List.of(
                                  Map.of("ID", "id-1", "genre_ID", 12, "currency_code", "USD"),
                                  Map.of("ID", "id-2", "genre_ID", 16, "currency_code", "GBP"))))
                      .result(),
                  ResultBuilder.selectedRows(List.of()).result(),
                  ResultBuilder.selectedRows(List.of()).result());
          cut.afterRead(ctx, dataList(row));
          assertThat(row).containsKey("SAP_Recommendations");
          Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
          assertThat((List<?>) recs.get("genre_ID")).hasSize(1);
          assertThat((List<?>) recs.get("currency_code")).hasSize(1);
        });
  }

  @Test
  void blobAndVectorFields_areExcludedFromContextSelect() {
    runIn(
        () -> {
          Map<String, Object> row = draftRow("genre_ID", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          ArgumentCaptor<CqnSelect> selectCaptor = ArgumentCaptor.forClass(CqnSelect.class);
          when(db.run(selectCaptor.capture())).thenReturn(twoContextRows());
          cut.afterRead(ctx, dataList(row));
          String selectSql = selectCaptor.getAllValues().get(0).toString();
          assertThat(selectSql).doesNotContain("image");
          assertThat(selectSql).doesNotContain("embedding");
          assertThat(selectSql).contains("genre_ID");
        });
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void nonIdKey_usesSyntheticKeyColumn() {
    runIn(
        () -> {
          Map<String, Object> row = new HashMap<>();
          row.put("isbn", "978-3-16");
          row.put("IsActiveEntity", false);
          row.put("category_ID", null);
          CdsReadEventContext ctx = readContext("test.IsbnBooks", List.of(row));
          when(db.run(any(CqnSelect.class)))
              .thenReturn(
                  ResultBuilder.selectedRows(
                          new ArrayList<>(
                              List.of(
                                  new HashMap<>(Map.of("isbn", "978-1-01", "category_ID", 1)),
                                  new HashMap<>(Map.of("isbn", "978-1-02", "category_ID", 2)))))
                      .result());
          cut.afterRead(ctx, dataList(row));
          assertThat(row).containsKey("SAP_Recommendations");
          Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
          assertThat((List<?>) recs.get("category_ID")).hasSize(1);
        });
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void composedKeys_usesSyntheticKeyColumn() {
    runIn(
        () -> {
          Map<String, Object> row = new HashMap<>();
          row.put("order_ID", 1);
          row.put("item_no", 10);
          row.put("IsActiveEntity", false);
          row.put("category_ID", null);
          CdsReadEventContext ctx = readContext("test.OrderItems", List.of(row));
          when(db.run(any(CqnSelect.class)))
              .thenReturn(
                  ResultBuilder.selectedRows(
                          new ArrayList<>(
                              List.of(
                                  new HashMap<>(
                                      Map.of("order_ID", 1, "item_no", 1, "category_ID", 1)),
                                  new HashMap<>(
                                      Map.of("order_ID", 1, "item_no", 2, "category_ID", 2)))))
                      .result());
          cut.afterRead(ctx, dataList(row));
          assertThat(row).containsKey("SAP_Recommendations");
          Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
          assertThat((List<?>) recs.get("category_ID")).hasSize(1);
        });
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void rptStyleClient_filledColumns_areExcludedFromRecommendations() {
    runIn(
        () -> {
          Map<String, Object> row = new HashMap<>();
          row.put("ID", "a009c640-434a-4542-ac68-51b400c880ec");
          row.put("IsActiveEntity", false);
          row.put("genre_ID", 12);
          row.put("currency_code", null);
          CdsReadEventContext ctx = readContext("test.Books", List.of(row));
          when(db.run(any(CqnSelect.class)))
              .thenReturn(
                  twoContextRows(),
                  ResultBuilder.selectedRows(List.of()).result(),
                  ResultBuilder.selectedRows(List.of()).result());
          predictionClient = rptStyleClient();
          cut.afterRead(ctx, dataList(row));
          assertThat(row).containsKey("SAP_Recommendations");
          Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
          assertThat(recs).doesNotContainKey("genre_ID");
          assertThat((List<?>) recs.get("currency_code")).hasSize(1);
        });
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private CdsReadEventContext readContext(String entityName, List<Map<String, Object>> resultRows) {
    CdsReadEventContext ctx = CdsReadEventContext.create(entityName);
    CdsServiceUtils.getEventContextSPI(ctx).setService(runtimeService());
    Result result =
        ResultBuilder.selectedRows(resultRows)
            .rowType(runtime.getCdsModel().getEntity(entityName))
            .result();
    ctx.setResult(result);
    return ctx;
  }

  private static Service runtimeService() {
    return runtime.getServiceCatalog().getServices().findFirst().orElseThrow();
  }

  private void runIn(Runnable test) {
    runtime.requestContext().run((Consumer<RequestContext>) rc -> test.run());
  }

  private Map<String, Object> draftRow(String col, Object val) {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", "a009c640-434a-4542-ac68-51b400c880ec");
    row.put("IsActiveEntity", false);
    row.put(col, val);
    return row;
  }

  private static List<CdsData> dataList(Map<String, Object> row) {
    return List.of(CdsData.create(row));
  }

  private static List<CdsData> dataList(List<Map<String, Object>> rows) {
    return rows.stream().map(CdsData::create).toList();
  }

  private static Result twoContextRows() {
    return ResultBuilder.selectedRows(
            new ArrayList<>(
                List.of(
                    Map.of("ID", "x1", "genre_ID", 12, "currency_code", "USD"),
                    Map.of("ID", "x2", "genre_ID", 16, "currency_code", "GBP"))))
        .result();
  }

  private static RecommendationClient rptStyleClient() {
    Random random = new Random(42);
    return (rows, predictionColumns, indexColumn) -> {
      List<CdsData> predictions = new ArrayList<>();
      for (CdsData row : rows) {
        if (predictionColumns.stream().noneMatch(col -> "[PREDICT]".equals(row.get(col)))) {
          continue;
        }
        Map<String, Object> prediction = new HashMap<>();
        for (String col : predictionColumns) {
          List<Object> available =
              rows.stream()
                  .filter(r -> r.get(col) != null && !"[PREDICT]".equals(r.get(col)))
                  .map(r -> r.get(col))
                  .toList();
          Object val = available.isEmpty() ? null : available.get(random.nextInt(available.size()));
          prediction.put(col, List.of(Map.of("prediction", val)));
        }
        prediction.put(indexColumn, row.get(indexColumn));
        predictions.add(CdsData.create(prediction));
      }
      return predictions;
    };
  }

  private static RecommendationClient randomPickClient() {
    Random random = new Random(42);
    return (rows, predictionColumns, indexColumn) -> {
      List<CdsData> predictions = new ArrayList<>();
      for (CdsData row : rows) {
        Map<String, Object> prediction = new HashMap<>();
        boolean addPrediction = false;
        for (String col : predictionColumns) {
          if ("[PREDICT]".equals(row.get(col))) {
            addPrediction = true;
            List<Object> available =
                rows.stream()
                    .filter(r -> r.get(col) != null && !"[PREDICT]".equals(r.get(col)))
                    .map(r -> r.get(col))
                    .toList();
            Object val =
                available.isEmpty() ? null : available.get(random.nextInt(available.size()));
            prediction.put(col, List.of(Map.of("prediction", val)));
          }
        }
        if (addPrediction) {
          prediction.put(indexColumn, row.get(indexColumn));
          predictions.add(CdsData.create(prediction));
        }
      }
      return predictions;
    };
  }
}
