/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.ai.client;

import java.util.List;
import java.util.Map;

public interface AIClient {
  /**
   * Fetch predictions for the given rows.
   *
   * @param rows context rows + rows with [PREDICT] placeholders
   * @param predictionColumns fields to predict
   * @param tenantId the current tenant ID; null in single-tenant mode
   * @return predicted values per row
   */
  List<Map<String, Object>> fetchPredictions(
      List<Map> rows, List<String> predictionColumns, String tenantId);
}
