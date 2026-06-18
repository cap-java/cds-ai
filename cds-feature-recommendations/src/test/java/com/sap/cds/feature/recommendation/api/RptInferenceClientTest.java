/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.CdsData;
import com.sap.cds.feature.recommendation.RptIndexColumns;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RptInferenceClientTest {

  @Test
  void resolveIndexColumn_singleStringKey_usesItDirectly() {
    CdsData row = CdsData.create(Map.of("isbn", "978-3-16"));
    assertThat(RptIndexColumns.resolveIndexColumn(List.of("isbn"), row)).isEqualTo("isbn");
  }

  @Test
  void resolveIndexColumn_singleUuidKey_usesItDirectly() {
    CdsData row = CdsData.create(Map.of("ID", "a009c640-434a-4542-ac68-51b400c880ec"));
    assertThat(RptIndexColumns.resolveIndexColumn(List.of("ID"), row)).isEqualTo("ID");
  }

  @Test
  void resolveIndexColumn_singleIntegerKey_usesSyntheticColumn() {
    CdsData row = CdsData.create(Map.of("order_ID", 42));
    assertThat(RptIndexColumns.resolveIndexColumn(List.of("order_ID"), row))
        .isEqualTo("SAP_RECOMMENDATIONS_ID");
  }

  @Test
  void resolveIndexColumn_compositeKey_usesSyntheticColumn() {
    CdsData row = CdsData.create(Map.of("order_ID", 1, "item_no", 10));
    assertThat(RptIndexColumns.resolveIndexColumn(List.of("order_ID", "item_no"), row))
        .isEqualTo("SAP_RECOMMENDATIONS_ID");
  }

  @Test
  void computeSyntheticKey_singleKey() {
    String key = RptInferenceClient.computeSyntheticKey(Map.of("ID", "abc"), List.of("ID"));
    assertThat(key).isEqualTo("ID" + '\0' + "abc");
  }

  @Test
  void computeSyntheticKey_compositeKey() {
    String key =
        RptInferenceClient.computeSyntheticKey(
            Map.of("order_ID", 1, "item_no", 10), List.of("order_ID", "item_no"));
    assertThat(key).isEqualTo("order_ID" + '\0' + "1" + '\0' + "item_no" + '\0' + "10");
  }

  @Test
  void computeSyntheticKey_noCollision_betweenDifferentCompositions() {
    // "1" + "0" must not produce the same key as "10" + ""
    String key1 =
        RptInferenceClient.computeSyntheticKey(
            Map.of("order_ID", "1", "item_no", "0"), List.of("order_ID", "item_no"));
    String key2 =
        RptInferenceClient.computeSyntheticKey(
            Map.of("order_ID", "10", "item_no", ""), List.of("order_ID", "item_no"));
    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void computeSyntheticKey_nullValue_doesNotCrash() {
    Map<String, Object> row = new java.util.HashMap<>();
    row.put("order_ID", 1);
    row.put("item_no", null);
    String key = RptInferenceClient.computeSyntheticKey(row, List.of("order_ID", "item_no"));
    assertThat(key).isEqualTo("order_ID" + '\0' + "1" + '\0' + "item_no" + '\0');
  }
}
