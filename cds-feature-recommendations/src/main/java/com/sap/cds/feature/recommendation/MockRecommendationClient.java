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

class MockRecommendationClient implements RecommendationClient {

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
        predictionEntry.put("prediction", contextValue);
        prediction.put(col, List.of(predictionEntry));
      }
    }
    prediction.put(indexColumn, predictionRow.get(keyNames.get(0)));
    return List.of(CdsData.create(prediction));
  }
}
