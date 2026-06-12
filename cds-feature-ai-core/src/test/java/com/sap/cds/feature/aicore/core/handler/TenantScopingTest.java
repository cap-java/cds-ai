/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.ResourceGroupApi;
import com.sap.ai.sdk.core.model.BckndResourceGroup;
import com.sap.ai.sdk.core.model.BckndResourceGroupLabel;
import com.sap.cds.feature.aicore.core.AICoreServiceImpl;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the tenant-scoping guard methods in {@link AbstractCrudHandler}.
 * Tests the {@code ensureResourceGroupAccessible} logic which is used by
 * ConfigurationHandler and DeploymentHandler.
 */
@ExtendWith(MockitoExtension.class)
class TenantScopingTest {

  @Mock private AICoreServiceImpl service;
  @Mock private ResourceGroupApi resourceGroupApi;
  @Mock private EventContext eventContext;

  /** Concrete subclass to expose the protected method for testing. */
  private static class TestableHandler extends AbstractCrudHandler {
    TestableHandler(ResourceGroupApi resourceGroupApi) {
      super(resourceGroupApi);
    }

    void callEnsureResourceGroupAccessible(EventContext context, String resourceGroupId) {
      ensureResourceGroupAccessible(context, resourceGroupId);
    }
  }

  private TestableHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TestableHandler(resourceGroupApi);
    when(eventContext.getService()).thenReturn(service);
  }

  // ── ensureResourceGroupAccessible ──────────────────────────────────────────

  @Test
  void providerUser_allowsAccessToAnyResourceGroup() {
    when(service.isProviderUser()).thenReturn(true);

    assertThatCode(() -> handler.callEnsureResourceGroupAccessible(eventContext, "any-rg"))
        .doesNotThrowAnyException();
    verify(resourceGroupApi, never()).get("any-rg");
  }

  @Test
  void singleTenancy_allowsAccessToAnyResourceGroup() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(false);

    assertThatCode(() -> handler.callEnsureResourceGroupAccessible(eventContext, "any-rg"))
        .doesNotThrowAnyException();
    verify(resourceGroupApi, never()).get("any-rg");
  }

  @Test
  void multiTenancy_nullTenant_allowsAccess() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(true);
    when(service.currentTenantId()).thenReturn(null);

    assertThatCode(() -> handler.callEnsureResourceGroupAccessible(eventContext, "any-rg"))
        .doesNotThrowAnyException();
    verify(resourceGroupApi, never()).get("any-rg");
  }

  @Test
  void multiTenancy_matchingTenantLabel_allowsAccess() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(true);
    when(service.currentTenantId()).thenReturn("tenant-a");

    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
    when(label.getKey()).thenReturn(AICoreServiceImpl.TENANT_LABEL_KEY);
    when(label.getValue()).thenReturn("tenant-a");
    when(rg.getLabels()).thenReturn(List.of(label));
    when(resourceGroupApi.get("rg-for-a")).thenReturn(rg);

    assertThatCode(() -> handler.callEnsureResourceGroupAccessible(eventContext, "rg-for-a"))
        .doesNotThrowAnyException();
  }

  @Test
  void multiTenancy_nonMatchingTenantLabel_throws404() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(true);
    when(service.currentTenantId()).thenReturn("tenant-a");

    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    BckndResourceGroupLabel label = mock(BckndResourceGroupLabel.class);
    when(label.getKey()).thenReturn(AICoreServiceImpl.TENANT_LABEL_KEY);
    when(label.getValue()).thenReturn("tenant-b");
    when(rg.getLabels()).thenReturn(List.of(label));
    when(resourceGroupApi.get("rg-for-b")).thenReturn(rg);

    assertThatThrownBy(() -> handler.callEnsureResourceGroupAccessible(eventContext, "rg-for-b"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void multiTenancy_noLabels_throws404() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(true);
    when(service.currentTenantId()).thenReturn("tenant-a");

    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getLabels()).thenReturn(null);
    when(resourceGroupApi.get("rg-no-labels")).thenReturn(rg);

    assertThatThrownBy(() -> handler.callEnsureResourceGroupAccessible(eventContext, "rg-no-labels"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void multiTenancy_emptyLabels_throws404() {
    when(service.isProviderUser()).thenReturn(false);
    when(service.isMultiTenancyEnabled()).thenReturn(true);
    when(service.currentTenantId()).thenReturn("tenant-a");

    BckndResourceGroup rg = mock(BckndResourceGroup.class);
    when(rg.getLabels()).thenReturn(List.of());
    when(resourceGroupApi.get("rg-empty-labels")).thenReturn(rg);

    assertThatThrownBy(() -> handler.callEnsureResourceGroupAccessible(eventContext, "rg-empty-labels"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("not found");
  }
}
