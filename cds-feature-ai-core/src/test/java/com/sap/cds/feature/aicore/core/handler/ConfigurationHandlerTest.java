/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.ConfigurationApi;
import com.sap.ai.sdk.core.model.AiConfigurationList;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.CdsReadEventContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigurationHandlerTest {

  @Mock private AICoreServiceImpl service;
  @Mock private ConfigurationApi configurationApi;
  @Mock private CdsReadEventContext context;
  @Mock private CqnSelect select;
  @Mock private CdsModel model;
  @Mock private CqnAnalyzer analyzer;
  @Mock private AnalysisResult analysisResult;

  @Test
  void onRead_nullResources_returnsEmptyListWithoutNpe() {
    when(service.getConfigurationApi()).thenReturn(configurationApi);
    when(context.getCqn()).thenReturn(select);
    when(context.getModel()).thenReturn(model);
    when(analyzer.analyze(select)).thenReturn(analysisResult);
    when(analysisResult.targetKeys()).thenReturn(new HashMap<>());
    when(analysisResult.targetValues()).thenReturn(new HashMap<>());
    when(service.resolveResourceGroupFromKeys(any())).thenReturn("default");

    AiConfigurationList listWithNullResources = mock(AiConfigurationList.class);
    when(listWithNullResources.getResources()).thenReturn(null);
    when(configurationApi.query(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(listWithNullResources);

    try (MockedStatic<CqnAnalyzer> staticAnalyzer = mockStatic(CqnAnalyzer.class)) {
      staticAnalyzer.when(() -> CqnAnalyzer.create(model)).thenReturn(analyzer);

      ConfigurationHandler handler = new ConfigurationHandler(service);
      handler.onRead(context);
    }

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
    verify(context).setResult(captor.capture());
    assertThat(captor.getValue()).isEmpty();
  }
}
