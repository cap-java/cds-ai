/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import static org.mockito.Mockito.*;

import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.services.ServiceCatalog;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.environment.CdsEnvironment;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationConfigurationTest {

  @Mock private CdsRuntimeConfigurer configurer;
  @Mock private CdsRuntime runtime;
  @Mock private ServiceCatalog serviceCatalog;
  @Mock private CdsEnvironment environment;
  @Mock private RemoteService aiCoreService;
  @Mock private PersistenceService persistenceService;

  @Test
  void aiCoreServiceFound_registersHandler() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(runtime.getEnvironment()).thenReturn(environment);
    when(environment.getServiceBindings()).thenReturn(Stream.empty());
    when(serviceCatalog.getService(RemoteService.class, AICore.SERVICE_NAME))
        .thenReturn(aiCoreService);
    when(serviceCatalog.getService(PersistenceService.class, PersistenceService.DEFAULT_NAME))
        .thenReturn(persistenceService);

    new RecommendationConfiguration().eventHandlers(configurer);

    verify(configurer).eventHandler(any(FioriRecommendationHandler.class));
  }

  @Test
  void aiCoreServiceNull_doesNotRegisterHandler() {
    when(configurer.getCdsRuntime()).thenReturn(runtime);
    when(runtime.getServiceCatalog()).thenReturn(serviceCatalog);
    when(serviceCatalog.getService(RemoteService.class, AICore.SERVICE_NAME)).thenReturn(null);

    new RecommendationConfiguration().eventHandlers(configurer);

    verify(configurer, never()).eventHandler(any());
  }
}
