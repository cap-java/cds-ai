/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.ai.sdk.core.model.AiDeploymentCreationRequest;
import com.sap.ai.sdk.core.model.AiDeploymentCreationResponse;
import com.sap.ai.sdk.core.model.AiDeploymentModificationRequest;
import com.sap.ai.sdk.core.model.AiExecutionStatus;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentHandlerTest {

  @Mock private AICoreServiceImpl service;
  @Mock private DeploymentApi deploymentApi;
  @Mock private CdsUpdateEventContext updateContext;
  @Mock private CdsCreateEventContext createContext;

  private DeploymentHandler cut;

  @BeforeEach
  void setup() {
    when(service.getDeploymentApi()).thenReturn(deploymentApi);
    cut = new DeploymentHandler(service);
  }

  @Test
  void onUpdate_emptyEntries_throwsBadRequest() {
    List<Deployments> entries = List.of();

    assertThatThrownBy(() -> cut.onUpdate(updateContext, entries))
        .isInstanceOfSatisfying(
            ServiceException.class,
            e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatuses.BAD_REQUEST))
        .hasMessageContaining("No update payload provided");

    verifyNoInteractions(deploymentApi);
  }

  @Test
  void onUpdate_payloadWithoutTargetStatusOrConfigurationId_throwsBadRequest() {
    List<Deployments> entries = List.of(Deployments.of(Map.of("ttl", "1d")));

    assertThatThrownBy(() -> cut.onUpdate(updateContext, entries))
        .isInstanceOfSatisfying(
            ServiceException.class,
            e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatuses.BAD_REQUEST))
        .hasMessageContaining("targetStatus")
        .hasMessageContaining("configurationId");

    verifyNoInteractions(deploymentApi);
  }

  @Test
  void onUpdate_withTargetStatus_callsModifyWithTargetStatus() {
    Deployments data = Deployments.create();
    data.setTargetStatus("STOPPED");
    List<Deployments> entries = List.of(data);

    CqnUpdate cqnUpdate = mock(CqnUpdate.class);
    CdsModel model = mock(CdsModel.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);

    when(updateContext.getCqn()).thenReturn(cqnUpdate);
    when(updateContext.getModel()).thenReturn(model);
    Map<String, Object> keys = new HashMap<>();
    keys.put(Deployments.ID, "dep-123");
    when(analysisResult.targetKeys()).thenReturn(keys);
    when(analyzer.analyze(cqnUpdate)).thenReturn(analysisResult);
    when(service.resolveResourceGroupFromKeys(any())).thenReturn("rg-1");
    when(service.isProviderUser()).thenReturn(true);

    try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
      staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
      cut.onUpdate(updateContext, entries);
    }

    ArgumentCaptor<AiDeploymentModificationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentModificationRequest.class);
    verify(deploymentApi).modify(eq("rg-1"), eq("dep-123"), captor.capture());
    assertThat(captor.getValue().getTargetStatus().getValue()).isEqualTo("STOPPED");
  }

  @Test
  void onUpdate_withConfigurationId_callsModifyWithConfigurationId() {
    Deployments data = Deployments.create();
    data.setConfigurationId("config-456");
    List<Deployments> entries = List.of(data);

    CqnUpdate cqnUpdate = mock(CqnUpdate.class);
    CdsModel model = mock(CdsModel.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);

    when(updateContext.getCqn()).thenReturn(cqnUpdate);
    when(updateContext.getModel()).thenReturn(model);
    Map<String, Object> keys = new HashMap<>();
    keys.put(Deployments.ID, "dep-789");
    when(analysisResult.targetKeys()).thenReturn(keys);
    when(analyzer.analyze(cqnUpdate)).thenReturn(analysisResult);
    when(service.resolveResourceGroupFromKeys(any())).thenReturn("rg-2");
    when(service.isProviderUser()).thenReturn(true);

    try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
      staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);
      cut.onUpdate(updateContext, entries);
    }

    ArgumentCaptor<AiDeploymentModificationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentModificationRequest.class);
    verify(deploymentApi).modify(eq("rg-2"), eq("dep-789"), captor.capture());
    assertThat(captor.getValue().getConfigurationId()).isEqualTo("config-456");
  }

  @Test
  void onCreate_createsDeploymentWithConfigurationId() {
    Deployments entry = Deployments.create();
    entry.setConfigurationId("cfg-1");
    entry.put(Deployments.RESOURCE_GROUP, Map.of("resourceGroupId", "rg-test"));
    List<Deployments> entries = List.of(entry);

    AiDeploymentCreationResponse response = mock(AiDeploymentCreationResponse.class);
    when(response.getId()).thenReturn("new-dep-id");
    when(response.getStatus()).thenReturn(AiExecutionStatus.UNKNOWN);
    when(service.resolveResourceGroupFromKeys(any())).thenReturn("rg-test");
    when(service.isProviderUser()).thenReturn(true);
    when(deploymentApi.create(eq("rg-test"), any(AiDeploymentCreationRequest.class)))
        .thenReturn(response);

    cut.onCreate(createContext, entries);

    verify(createContext).setResult(any(List.class));
    ArgumentCaptor<AiDeploymentCreationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentCreationRequest.class);
    verify(deploymentApi).create(eq("rg-test"), captor.capture());
    assertThat(captor.getValue().getConfigurationId()).isEqualTo("cfg-1");
  }

  @Test
  void onCreate_withTtl_setsTtlOnRequest() {
    Deployments entry = Deployments.create();
    entry.setConfigurationId("cfg-2");
    entry.setTtl("PT24H");
    List<Deployments> entries = List.of(entry);

    AiDeploymentCreationResponse response = mock(AiDeploymentCreationResponse.class);
    when(response.getId()).thenReturn("dep-ttl");
    when(response.getStatus()).thenReturn(AiExecutionStatus.UNKNOWN);
    when(service.resolveResourceGroupFromKeys(any())).thenReturn("rg-default");
    when(service.isProviderUser()).thenReturn(true);
    when(deploymentApi.create(eq("rg-default"), any(AiDeploymentCreationRequest.class)))
        .thenReturn(response);

    cut.onCreate(createContext, entries);

    ArgumentCaptor<AiDeploymentCreationRequest> captor =
        ArgumentCaptor.forClass(AiDeploymentCreationRequest.class);
    verify(deploymentApi).create(eq("rg-default"), captor.capture());
    assertThat(captor.getValue().getTtl()).isEqualTo("PT24H");
  }
}
