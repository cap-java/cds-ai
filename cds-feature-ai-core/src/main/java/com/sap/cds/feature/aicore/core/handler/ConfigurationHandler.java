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
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ArtifactArgumentBinding;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Configurations;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ParameterArgumentBinding;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
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

    String id = (String) keys.get(Configurations.ID);
    if (id != null) {
      AiConfiguration config = configurationApi.get(resourceGroupId, id);
      context.setResult(List.of(toConfigurations(config, resourceGroupId)));
    } else {
      String scenarioId = (String) values.get(Configurations.SCENARIO_ID);
      AiConfigurationList result =
          configurationApi.query(resourceGroupId, scenarioId, null, null, null, null, null, null);
      List<Map<String, Object>> results =
          mapResources(result.getResources(), c -> toConfigurations(c, resourceGroupId));
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
      String name = (String) entry.get(Configurations.NAME);
      String executableId = (String) entry.get(Configurations.EXECUTABLE_ID);
      String scenarioId = (String) entry.get(Configurations.SCENARIO_ID);

      AiConfigurationBaseData request =
          AiConfigurationBaseData.create()
              .name(name)
              .executableId(executableId)
              .scenarioId(scenarioId);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> paramBindings =
          (List<Map<String, Object>>) entry.get(Configurations.PARAMETER_BINDINGS);
      if (paramBindings != null) {
        List<AiParameterArgumentBinding> sdkBindings =
            paramBindings.stream()
                .map(
                    p ->
                        AiParameterArgumentBinding.create()
                            .key((String) p.get(ParameterArgumentBinding.KEY))
                            .value((String) p.get(ParameterArgumentBinding.VALUE)))
                .toList();
        request.parameterBindings(sdkBindings);
      }

      var response = configurationApi.create(resourceGroupId, request);
      CdsData result = CdsData.create(entry);
      result.put(Configurations.ID, response.getId());
      results.add(result);
      logger.debug(
          "Created configuration {} in resource group {}", response.getId(), resourceGroupId);
    }
    context.setResult(results);
  }

  private Configurations toConfigurations(AiConfiguration config, String resourceGroupId) {
    Configurations data = Configurations.create();
    data.setId(config.getId());
    data.setName(config.getName());
    data.setExecutableId(config.getExecutableId());
    data.setScenarioId(config.getScenarioId());
    data.put(Configurations.CREATED_AT, config.getCreatedAt());
    if (config.getParameterBindings() != null) {
      List<ParameterArgumentBinding> bindings =
          config.getParameterBindings().stream()
              .map(
                  b -> {
                    ParameterArgumentBinding bm = ParameterArgumentBinding.create();
                    bm.setKey(b.getKey());
                    bm.setValue(b.getValue());
                    return bm;
                  })
              .toList();
      data.put(Configurations.PARAMETER_BINDINGS, bindings);
    }
    if (config.getInputArtifactBindings() != null) {
      List<ArtifactArgumentBinding> bindings =
          config.getInputArtifactBindings().stream()
              .map(
                  b -> {
                    ArtifactArgumentBinding bm = ArtifactArgumentBinding.create();
                    bm.setKey(b.getKey());
                    bm.setArtifactId(b.getArtifactId());
                    return bm;
                  })
              .toList();
      data.put(Configurations.INPUT_ARTIFACT_BINDINGS, bindings);
    }
    data.setResourceGroup(ResourceGroups.create(resourceGroupId));
    return data;
  }
}
