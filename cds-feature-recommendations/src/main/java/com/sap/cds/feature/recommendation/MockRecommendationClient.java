/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.CdsData;
import com.sap.cds.feature.recommendation.api.RecommendationClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Mock implementation used when no AI Core binding is present. For each prediction column that
// is null in the predict row, it picks a random non-null value from the same column across the
// context rows and returns it as the prediction. Columns already filled are left unchanged.
class MockRecommendationClient implements RecommendationClient {

  // We use random here so you can see a difference in the UI. The actual value returned here is not
  // relevant for tests.
  private final Random random = new Random();

  @Override
  public List<CdsData> predict(
      CdsData predictionRow,
      List<CdsData> contextRows,
      List<String> predictionColumns,
      List<String> keyNames) {
    String indexColumn = keyNames.size() == 1 ? keyNames.get(0) : "SAP_RECOMMENDATIONS_ID";
    Map<String, Object> prediction = new HashMap<>();
    for (String col : predictionColumns) {
      if (predictionRow.get(col) == null) {
        List<Object> availableValues =
            contextRows.stream().filter(r -> r.get(col) != null).map(r -> r.get(col)).toList();
        Object contextValue =
            availableValues.isEmpty()
                ? null
                : availableValues.get(random.nextInt(availableValues.size()));
        Map<String, Object> predictionEntry = new HashMap<>();
        // Replace the empty entry in col with a randomly picked value of entries in the
        // contextRows.
        predictionEntry.put("prediction", contextValue);
        prediction.put(col, List.of(predictionEntry));
      }
    }
    if (!keyNames.isEmpty()) {
      prediction.put(indexColumn, predictionRow.get(keyNames.get(0)));
    }
    return List.of(CdsData.create(prediction));
  }
}
