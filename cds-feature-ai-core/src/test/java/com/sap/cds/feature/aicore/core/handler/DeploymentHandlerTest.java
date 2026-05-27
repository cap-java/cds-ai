/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.Deployments;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentHandlerTest {

  @Mock private AICoreServiceImpl service;
  @Mock private DeploymentApi deploymentApi;
  @Mock private CdsUpdateEventContext context;

  private DeploymentHandler cut;

  @BeforeEach
  void setup() {
    when(service.getDeploymentApi()).thenReturn(deploymentApi);
    cut = new DeploymentHandler(service);
  }

  @Test
  void onUpdate_emptyEntries_throwsBadRequest() {
    List<Deployments> entries = List.of();

    assertThatThrownBy(() -> cut.onUpdate(context, entries))
        .isInstanceOfSatisfying(
            ServiceException.class,
            e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatuses.BAD_REQUEST))
        .hasMessageContaining("No update payload provided");

    verifyNoInteractions(deploymentApi);
  }

  @Test
  void onUpdate_payloadWithoutTargetStatusOrConfigurationId_throwsBadRequest() {
    List<Deployments> entries = List.of(Deployments.of(Map.of("ttl", "1d")));

    assertThatThrownBy(() -> cut.onUpdate(context, entries))
        .isInstanceOfSatisfying(
            ServiceException.class,
            e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatuses.BAD_REQUEST))
        .hasMessageContaining("targetStatus")
        .hasMessageContaining("configurationId");

    verifyNoInteractions(deploymentApi);
  }
}
