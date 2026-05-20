/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.sap.ai.sdk.core.model.AiParameterArgumentBinding;
import com.sap.ai.sdk.foundationmodels.rpt.RptModel;
import com.sap.cds.feature.aicore.core.ModelDeploymentSpec;
import java.util.List;
import java.util.Map;

public final class RptModelSpec {

  public static final String SCENARIO_ID = "foundation-models";
  public static final String EXECUTABLE_ID = "aicore-sap";
  public static final String CONFIG_NAME = "sap-rpt-1-small";
  public static final String MODEL_NAME = "sap-rpt-1-small";
  public static final String MODEL_VERSION = "latest";

  private RptModelSpec() {}

  public static ModelDeploymentSpec rpt1() {
    return new ModelDeploymentSpec(
        SCENARIO_ID,
        EXECUTABLE_ID,
        CONFIG_NAME,
        List.of(
            AiParameterArgumentBinding.create().key("modelName").value(MODEL_NAME),
            AiParameterArgumentBinding.create().key("modelVersion").value(MODEL_VERSION)),
        deployment -> {
          var details = deployment.getDetails();
          if (details == null || details.getResources() == null) {
            return false;
          }
          if (details.getResources().getBackendDetails() instanceof Map<?, ?> map
              && map.get("model") instanceof Map<?, ?> model
              && model.get("name") instanceof String name) {
            return RptModel.SAP_RPT_1_SMALL.name().equals(name);
          }
          return false;
        });
  }
}
