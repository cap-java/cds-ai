/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.cds.CdsData;
import java.util.List;

public class RptIndexColumns {

  // RPT-1 requires a single string index column to identify rows in the request/response.
  // When the entity has a composite or non-string key, a synthetic string column is used instead.
  public static final String SYNTHETIC_INDEX_COLUMN = "SAP_RECOMMENDATIONS_ID";

  // Returns the column name to use as the RPT-1 index column. Uses the single key directly if
  // it holds a String value; falls back to the synthetic column for composite or non-string keys.
  public static String resolveIndexColumn(List<String> keyNames, CdsData sampleRow) {
    if (keyNames.size() == 1 && sampleRow.get(keyNames.get(0)) instanceof String) {
      return keyNames.get(0);
    }
    return SYNTHETIC_INDEX_COLUMN;
  }

  private RptIndexColumns() {}
}
