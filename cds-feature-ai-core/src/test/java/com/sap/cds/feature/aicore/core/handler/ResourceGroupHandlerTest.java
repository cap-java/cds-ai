/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupList;
import com.sap.ai.sdk.core.model.BckndResourceGroupPatchRequest;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceGroupHandlerTest {

  @Mock private AICoreServiceImpl service;
  @Mock private ResourceGroupApi resourceGroupApi;
  @Mock private CdsCreateEventContext createContext;

  private ResourceGroupHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ResourceGroupHandler(resourceGroupApi);
  }

  @Test
  void onCreate_withTenantIdOnly_setsOnlyTenantLabel() {
    Map<String, Object> entry = Map.of("resourceGroupId", "rg-1", "tenantId", "tenant-a");
    List<ResourceGroups> entries = List.of(ResourceGroups.of(entry));

    handler.onCreate(createContext, entries);

    BckndResourceGroupsPostRequest request = captureCreateRequest();
    assertThat(request.getResourceGroupId()).isEqualTo("rg-1");
    assertThat(request.getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple(AICoreServiceImpl.TENANT_LABEL_KEY, "tenant-a"));
  }

  @Test
  void onCreate_withLabelsOnly_setsOnlyUserLabels() {
    Map<String, Object> entry =
        Map.of(
            "resourceGroupId",
            "rg-2",
            "labels",
            List.of(Map.of("key", "env", "value", "prod"), Map.of("key", "team", "value", "ai")));
    List<ResourceGroups> entries = List.of(ResourceGroups.of(entry));

    handler.onCreate(createContext, entries);

    BckndResourceGroupsPostRequest request = captureCreateRequest();
    assertThat(request.getResourceGroupId()).isEqualTo("rg-2");
    assertThat(request.getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple("env", "prod"), tuple("team", "ai"));
  }

  @Test
  void onCreate_withTenantIdAndLabels_keepsTenantLabelAndUserLabels() {
    Map<String, Object> entry =
        Map.of(
            "resourceGroupId",
            "rg-3",
            "tenantId",
            "tenant-b",
            "labels",
            List.of(Map.of("key", "env", "value", "prod")));
    List<ResourceGroups> entries = List.of(ResourceGroups.of(entry));

    handler.onCreate(createContext, entries);

    BckndResourceGroupsPostRequest request = captureCreateRequest();
    // Tenant label first, then user-supplied labels — and tenant label is NOT lost.
    assertThat(request.getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(
            tuple(AICoreServiceImpl.TENANT_LABEL_KEY, "tenant-b"), tuple("env", "prod"));
  }

  @Test
  void onCreate_userSuppliedTenantLabelTakesPrecedence() {
    Map<String, Object> entry =
        Map.of(
            "resourceGroupId",
            "rg-4",
            "tenantId",
            "tenant-auto",
            "labels",
            List.of(Map.of("key", AICoreServiceImpl.TENANT_LABEL_KEY, "value", "tenant-user")));
    List<ResourceGroups> entries = List.of(ResourceGroups.of(entry));

    handler.onCreate(createContext, entries);

    BckndResourceGroupsPostRequest request = captureCreateRequest();
    assertThat(request.getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple(AICoreServiceImpl.TENANT_LABEL_KEY, "tenant-user"));
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class OnUpdateTests {

    @Mock private CdsUpdateEventContext updateContext;
    @Mock private CqnUpdate cqnUpdate;
    @Mock private CdsModel model;
    @Mock private CqnAnalyzer analyzer;
    @Mock private AnalysisResult analysisResult;

    @Test
    void onUpdate_withLabels_callsPatchWithLabels() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-upd");
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analyzer.analyze(cqnUpdate)).thenReturn(analysisResult);
      when(updateContext.getCqn()).thenReturn(cqnUpdate);
      when(updateContext.getModel()).thenReturn(model);
      when(updateContext.getService()).thenReturn(service);

      Map<String, Object> data = new HashMap<>();
      data.put(ResourceGroups.LABELS, List.of(Map.of("key", "env", "value", "staging")));
      when(cqnUpdate.entries()).thenReturn(List.of(data));

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
      when(label.getKey()).thenReturn(AICoreServiceImpl.TENANT_LABEL_KEY);
      when(label.getValue()).thenReturn("my-tenant");
      when(rg.getLabels()).thenReturn(List.of(label));
      when(resourceGroupApi.get("rg-upd")).thenReturn(rg);
      when(service.isProviderUser()).thenReturn(false);
      when(service.isMultiTenancyEnabled()).thenReturn(true);
      when(service.currentTenantId()).thenReturn("my-tenant");

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onUpdate(updateContext);
      }

      ArgumentCaptor<BckndResourceGroupPatchRequest> captor =
          ArgumentCaptor.forClass(BckndResourceGroupPatchRequest.class);
      verify(resourceGroupApi).patch(eq("rg-upd"), captor.capture());
      assertThat(captor.getValue().getLabels())
          .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
          .containsExactly(tuple("env", "staging"));
    }

    @Test
    void onUpdate_withoutLabels_callsPatchWithoutLabels() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-nolabel");
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analyzer.analyze(cqnUpdate)).thenReturn(analysisResult);
      when(updateContext.getCqn()).thenReturn(cqnUpdate);
      when(updateContext.getModel()).thenReturn(model);
      when(updateContext.getService()).thenReturn(service);

      Map<String, Object> data = new HashMap<>();
      // no labels in update payload
      when(cqnUpdate.entries()).thenReturn(List.of(data));

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      when(resourceGroupApi.get("rg-nolabel")).thenReturn(rg);
      when(service.isProviderUser()).thenReturn(true);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onUpdate(updateContext);
      }

      ArgumentCaptor<BckndResourceGroupPatchRequest> captor =
          ArgumentCaptor.forClass(BckndResourceGroupPatchRequest.class);
      verify(resourceGroupApi).patch(eq("rg-nolabel"), captor.capture());
      assertThat(captor.getValue().getLabels()).isNullOrEmpty();
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class BuildTenantLabelSelectorTests {

    @Mock private CdsReadEventContext readContext;
    @Mock private CqnSelect cqnSelect;
    @Mock private CdsModel model;
    @Mock private CqnAnalyzer analyzer;
    @Mock private AnalysisResult analysisResult;

    @Test
    void readAll_withTenantIdFilter_usesLabelSelector() {
      Map<String, Object> keys = new HashMap<>();
      Map<String, Object> values = new HashMap<>();
      values.put(ResourceGroups.TENANT_ID, "tenant-x");
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);

      BckndResourceGroupList result = mock(BckndResourceGroupList.class);
      when(result.getResources()).thenReturn(List.of());
      when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(result);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onRead(readContext);
      }

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> selectorCaptor = ArgumentCaptor.forClass(List.class);
      verify(resourceGroupApi)
          .getAll(any(), any(), any(), any(), any(), any(), selectorCaptor.capture());
      assertThat(selectorCaptor.getValue())
          .containsExactly(AICoreServiceImpl.TENANT_LABEL_KEY + "=tenant-x");
    }

    @Test
    void readAll_multiTenancy_nonProviderUser_restrictsByCurrentTenant() {
      Map<String, Object> keys = new HashMap<>();
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isMultiTenancyEnabled()).thenReturn(true);
      when(service.isProviderUser()).thenReturn(false);
      when(service.currentTenantId()).thenReturn("current-tenant");

      BckndResourceGroupList result = mock(BckndResourceGroupList.class);
      when(result.getResources()).thenReturn(List.of());
      when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(result);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onRead(readContext);
      }

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> selectorCaptor = ArgumentCaptor.forClass(List.class);
      verify(resourceGroupApi)
          .getAll(any(), any(), any(), any(), any(), any(), selectorCaptor.capture());
      assertThat(selectorCaptor.getValue())
          .containsExactly(AICoreServiceImpl.TENANT_LABEL_KEY + "=current-tenant");
    }

    @Test
    void readAll_multiTenancy_nullTenant_noLabelSelector() {
      Map<String, Object> keys = new HashMap<>();
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isMultiTenancyEnabled()).thenReturn(true);
      when(service.isProviderUser()).thenReturn(false);
      when(service.currentTenantId()).thenReturn(null);

      BckndResourceGroupList result = mock(BckndResourceGroupList.class);
      when(result.getResources()).thenReturn(List.of());
      when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(result);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onRead(readContext);
      }

      verify(resourceGroupApi).getAll(any(), any(), any(), any(), any(), any(), eq(null));
    }

    @Test
    void readAll_singleTenancy_noLabelSelector() {
      Map<String, Object> keys = new HashMap<>();
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isMultiTenancyEnabled()).thenReturn(false);

      BckndResourceGroupList result = mock(BckndResourceGroupList.class);
      when(result.getResources()).thenReturn(List.of());
      when(resourceGroupApi.getAll(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(result);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        handler.onRead(readContext);
      }

      verify(resourceGroupApi).getAll(any(), any(), any(), any(), any(), any(), eq(null));
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class EnsureOwnedByCurrentTenantTests {

    @Mock private CdsReadEventContext readContext;
    @Mock private CqnSelect cqnSelect;
    @Mock private CdsModel model;
    @Mock private CqnAnalyzer analyzer;
    @Mock private AnalysisResult analysisResult;

    @Test
    void readById_providerUser_allowsAccessToAnyRg() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-any");
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isProviderUser()).thenReturn(true);

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      when(rg.getResourceGroupId()).thenReturn("rg-any");
      when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);
      when(rg.getLabels()).thenReturn(null);
      when(resourceGroupApi.get("rg-any")).thenReturn(rg);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        assertThatCode(() -> handler.onRead(readContext)).doesNotThrowAnyException();
      }
    }

    @Test
    void readById_singleTenancy_allowsAccess() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-single");
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isProviderUser()).thenReturn(false);
      when(service.isMultiTenancyEnabled()).thenReturn(false);

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      when(rg.getResourceGroupId()).thenReturn("rg-single");
      when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);
      when(rg.getLabels()).thenReturn(null);
      when(resourceGroupApi.get("rg-single")).thenReturn(rg);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        assertThatCode(() -> handler.onRead(readContext)).doesNotThrowAnyException();
      }
    }

    @Test
    void readById_multiTenancy_wrongTenant_throws404() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-other");
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isProviderUser()).thenReturn(false);
      when(service.isMultiTenancyEnabled()).thenReturn(true);
      when(service.currentTenantId()).thenReturn("tenant-a");

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
      when(label.getKey()).thenReturn(AICoreServiceImpl.TENANT_LABEL_KEY);
      when(label.getValue()).thenReturn("tenant-b");
      when(rg.getLabels()).thenReturn(List.of(label));
      when(resourceGroupApi.get("rg-other")).thenReturn(rg);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        assertThatThrownBy(() -> handler.onRead(readContext))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("not found");
      }
    }

    @Test
    void readById_multiTenancy_matchingTenant_allowsAccess() {
      Map<String, Object> keys = new HashMap<>();
      keys.put(ResourceGroups.RESOURCE_GROUP_ID, "rg-mine");
      Map<String, Object> values = new HashMap<>();
      when(analysisResult.targetKeys()).thenReturn(keys);
      when(analysisResult.targetValues()).thenReturn(values);
      when(analyzer.analyze(cqnSelect)).thenReturn(analysisResult);
      when(readContext.getCqn()).thenReturn(cqnSelect);
      when(readContext.getModel()).thenReturn(model);
      when(readContext.getService()).thenReturn(service);
      when(service.isProviderUser()).thenReturn(false);
      when(service.isMultiTenancyEnabled()).thenReturn(true);
      when(service.currentTenantId()).thenReturn("tenant-a");

      BckndResourceGroup rg = mock(BckndResourceGroup.class);
      BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
      when(label.getKey()).thenReturn(AICoreServiceImpl.TENANT_LABEL_KEY);
      when(label.getValue()).thenReturn("tenant-a");
      when(rg.getLabels()).thenReturn(List.of(label));
      when(rg.getResourceGroupId()).thenReturn("rg-mine");
      when(rg.getStatus()).thenReturn(BckndResourceGroup.StatusEnum.PROVISIONED);
      when(resourceGroupApi.get("rg-mine")).thenReturn(rg);

      try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
        staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
        assertThatCode(() -> handler.onRead(readContext)).doesNotThrowAnyException();
      }
    }
  }

  private BckndResourceGroupsPostRequest captureCreateRequest() {
    ArgumentCaptor<BckndResourceGroupsPostRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupsPostRequest.class);
    verify(resourceGroupApi).create(captor.capture());
    return captor.getValue();
  }
}
