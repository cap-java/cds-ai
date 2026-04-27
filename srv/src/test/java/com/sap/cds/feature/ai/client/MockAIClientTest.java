/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockAIClientTest {

  private MockAIClient cut;

  @BeforeEach
  void setup() {
    cut = new MockAIClient();
  }

  @Test
  void noRowsReturnsEmpty() {
    List<Map<String, Object>> result =
        cut.fetchPredictions(new ArrayList<>(), List.of("genre_ID"), null);
    assertThat(result).isEmpty();
  }

  @Test
  void rowWithoutPredictPlaceholderIsSkipped() {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", "id-1");
    row.put("genre_ID", 10);

    List<Map<String, Object>> result =
        cut.fetchPredictions(List.of(row), List.of("genre_ID"), null);

    assertThat(result).isEmpty();
  }

  @Test
  void rowWithPredictPlaceholderReturnsPrediction() {
    Map<String, Object> contextRow = new HashMap<>();
    contextRow.put("ID", "id-1");
    contextRow.put("genre_ID", 10);

    Map<String, Object> predictRow = new HashMap<>();
    predictRow.put("ID", "id-2");
    predictRow.put("genre_ID", "[PREDICT]");

    List<Map<String, Object>> result =
        cut.fetchPredictions(List.of(contextRow, predictRow), List.of("genre_ID"), null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).get("ID")).isEqualTo("id-2");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> preds = (List<Map<String, Object>>) result.get(0).get("genre_ID");
    assertThat(preds).hasSize(1);
    assertThat(preds.get(0).get("prediction")).isEqualTo(10);
  }

  @Test
  void predictionPicksValueFromContextRows() {
    Map<String, Object> c1 = new HashMap<>();
    c1.put("ID", "id-1");
    c1.put("genre_ID", 10);

    Map<String, Object> c2 = new HashMap<>();
    c2.put("ID", "id-2");
    c2.put("genre_ID", 20);

    Map<String, Object> predictRow = new HashMap<>();
    predictRow.put("ID", "id-3");
    predictRow.put("genre_ID", "[PREDICT]");

    List<Map<String, Object>> result =
        cut.fetchPredictions(List.of(c1, c2, predictRow), List.of("genre_ID"), null);

    assertThat(result).hasSize(1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> preds = (List<Map<String, Object>>) result.get(0).get("genre_ID");
    assertThat(preds.get(0).get("prediction")).isIn(10, 20);
  }

  @Test
  void multipleColumnsEachGetPrediction() {
    Map<String, Object> contextRow = new HashMap<>();
    contextRow.put("ID", "id-1");
    contextRow.put("genre_ID", 10);
    contextRow.put("currency_code", "EUR");

    Map<String, Object> predictRow = new HashMap<>();
    predictRow.put("ID", "id-2");
    predictRow.put("genre_ID", "[PREDICT]");
    predictRow.put("currency_code", "[PREDICT]");

    List<Map<String, Object>> result =
        cut.fetchPredictions(
            List.of(contextRow, predictRow), List.of("genre_ID", "currency_code"), null);

    assertThat(result).hasSize(1);
    Map<String, Object> prediction = result.get(0);
    assertThat(prediction).containsKey("genre_ID");
    assertThat(prediction).containsKey("currency_code");
  }

  @Test
  void noContextValuesResultsInNullPrediction() {
    // Only the predict row, no context rows with real values
    Map<String, Object> predictRow = new HashMap<>();
    predictRow.put("ID", "id-1");
    predictRow.put("genre_ID", "[PREDICT]");

    List<Map<String, Object>> result =
        cut.fetchPredictions(List.of(predictRow), List.of("genre_ID"), null);

    assertThat(result).hasSize(1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> preds = (List<Map<String, Object>>) result.get(0).get("genre_ID");
    assertThat(preds.get(0).get("prediction")).isNull();
  }

  @Test
  void multipleRowsWithPredictEachGetPredicted() {
    Map<String, Object> contextRow = new HashMap<>();
    contextRow.put("ID", "id-1");
    contextRow.put("genre_ID", 10);

    Map<String, Object> predictRow1 = new HashMap<>();
    predictRow1.put("ID", "id-2");
    predictRow1.put("genre_ID", "[PREDICT]");

    Map<String, Object> predictRow2 = new HashMap<>();
    predictRow2.put("ID", "id-3");
    predictRow2.put("genre_ID", "[PREDICT]");

    List<Map<String, Object>> result =
        cut.fetchPredictions(
            List.of(contextRow, predictRow1, predictRow2), List.of("genre_ID"), null);

    assertThat(result).hasSize(2);
  }
}
