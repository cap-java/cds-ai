/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import com.sap.cds.CdsData;
import java.util.List;

public interface RecommendationClient {

  List<CdsData> predict(List<CdsData> rows, List<String> predictionColumns, String indexColumn);
}
