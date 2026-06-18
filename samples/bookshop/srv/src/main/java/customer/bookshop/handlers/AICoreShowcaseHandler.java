package customer.bookshop.handlers;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.recommendation.api.RptInferenceClient;
import com.sap.cds.feature.recommendation.api.RptModelSpec;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.cds.RemoteService;
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

  private RemoteService getAICoreService() {
    return runtime.getServiceCatalog().getService(RemoteService.class, AICore.SERVICE_NAME);
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
    RemoteService service = getAICoreService();
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    String rgId = rgCtx.getResult();
    context.put("result", rgId);
    context.setCompleted();
  }

  @On(event = "getMyResourceGroup")
  public void onGetMyResourceGroup(EventContext context) {
    RemoteService service = getAICoreService();
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    String rgId = rgCtx.getResult();
    context.put("result", rgId);
    context.setCompleted();
  }

  @On(event = "provisionRpt1")
  public void onProvisionRpt1(EventContext context) {
    String resourceGroupId = (String) context.get("resourceGroupId");
    RemoteService service = getAICoreService();
    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(resourceGroupId);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);
    String deploymentId = depCtx.getResult();
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

    List<CdsData> contextRows =
        List.of(
            CdsData.create(
                Map.of("ID", "ctx-1", "name", "Laptop", "price", "999.99", "category", "Electronics")),
            CdsData.create(
                Map.of("ID", "ctx-2", "name", "Mouse", "price", "29.99", "category", "Electronics")),
            CdsData.create(
                Map.of("ID", "ctx-3", "name", "Shirt", "price", "49.99", "category", "Clothing")),
            CdsData.create(
                Map.of("ID", "ctx-4", "name", "Novel", "price", "14.99", "category", "Books")),
            CdsData.create(
                Map.of(
                    "ID", "ctx-5", "name", "Blender", "price", "89.99", "category", "Appliances")));

    RemoteService service = getAICoreService();
    RptInferenceClient client = createRptClient(service, List.of("ID"));

    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> product : products) {
      CdsData predictionRow = CdsData.create(new HashMap<>(product));
      List<CdsData> predictions =
          client.predict(predictionRow, contextRows, List.of("category"));
      for (CdsData prediction : predictions) {
        String id = (String) prediction.get("ID");
        Object categoryObj = prediction.get("category");
        String category =
            categoryObj instanceof List<?> list && !list.isEmpty()
                ? extractPrediction(list)
                : String.valueOf(categoryObj);
        results.add(Map.of("ID", id, "category", category));
      }
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

  /** Helper to resolve a ready-to-use RptInferenceClient from the AI Core RemoteService. */
  private static RptInferenceClient createRptClient(
      RemoteService service, List<String> keyNames) {
    ResourceGroupContext rgCtx = ResourceGroupContext.create();
    service.emit(rgCtx);
    String rg = rgCtx.getResult();

    DeploymentIdContext depCtx = DeploymentIdContext.create();
    depCtx.setResourceGroupId(rg);
    depCtx.setSpec(RptModelSpec.rpt1());
    service.emit(depCtx);

    InferenceClientContext infCtx = InferenceClientContext.create();
    infCtx.setResourceGroupId(rg);
    infCtx.setDeploymentId(depCtx.getResult());
    service.emit(infCtx);

    return new RptInferenceClient(infCtx.getResult(), keyNames);
  }
}
