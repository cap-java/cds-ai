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
  //
  // @param predictionRow  the single entity row to predict values for; prediction columns contain
  //                       null for missing values that the model should fill
  // @param contextRows    historical rows from the same entity used as training context
  // @param predictionColumns  names of the columns the model should predict
  // @param keyNames       names of the entity's key columns, used to identify rows in the response
  List<CdsData> predict(
      CdsData predictionRow,
      List<CdsData> contextRows,
      List<String> predictionColumns,
      List<String> keyNames);
}
