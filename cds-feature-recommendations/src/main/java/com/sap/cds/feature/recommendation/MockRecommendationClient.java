/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.CdsData;
import com.sap.cds.feature.recommendation.api.RecommendationClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

class MockRecommendationClient implements RecommendationClient {

  private final Random random = new Random();

  @Override
  public List<CdsData> predict(
      List<CdsData> rows, List<String> predictionColumns, String indexColumn) {
    List<CdsData> predictions = new ArrayList<>();
    for (CdsData row : rows) {
      Map<String, Object> prediction = new HashMap<>();
      boolean addPrediction = false;
      for (String col : predictionColumns) {
        if ("[PREDICT]".equals(row.get(col))) {
          addPrediction = true;
          List<Object> availableValues =
              rows.stream()
                  .filter(r -> r.get(col) != null && !"[PREDICT]".equals(r.get(col)))
                  .map(r -> r.get(col))
                  .toList();
          Object contextValue =
              availableValues.isEmpty()
                  ? null
                  : availableValues.get(random.nextInt(availableValues.size()));
          Map<String, Object> predictionEntry = new HashMap<>();
          predictionEntry.put("prediction", contextValue);
          prediction.put(col, List.of(predictionEntry));
        }
      }
      if (addPrediction) {
        prediction.put(indexColumn, row.get(indexColumn));
        predictions.add(CdsData.create(prediction));
      }
    }
    return predictions;
  }
}
