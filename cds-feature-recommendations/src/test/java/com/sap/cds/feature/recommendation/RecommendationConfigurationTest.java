/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import static org.mockito.Mockito.*;

import com.sap.cds.feature.aicore.core.AICoreService;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationConfigurationTest {

  @Mock private CdsRuntimeConfigurer configurer;
  @Mock private CdsRuntime runtime;
  @Mock private ServiceCatalog serviceCatalog;
  @Mock private AICoreService aiCoreService;

  @Test
  void aiCoreServiceFound_registersHandler() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(serviceCatalog.getService(AICoreService.class, AICoreService.DEFAULT_NAME))
        .thenReturn(aiCoreService);

    new RecommendationConfiguration().eventHandlers(configurer);

    verify(configurer).eventHandler(any(FioriRecommendationHandler.class));
  }

  @Test
  void aiCoreServiceNull_doesNotRegisterHandler() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(serviceCatalog.getService(AICoreService.class, AICoreService.DEFAULT_NAME))
        .thenReturn(null);

    new RecommendationConfiguration().eventHandlers(configurer);

    verify(configurer, never()).eventHandler(any());
  }
}
