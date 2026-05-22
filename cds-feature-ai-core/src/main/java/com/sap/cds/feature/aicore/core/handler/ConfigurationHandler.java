/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.model.AiConfiguration;
import com.sap.ai.sdk.core.model.AiConfigurationBaseData;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.ai.sdk.core.model.AiParameterArgumentBinding;
import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICoreService.DEFAULT_NAME)
public class ConfigurationHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(ConfigurationHandler.class);

  private final ConfigurationApi configurationApi;

  public ConfigurationHandler(AICoreServiceImpl service) {
    super(service);
    this.configurationApi = service.getConfigurationApi();
  }

  @On(event = CqnService.EVENT_READ, entity = AICoreService.CONFIGURATIONS)
  public void onRead(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    AnalysisResult analysis = CqnAnalyzer.create(model).analyze(select);
    Map<String, Object> keys = analysis.targetKeys();
    Map<String, Object> values = analysis.targetValues();

    String resourceGroupId = resolveResourceGroup(merge(keys, values));
    logger.debug(
        "Reading configurations for resourceGroup={}, keys={}, values={}",
        resourceGroupId,
        keys,
        values);

    String id = (String) keys.get("id");
    if (id != null) {
      AiConfiguration config = configurationApi.get(resourceGroupId, id);
      context.setResult(List.of(toMap(config, resourceGroupId)));
    } else {
      String scenarioId = (String) values.get("scenarioId");
      AiConfigurationList result =
          configurationApi.query(resourceGroupId, scenarioId, null, null, null, null, null, null);
      List<Map<String, Object>> results =
          mapResources(result.getResources(), c -> toMap(c, resourceGroupId));
      logger.debug("ConfigurationApi.query returned {} resources", results.size());
      context.setResult(results);
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.CONFIGURATIONS)
  public void onCreate(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> entries = insert.entries();
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entry : entries) {
      String resourceGroupId = resolveResourceGroup(entry);
      String name = (String) entry.get("name");
      String executableId = (String) entry.get("executableId");
      String scenarioId = (String) entry.get("scenarioId");

      AiConfigurationBaseData request =
          AiConfigurationBaseData.create()
              .name(name)
              .executableId(executableId)
              .scenarioId(scenarioId);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> paramBindings =
          (List<Map<String, Object>>) entry.get("parameterBindings");
      if (paramBindings != null) {
        List<AiParameterArgumentBinding> sdkBindings =
            paramBindings.stream()
                .map(
                    p ->
                        AiParameterArgumentBinding.create()
                            .key((String) p.get("key"))
                            .value((String) p.get("value")))
                .toList();
        request.parameterBindings(sdkBindings);
      }

      var response = configurationApi.create(resourceGroupId, request);
      CdsData result = CdsData.create(entry);
      result.put("id", response.getId());
      results.add(result);
      logger.debug(
          "Created configuration {} in resource group {}", response.getId(), resourceGroupId);
    }
    context.setResult(results);
  }

  private CdsData toMap(AiConfiguration config, String resourceGroupId) {
    CdsData data = CdsData.create();
    data.put("id", config.getId());
    data.put("name", config.getName());
    data.put("executableId", config.getExecutableId());
    data.put("scenarioId", config.getScenarioId());
    data.put("createdAt", config.getCreatedAt());
    if (config.getParameterBindings() != null) {
      List<CdsData> bindings =
          config.getParameterBindings().stream()
              .map(
                  b -> {
                    CdsData bm = CdsData.create();
                    bm.put("key", b.getKey());
                    bm.put("value", b.getValue());
                    return bm;
                  })
              .toList();
      data.put("parameterBindings", bindings);
    }
    if (config.getInputArtifactBindings() != null) {
      List<CdsData> bindings =
          config.getInputArtifactBindings().stream()
              .map(
                  b -> {
                    CdsData bm = CdsData.create();
                    bm.put("key", b.getKey());
                    bm.put("artifactId", b.getArtifactId());
                    return bm;
                  })
              .toList();
      data.put("inputArtifactBindings", bindings);
    }
    data.putPath("resourceGroup.resourceGroupId", resourceGroupId);
    return data;
  }
}
