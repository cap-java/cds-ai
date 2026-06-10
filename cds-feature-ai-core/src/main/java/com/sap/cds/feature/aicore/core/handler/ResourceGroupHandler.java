/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupPatchRequest;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
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

    String resourceGroupId = (String) keys.get(ResourceGroups.RESOURCE_GROUP_ID);
    if (resourceGroupId == null) {
      resourceGroupId = (String) values.get(ResourceGroups.RESOURCE_GROUP_ID);
    }

    if (resourceGroupId != null) {
      BckndResourceGroup rg = resourceGroupApi.get(resourceGroupId);
      context.setResult(List.of(toMap(rg)));
    } else {
      List<String> labelSelector = null;
      if (values.containsKey(ResourceGroups.TENANT_ID)) {
        String tenantId = (String) values.get(ResourceGroups.TENANT_ID);
        labelSelector = List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + tenantId);
      }
      BckndResourceGroupList result =
          resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
      context.setResult(mapResources(result.getResources(), this::toMap));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = AICoreService.RESOURCE_GROUPS)
  public void onCreate(CdsCreateEventContext context, List<ResourceGroups> entries) {
    List<Map<String, Object>> results = new ArrayList<>();

    for (ResourceGroups entry : entries) {
      String resourceGroupId = entry.getResourceGroupId();
      BckndResourceGroupsPostRequest request =
          BckndResourceGroupsPostRequest.create().resourceGroupId(resourceGroupId);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> labels =
          (List<Map<String, Object>>) entry.get(ResourceGroups.LABELS);
      List<BckndResourceGroupLabel> mergedLabels = new ArrayList<>();

      // User-supplied labels take precedence: if they include the tenant label key, we skip
      // the auto-generated one based on the tenantId field.
      boolean userSuppliedTenantLabel =
          labels != null
              && labels.stream()
                  .anyMatch(l -> AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.get("key")));

      if (entry.getTenantId() != null && !userSuppliedTenantLabel) {
        mergedLabels.add(
            BckndResourceGroupLabel.create()
                .key(AICoreServiceImpl.TENANT_LABEL_KEY)
                .value(entry.getTenantId()));
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
    List<Map<String, Object>> labels = (List<Map<String, Object>>) data.get(ResourceGroups.LABELS);
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
    if (keys.containsKey(ResourceGroups.RESOURCE_GROUP_ID)) {
      return (String) keys.get(ResourceGroups.RESOURCE_GROUP_ID);
    }
    if (keys.containsKey(ResourceGroups.TENANT_ID)) {
      return service.resourceGroupForTenant((String) keys.get(ResourceGroups.TENANT_ID));
    }
    return service.getDefaultResourceGroup();
  }

  private static List<BckndResourceGroupLabel> toSdkLabels(List<Map<String, Object>> labels) {
    return labels.stream()
        .map(
            l ->
                BckndResourceGroupLabel.create()
                    .key((String) l.get("key"))
                    .value((String) l.get("value")))
        .toList();
  }

  private ResourceGroups toMap(BckndResourceGroup rg) {
    ResourceGroups data = ResourceGroups.create();
    data.setResourceGroupId(rg.getResourceGroupId());
    data.setStatus(rg.getStatus().getValue());
    data.setStatusMessage(rg.getStatusMessage());
    data.put(ResourceGroups.CREATED_AT, rg.getCreatedAt());
    if (rg.getLabels() != null) {
      List<CdsData> labels = new ArrayList<>(rg.getLabels().size());
      for (BckndResourceGroupLabel l : rg.getLabels()) {
        var lm = com.sap.cds.feature.aicore.generated.cds4j.aicore.BckndResourceGroupLabel.create();
        lm.setKey(l.getKey());
        lm.setValue(l.getValue());
        labels.add(lm);
        if (AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.getKey())) {
          data.setTenantId(l.getValue());
        }
      }
      data.put(ResourceGroups.LABELS, labels);
    }
    return data;
  }
}
