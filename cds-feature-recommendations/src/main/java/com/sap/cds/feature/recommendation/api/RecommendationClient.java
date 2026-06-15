/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import com.sap.cds.CdsData;
import java.util.List;

public interface RecommendationClient {

  // Currently limited to a single prediction row. Multiple prediction rows may be supported in the
  // future via a separate overload, but are ruled out at two points for now:
  // (1) FioriRecommendationHandler bails out when the read returns more than one entity,
  //     so predictions only fire on single-entity reads.
  // (2) FioriRecommendationHandler also rejects responses with more than one prediction back from
  //     the model, treating it as an unexpected state.
  List<CdsData> predict(
      CdsData predictionRow,
      List<CdsData> contextRows,
      List<String> predictionColumns,
      String indexColumn);
}
