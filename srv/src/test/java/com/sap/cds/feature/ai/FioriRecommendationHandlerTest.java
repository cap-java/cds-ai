/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.ResultBuilder;
import com.sap.cds.feature.ai.client.MockAIClient;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsType;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FioriRecommendationHandlerTest {

  @Mock private PersistenceService db;
  @Mock private CdsReadEventContext context;
  @Mock private CdsEntity entity;
  @Mock private UserInfo userInfo;

  private FioriRecommendationHandler cut;

  @BeforeEach
  void setup() {
    cut = new FioriRecommendationHandler(Optional.empty(), db);
  }

  @Test
  void emptyRows_returnsEarlyWithoutPredictions() {
    when(context.getResult()).thenReturn(result());
    cut.afterRead(context);
    verifyNoInteractions(db);
  }

  @Test
  void multipleRows_returnsEarlyWithoutPredictions() {
    when(context.getResult())
        .thenReturn(
            result(
                Map.of("ID", "1", "IsActiveEntity", false),
                Map.of("ID", "2", "IsActiveEntity", false)));
    cut.afterRead(context);
    verifyNoInteractions(db);
  }

  @Test
  void activeEntity_returnsEarlyWithoutPredictions() {
    when(context.getResult()).thenReturn(result(Map.of("ID", "1", "IsActiveEntity", true)));
    cut.afterRead(context);
    verifyNoInteractions(db);
  }

  @Test
  void noPredictionColumns_returnsEarlyWithoutPredictions() {
    // association-typed element: passes ValueList filter but is excluded by the Association filter
    CdsElement assocEl = mock(CdsElement.class);
    when(assocEl.findAnnotation("@Common.ValueList"))
        .thenReturn(Optional.of(mock(CdsAnnotation.class)));
    CdsType type = mock(CdsType.class);
    when(type.getQualifiedName()).thenReturn("cds.Association");
    when(assocEl.getType()).thenReturn(type);
    when(context.getTarget()).thenReturn(entity);
    when(entity.elements()).thenAnswer(inv -> java.util.stream.Stream.of(assocEl));
    when(context.getResult()).thenReturn(result(draftRow("title", "foo")));
    cut.afterRead(context);
    verifyNoInteractions(db);
  }

  @Test
  void notEnoughContextRows_returnsEarlyWithoutPredictions() {
    setupEntity(genreIdEl());
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class)))
        .thenReturn(
            ResultBuilder.selectedRows(List.of(Map.of("ID", "x1", "genre_ID", 12))).result());
    cut.afterRead(context);
    assertThat(row).doesNotContainKey("SAP_Recommendations");
  }

  @Test
  void allColumnsAlreadyFilled_returnsEarlyWithoutPredictions() {
    setupEntity(genreIdEl());
    Map<String, Object> row = draftRow("genre_ID", 16);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
    cut.afterRead(context);
    assertThat(row).doesNotContainKey("SAP_Recommendations");
  }

  @Test
  void emptyPredictions_returnsEarlyWithoutRecommendations() {
    setupEntityWithUserInfo(genreIdEl());
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
    try (MockedConstruction<MockAIClient> ignored =
        mockConstruction(
            MockAIClient.class,
            (mock, ctx) ->
                when(mock.fetchPredictions(any(), any(), any())).thenReturn(List.of()))) {
      cut = new FioriRecommendationHandler(Optional.empty(), db);
      cut.afterRead(context);
    }
    assertThat(row).doesNotContainKey("SAP_Recommendations");
  }

  @Test
  void multiplePredictions_returnsEarlyWithoutRecommendations() {
    setupEntityWithUserInfo(genreIdEl());
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
    try (MockedConstruction<MockAIClient> ignored =
        mockConstruction(
            MockAIClient.class,
            (mock, ctx) ->
                when(mock.fetchPredictions(any(), any(), any()))
                    .thenReturn(List.of(Map.of("ID", "id-1"), Map.of("ID", "id-2"))))) {
      cut = new FioriRecommendationHandler(Optional.empty(), db);
      cut.afterRead(context);
    }
    assertThat(row).doesNotContainKey("SAP_Recommendations");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void draftRow_withGenreAndCurrency_addsSapRecommendations() {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", "a009c640-434a-4542-ac68-51b400c880ec");
    row.put("IsActiveEntity", false);
    row.put("genre_ID", null);
    row.put("currency_code", null);
    CdsAnnotation textAnn = mock(CdsAnnotation.class);
    when(textAnn.getValue()).thenReturn("genre.name");
    setupEntityWithUserInfo(
        element("genre_ID", "cds.Integer", true, Optional.empty()),
        element("currency_code", "cds.String", true, Optional.empty()),
        element("genre", "cds.Association", false, Optional.of(textAnn)));
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class)))
        .thenReturn(
            ResultBuilder.selectedRows(
                    new ArrayList<>(
                        List.of(
                            Map.of("ID", "id-1", "genre_ID", 12, "currency_code", "USD"),
                            Map.of("ID", "id-2", "genre_ID", 16, "currency_code", "GBP"))))
                .result(),
            ResultBuilder.selectedRows(List.of()).result());
    cut.afterRead(context);
    assertThat(row).containsKey("SAP_Recommendations");
    Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
    assertThat((List<?>) recs.get("genre_ID")).hasSize(1);
    assertThat((List<?>) recs.get("currency_code")).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void textAnnotation_asMap_withEqKey_resolvesTextPath() {
    CdsAnnotation<Object> textAnn = mock(CdsAnnotation.class);
    when(textAnn.getValue()).thenReturn(Map.of("=", "genre.name"));
    setupEntityWithUserInfo(
        genreIdEl(), element("genre", "cds.Association", false, Optional.of(textAnn)));
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class)))
        .thenReturn(twoContextRows(), ResultBuilder.selectedRows(List.of()).result());
    cut.afterRead(context);
    assertThat((Map<String, ?>) row.get("SAP_Recommendations")).containsKey("genre_ID");
  }

  @Test
  @SuppressWarnings("unchecked")
  void textAnnotation_asMap_withoutEqKey_noDescription() {
    CdsAnnotation<Object> textAnn = mock(CdsAnnotation.class);
    when(textAnn.getValue()).thenReturn(Map.of("other", "value"));
    setupEntityWithUserInfo(
        genreIdEl(), element("genre", "cds.Association", false, Optional.of(textAnn)));
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
    cut.afterRead(context);
    Map<String, Object> recs = (Map<String, Object>) row.get("SAP_Recommendations");
    assertThat(((List<Map<String, Object>>) recs.get("genre_ID")).get(0))
        .containsEntry("RecommendedFieldDescription", "");
  }

  @Test
  @SuppressWarnings("unchecked")
  void textPath_invalidFormat_skipsColumn() {
    CdsAnnotation<Object> textAnn = mock(CdsAnnotation.class);
    when(textAnn.getValue()).thenReturn("genre.parent.name");
    setupEntityWithUserInfo(
        genreIdEl(), element("genre", "cds.Association", false, Optional.of(textAnn)));
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));
    when(db.run(any(CqnSelect.class))).thenReturn(twoContextRows());
    cut.afterRead(context);
    assertThat((Map<String, ?>) row.get("SAP_Recommendations")).doesNotContainKey("genre_ID");
  }

  @Test
  void blobAndVectorFields_areExcludedFromContextSelect() {
    // Arrange: entity with a predictable genre_ID, a LargeBinary (image), and a Vector field
    setupEntityWithUserInfo(
        genreIdEl(),
        element("image", "cds.LargeBinary", false, Optional.empty()),
        element("embedding", "cds.Vector", false, Optional.empty()));
    Map<String, Object> row = draftRow("genre_ID", null);
    when(context.getResult()).thenReturn(result(row));

    ArgumentCaptor<CqnSelect> selectCaptor = ArgumentCaptor.forClass(CqnSelect.class);
    when(db.run(selectCaptor.capture())).thenReturn(twoContextRows());

    cut.afterRead(context);

    // The SELECT sent to db.run() must not contain the BLOB or Vector column names
    String selectSql = selectCaptor.getValue().toString();
    assertThat(selectSql).doesNotContain("image");
    assertThat(selectSql).doesNotContain("embedding");
    assertThat(selectSql).contains("genre_ID");
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  @SafeVarargs
  private static Result result(Map<String, Object>... rows) {
    return ResultBuilder.selectedRows(List.of(rows)).result();
  }

  private Map<String, Object> draftRow(String col, Object val) {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", "id-1");
    row.put("IsActiveEntity", false);
    row.put(col, val);
    return row;
  }

  private Result twoContextRows() {
    return ResultBuilder.selectedRows(
            new ArrayList<>(
                List.of(Map.of("ID", "x1", "genre_ID", 12), Map.of("ID", "x2", "genre_ID", 16))))
        .result();
  }

  private CdsElement genreIdEl() {
    return element("genre_ID", "cds.Integer", true, Optional.empty());
  }

  private void setupEntity(CdsElement... elements) {
    when(context.getTarget()).thenReturn(entity);
    lenient().when(entity.getName()).thenReturn("Books");
    lenient().when(entity.getQualifiedName()).thenReturn("bookshop.Books");
    when(entity.elements()).thenAnswer(inv -> java.util.stream.Stream.of(elements));
  }

  private void setupEntityWithUserInfo(CdsElement... elements) {
    setupEntity(elements);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn(null); // can be null, but must be callabe
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static CdsElement element(
      String name, String cdsType, boolean hasValueList, Optional<CdsAnnotation> textAnnotation) {
    CdsElement el = mock(CdsElement.class);
    when(el.getName()).thenReturn(name);
    CdsType type = mock(CdsType.class);
    lenient().when(type.getQualifiedName()).thenReturn(cdsType);
    lenient().when(el.getType()).thenReturn(type);
    when(el.findAnnotation("@Common.ValueList")).thenReturn(Optional.empty());
    Optional valueListAnn =
        hasValueList ? Optional.of(mock(CdsAnnotation.class)) : Optional.empty();
    when(el.findAnnotation("@Common.ValueListWithFixedValues")).thenReturn(valueListAnn);
    lenient().when(el.findAnnotation("@Common.Text")).thenReturn((Optional) textAnnotation);
    return el;
  }
}
