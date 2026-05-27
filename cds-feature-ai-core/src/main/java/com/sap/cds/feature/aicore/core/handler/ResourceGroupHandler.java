/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static com.sap.cds.feature.aicore.core.AICoreElements.Label;
import static com.sap.cds.feature.aicore.core.AICoreElements.ResourceGroup;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupPatchRequest;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(AICoreService.DEFAULT_NAME)
public class ResourceGroupHandler extends AbstractCrudHandler {

  private static final Logger logger = LoggerFactory.getLogger(ResourceGroupHandler.class);

  private final ResourceGroupApi resourceGroupApi;

  public ResourceGroupHandler(AICoreServiceImpl service) {
    super(service);
    this.resourceGroupApi = service.getResourceGroupApi();
  }

  @On(event = CqnService.EVENT_READ, entity = AICoreService.RESOURCE_GROUPS)
  public void onRead(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    AnalysisResult analysis = CqnAnalyzer.create(model).analyze(select);

    Map<String, Object> keys = analysis.targetKeys();
    Map<String, Object> values = analysis.targetValues();

    String resourceGroupId = (String) keys.get(ResourceGroup.RESOURCE_GROUP_ID);
    if (resourceGroupId == null) {
      resourceGroupId = (String) values.get(ResourceGroup.RESOURCE_GROUP_ID);
    }

    if (resourceGroupId != null) {
      BckndResourceGroup rg = resourceGroupApi.get(resourceGroupId);
      context.setResult(List.of(toMap(rg)));
    } else {
      List<String> labelSelector = null;
      if (values.containsKey(ResourceGroup.TENANT_ID)) {
        String tenantId = (String) values.get(ResourceGroup.TENANT_ID);
        labelSelector = List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + tenantId);
      }
      BckndResourceGroupList result =
          resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
      context.setResult(mapResources(result.getResources(), this::toMap));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.RESOURCE_GROUPS)
  public void onCreate(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> entries = insert.entries();
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> entry : entries) {
      String resourceGroupId = (String) entry.get(ResourceGroup.RESOURCE_GROUP_ID);
      BckndResourceGroupsPostRequest request =
          BckndResourceGroupsPostRequest.create().resourceGroupId(resourceGroupId);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> labels =
          (List<Map<String, Object>>) entry.get(ResourceGroup.LABELS);
      List<BckndResourceGroupLabel> mergedLabels = new ArrayList<>();

      // User-supplied labels take precedence: if they include the tenant label key, we skip
      // the auto-generated one based on the tenantId field.
      boolean userSuppliedTenantLabel =
          labels != null
              && labels.stream()
                  .anyMatch(l -> AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.get(Label.KEY)));

      if (entry.containsKey(ResourceGroup.TENANT_ID) && !userSuppliedTenantLabel) {
        String tenantId = (String) entry.get(ResourceGroup.TENANT_ID);
        mergedLabels.add(
            BckndResourceGroupLabel.create()
                .key(AICoreServiceImpl.TENANT_LABEL_KEY)
                .value(tenantId));
      }

      if (labels != null) {
        mergedLabels.addAll(toSdkLabels(labels));
      }

      if (!mergedLabels.isEmpty()) {
        request.labels(mergedLabels);
      }

      resourceGroupApi.create(request);
      logger.debug("Created resource group {}", resourceGroupId);
      results.add(entry);
    }
    context.setResult(results);
  }

  @On(event = CqnService.EVENT_UPDATE, entity = AICoreService.RESOURCE_GROUPS)
  public void onUpdate(CdsUpdateEventContext context) {
    CqnUpdate update = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();

    String resourceGroupId = resolveResourceGroupId(keys);

    Map<String, Object> data = update.entries().get(0);
    BckndResourceGroupPatchRequest patchRequest = BckndResourceGroupPatchRequest.create();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> labels = (List<Map<String, Object>>) data.get(ResourceGroup.LABELS);
    if (labels != null) {
      patchRequest.labels(toSdkLabels(labels));
    }

    resourceGroupApi.patch(resourceGroupId, patchRequest);
    logger.debug("Updated resource group {}", resourceGroupId);
    context.setResult(List.of(CdsData.create(data)));
  }

  @On(event = CqnService.EVENT_DELETE, entity = AICoreService.RESOURCE_GROUPS)
  public void onDelete(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();

    String resourceGroupId = resolveResourceGroupId(keys);
    resourceGroupApi.delete(resourceGroupId);
    logger.debug("Deleted resource group {}", resourceGroupId);
    context.setResult(List.of());
  }

  private String resolveResourceGroupId(Map<String, Object> keys) {
    if (keys.containsKey(ResourceGroup.RESOURCE_GROUP_ID)) {
      return (String) keys.get(ResourceGroup.RESOURCE_GROUP_ID);
    }
    if (keys.containsKey(ResourceGroup.TENANT_ID)) {
      return service.resourceGroupForTenant((String) keys.get(ResourceGroup.TENANT_ID));
    }
    return service.getDefaultResourceGroup();
  }

  private static List<BckndResourceGroupLabel> toSdkLabels(List<Map<String, Object>> labels) {
    return labels.stream()
        .map(
            l ->
                BckndResourceGroupLabel.create()
                    .key((String) l.get(Label.KEY))
                    .value((String) l.get(Label.VALUE)))
        .toList();
  }

  private CdsData toMap(BckndResourceGroup rg) {
    CdsData data = CdsData.create();
    data.put(ResourceGroup.RESOURCE_GROUP_ID, rg.getResourceGroupId());
    data.put(ResourceGroup.STATUS, rg.getStatus().getValue());
    data.put(ResourceGroup.STATUS_MESSAGE, rg.getStatusMessage());
    data.put(ResourceGroup.CREATED_AT, rg.getCreatedAt());
    if (rg.getLabels() != null) {
      List<CdsData> labels = new ArrayList<>(rg.getLabels().size());
      for (BckndResourceGroupLabel l : rg.getLabels()) {
        CdsData lm = CdsData.create();
        lm.put(Label.KEY, l.getKey());
        lm.put(Label.VALUE, l.getValue());
        labels.add(lm);
        if (AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.getKey())) {
          data.put(ResourceGroup.TENANT_ID, l.getValue());
        }
      }
      data.put(ResourceGroup.LABELS, labels);
    }
    return data;
  }
}
