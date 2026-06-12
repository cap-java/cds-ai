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
import com.sap.cds.feature.aicore.core.AbstractAICoreService;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups_;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
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

  public ResourceGroupHandler(ResourceGroupApi resourceGroupApi) {
    super(resourceGroupApi);
    this.resourceGroupApi = resourceGroupApi;
  }

  @On(event = CqnService.EVENT_READ, entity = ResourceGroups_.CDS_NAME)
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
      ensureOwnedByCurrentTenant(context, rg);
      context.setResult(List.of(toMap(rg)));
    } else {
      List<String> labelSelector = buildTenantLabelSelector(context, values);
      BckndResourceGroupList result =
          resourceGroupApi.getAll(null, null, null, null, null, null, labelSelector);
      context.setResult(mapResources(result.getResources(), this::toMap));
    }
  }

  @On(event = CqnService.EVENT_CREATE, entity = ResourceGroups_.CDS_NAME)
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

  @On(event = CqnService.EVENT_UPDATE, entity = ResourceGroups_.CDS_NAME)
  public void onUpdate(CdsUpdateEventContext context) {
    CqnUpdate update = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();

    String resourceGroupId = resolveResourceGroupId(context, keys);
    ensureOwnedByCurrentTenant(context, resourceGroupApi.get(resourceGroupId));

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

  @On(event = CqnService.EVENT_DELETE, entity = ResourceGroups_.CDS_NAME)
  public void onDelete(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();

    String resourceGroupId = resolveResourceGroupId(context, keys);
    ensureOwnedByCurrentTenant(context, resourceGroupApi.get(resourceGroupId));

    resourceGroupApi.delete(resourceGroupId);
    logger.debug("Deleted resource group {}", resourceGroupId);
    context.setResult(List.of());
  }

  private String resolveResourceGroupId(EventContext context, Map<String, Object> keys) {
    if (keys.containsKey(ResourceGroups.RESOURCE_GROUP_ID)) {
      return (String) keys.get(ResourceGroups.RESOURCE_GROUP_ID);
    }
    AbstractAICoreService svc = getService(context);
    if (keys.containsKey(ResourceGroups.TENANT_ID)) {
      return svc.resourceGroupForTenant((String) keys.get(ResourceGroups.TENANT_ID));
    }
    return svc.resourceGroup();
  }

  /**
   * Builds a tenant-scoped label selector for list queries. In multi-tenancy mode, non-provider
   * users are restricted to their own tenant's resource groups.
   */
  private List<String> buildTenantLabelSelector(EventContext context, Map<String, Object> values) {
    // If a specific tenantId is requested in the query, use that
    if (values.containsKey(ResourceGroups.TENANT_ID)) {
      String tenantId = (String) values.get(ResourceGroups.TENANT_ID);
      return List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + tenantId);
    }
    // In MT mode, restrict non-provider users to their own tenant
    AbstractAICoreService svc = getService(context);
    if (svc.isMultiTenancyEnabled() && !svc.isProviderUser()) {
      String currentTenant = svc.currentTenantId();
      if (currentTenant != null) {
        return List.of(AICoreServiceImpl.TENANT_LABEL_KEY + "=" + currentTenant);
      }
    }
    return null;
  }

  /**
   * Verifies that the given resource group is owned by the current tenant. Provider/system users
   * are allowed to access any resource group. Throws 404 if the resource group belongs to a
   * different tenant.
   */
  private void ensureOwnedByCurrentTenant(EventContext context, BckndResourceGroup rg) {
    AbstractAICoreService svc = getService(context);
    if (svc.isProviderUser()) {
      return;
    }
    if (!svc.isMultiTenancyEnabled()) {
      return;
    }
    String currentTenant = svc.currentTenantId();
    if (currentTenant == null) {
      return;
    }
    if (rg.getLabels() != null
        && rg.getLabels().stream()
            .anyMatch(
                l ->
                    AICoreServiceImpl.TENANT_LABEL_KEY.equals(l.getKey())
                        && currentTenant.equals(l.getValue()))) {
      return;
    }
    throw new ServiceException(ErrorStatuses.NOT_FOUND, "Resource group not found");
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
