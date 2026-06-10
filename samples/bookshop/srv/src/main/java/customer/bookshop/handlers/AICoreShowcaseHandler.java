package customer.bookshop.handlers;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import com.sap.cds.feature.recommendation.api.RptInferenceClient;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.runtime.CdsRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ServiceName("AICoreShowcaseService")
public class AICoreShowcaseHandler implements EventHandler {

  @Autowired private CdsRuntime runtime;

  private AICoreService getAICoreService() {
    return runtime.getServiceCatalog().getService(AICoreService.class, AICoreService.DEFAULT_NAME);
  }

  // This handler is NOT required - the plugin automatically delegates reads on projections
  // of AICore entities. It is kept here only to demonstrate how to query the AICore service
  // programmatically, e.g. for custom filtering or post-processing.
  @On(event = CqnService.EVENT_READ, entity = "AICoreShowcaseService.Configurations")
  public void onReadConfigurations(CdsReadEventContext context) {
    context.setResult(getAICoreService().run(Select.from("AICore.configurations")));
  }

  @On(event = "setupTenantResources")
  public void onSetupTenantResources(EventContext context) {
    String rgId = getAICoreService().resourceGroup();
    context.put("result", rgId);
    context.setCompleted();
  }

  @On(event = "getMyResourceGroup")
  public void onGetMyResourceGroup(EventContext context) {
    String rgId = getAICoreService().resourceGroup();
    context.put("result", rgId);
    context.setCompleted();
  }

  @On(event = "provisionRpt1")
  public void onProvisionRpt1(EventContext context) {
    String resourceGroupId = (String) context.get("resourceGroupId");
    String deploymentId = getAICoreService().deploymentId(resourceGroupId, RptModelSpec.rpt1());
    context.put("result", deploymentId);
    context.setCompleted();
  }

  @On(event = "stopDeployment")
  public void onStopDeployment(EventContext context) {
    String deploymentId = (String) context.get("deploymentId");
    String resourceGroupId = (String) context.get("resourceGroupId");

    getAICoreService()
        .run(
            Update.entity("AICore.deployments")
                .where(d -> d.get("id").eq(deploymentId))
                .data(
                    Map.of(
                        "targetStatus",
                        "STOPPED",
                        "resourceGroup_resourceGroupId",
                        resourceGroupId)));
    context.setCompleted();
  }

  @On(event = "createConfiguration")
  public void onCreateConfiguration(EventContext context) {
    String name = (String) context.get("name");
    String scenarioId = (String) context.get("scenarioId");
    String executableId = (String) context.get("executableId");
    String resourceGroupId = (String) context.get("resourceGroupId");

    Result result =
        getAICoreService()
            .run(
                Insert.into("AICore.configurations")
                    .entry(
                        Map.of(
                            "name", name,
                            "scenarioId", scenarioId,
                            "executableId", executableId,
                            "resourceGroup_resourceGroupId", resourceGroupId,
                            "parameterBindings",
                                List.of(
                                    Map.of("key", "modelName", "value", "sap-rpt-1-small"),
                                    Map.of("key", "modelVersion", "value", "latest")))));

    String configId = (String) result.single().get("id");
    context.put("result", configId);
    context.setCompleted();
  }

  @SuppressWarnings("unchecked")
  @On(event = "predictCategory")
  public void onPredictCategory(EventContext context) {
    List<Map<String, Object>> products = (List<Map<String, Object>>) context.get("products");

    List<CdsData> rows = new ArrayList<>();
    rows.add(
        CdsData.create(
            Map.of("ID", "ctx-1", "name", "Laptop", "price", "999.99", "category", "Electronics")));
    rows.add(
        CdsData.create(
            Map.of("ID", "ctx-2", "name", "Mouse", "price", "29.99", "category", "Electronics")));
    rows.add(
        CdsData.create(
            Map.of("ID", "ctx-3", "name", "Shirt", "price", "49.99", "category", "Clothing")));
    rows.add(
        CdsData.create(
            Map.of("ID", "ctx-4", "name", "Novel", "price", "14.99", "category", "Books")));
    rows.add(
        CdsData.create(
            Map.of("ID", "ctx-5", "name", "Blender", "price", "89.99", "category", "Appliances")));

    for (Map<String, Object> product : products) {
      Map<String, Object> row = new HashMap<>(product);
      row.put("category", "[PREDICT]");
      rows.add(CdsData.create(row));
    }

    AICoreService service = getAICoreService();
    String rg = service.resourceGroup();
    String deploymentId = service.deploymentId(rg, RptModelSpec.rpt1());
    RptInferenceClient client =
        new RptInferenceClient(
            service.inferenceClient(rg, deploymentId),
            ((AbstractAICoreService) service).getRetry());
    List<CdsData> predictions = client.predict(rows, List.of("category"), "ID");

    List<Map<String, Object>> results = new ArrayList<>();
    for (CdsData prediction : predictions) {
      String id = (String) prediction.get("ID");
      Object categoryObj = prediction.get("category");
      String category =
          categoryObj instanceof List<?> list && !list.isEmpty()
              ? extractPrediction(list)
              : String.valueOf(categoryObj);
      results.add(Map.of("ID", id, "category", category));
    }

    context.put("result", results);
    context.setCompleted();
  }

  private String extractPrediction(List<?> predictionList) {
    if (predictionList.get(0) instanceof Map<?, ?> map) {
      Object prediction = map.get("prediction");
      return prediction != null ? prediction.toString() : "";
    }
    return predictionList.get(0).toString();
  }
}
