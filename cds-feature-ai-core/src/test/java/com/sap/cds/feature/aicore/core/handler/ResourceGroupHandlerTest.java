/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.ai.sdk.core.model.BckndResourceGroupsPostRequest;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.ResourceGroups;
import com.sap.cds.services.cds.CdsCreateEventContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceGroupHandlerTest {

  @Mock private AICoreServiceImpl service;
  @Mock private ResourceGroupApi resourceGroupApi;
  @Mock private CdsCreateEventContext context;

  private ResourceGroupHandler handler;

  @BeforeEach
  void setUp() {
    when(service.getResourceGroupApi()).thenReturn(resourceGroupApi);
    handler = new ResourceGroupHandler(service);
  }

  @Test
  void onCreate_withTenantIdOnly_setsOnlyTenantLabel() {
    Map<String, Object> entry = Map.of("resourceGroupId", "rg-1", "tenantId", "tenant-a");
    List<ResourceGroups> entries = List.of(ResourceGroups.of(entry));

    handler.onCreate(context, entries);

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

    handler.onCreate(context, entries);

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

    handler.onCreate(context, entries);

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

    handler.onCreate(context, entries);

    BckndResourceGroupsPostRequest request = captureCreateRequest();
    assertThat(request.getLabels())
        .extracting(BckndResourceGroupLabel::getKey, BckndResourceGroupLabel::getValue)
        .containsExactly(tuple(AICoreServiceImpl.TENANT_LABEL_KEY, "tenant-user"));
  }

  private BckndResourceGroupsPostRequest captureCreateRequest() {
    ArgumentCaptor<BckndResourceGroupsPostRequest> captor =
        ArgumentCaptor.forClass(BckndResourceGroupsPostRequest.class);
    verify(resourceGroupApi).create(captor.capture());
    return captor.getValue();
  }
}
