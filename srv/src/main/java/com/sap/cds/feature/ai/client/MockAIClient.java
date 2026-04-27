/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockAIClient implements AIClient {

  private static final Logger logger = LoggerFactory.getLogger(MockAIClient.class);

  @Override
  public List<Map<String, Object>> fetchPredictions(
      List<Map> rows, List<String> predictionColumns, String tenantId) {
    List<Map<String, Object>> predictions = new ArrayList<>();
    Random random = new Random();

    for (Map<String, Object> row : rows) {
      Map<String, Object> prediction = new HashMap<>();
      boolean addPrediction = false;
      for (String col : predictionColumns) {
        if ("[PREDICT]".equals(row.get(col))) {
          addPrediction = true;
          List<Object> availableValues =
              rows.stream()
                  .filter(r -> r.get(col) != null && !"[PREDICT]".equals(r.get(col)))
                  .map(r -> r.get(col))
                  .collect(java.util.stream.Collectors.toList());
          Object contextValue =
              availableValues.isEmpty()
                  ? null
                  : availableValues.get(
                      random.nextInt(
                          availableValues
                              .size())); // get a random value from the existing values for this
          // column
          Map<String, Object> predictionEntry = new HashMap<>();
          predictionEntry.put("prediction", contextValue);
          prediction.put(col, List.of(predictionEntry));
        }
      }
      if (addPrediction) {
        prediction.put("ID", row.get("ID"));
        predictions.add(prediction);
      }
    }
    logger.info("Generated mock predictions: " + predictions.toString());
    return predictions;
  }
}
