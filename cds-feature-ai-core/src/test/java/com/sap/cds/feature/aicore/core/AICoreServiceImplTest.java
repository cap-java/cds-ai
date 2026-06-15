/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.ai.sdk.core.client.DeploymentApi;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class AICoreServiceImplTest {

  private static final AICoreConfig CONFIG = new AICoreConfig("default", "cds-", 10, 300, true);

  @Test
  void notReadyYet_topLevel403_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(403);

    assertThat(DeploymentResolver.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel412_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(412);

    assertThat(DeploymentResolver.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel404_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(404);

    assertThat(DeploymentResolver.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel500_returnsFalse() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(500);

    assertThat(DeploymentResolver.notReadyYet(e)).isFalse();
  }

  @Test
  void notReadyYet_topLevel500WrappingInner403_returnsTrue() {
    OpenApiRequestException inner = mock(OpenApiRequestException.class);
    when(inner.statusCode()).thenReturn(403);

    OpenApiRequestException outer = mock(OpenApiRequestException.class);
    when(outer.statusCode()).thenReturn(500);
    when(outer.getCause()).thenReturn(inner);

    assertThat(DeploymentResolver.notReadyYet(outer)).isTrue();
  }

  @Test
  void notReadyYet_nullStatusCodeOnAllLevels_returnsFalse() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(null);

    assertThat(DeploymentResolver.notReadyYet(e)).isFalse();
  }

  @Test
  void deploymentLocksFieldIsConcurrentHashMap() throws NoSuchFieldException {
    // Locks must live in a non-evicting map: a Caffeine cache could evict an entry between two
    // threads' lookups, causing them to synchronize on different objects for the same cache key
    // and race to create duplicate AI Core deployments.
    Field field = DeploymentResolver.class.getDeclaredField("deploymentLocks");
    assertThat(field.getType()).isEqualTo(ConcurrentHashMap.class);
  }

  @Test
  void concurrentHashMapComputeIfAbsentReturnsSameLockObjectForSameKey() {
    ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    Object lock1 = locks.computeIfAbsent("rg-1", k -> new Object());
    Object lock2 = locks.computeIfAbsent("rg-1", k -> new Object());
    Object differentRg = locks.computeIfAbsent("rg-2", k -> new Object());

    assertThat(lock1).isSameAs(lock2);
    assertThat(lock1).isNotSameAs(differentRg);
  }

  @Test
  void invalidateTenantRemovesAllRelatedEntries() throws Exception {
    String tenantId = "tenant-1";
    String resourceGroupId = "cds-tenant-1";

    DeploymentResolver resolver = freshResolver();
    putInTenantCache(resolver, tenantId, resourceGroupId);
    putInDeploymentCache(resolver, resourceGroupId, "deployment-id");
    putInDeploymentLocks(resolver, resourceGroupId);

    resolver.invalidateTenant(tenantId);

    assertThat(resolver.getTenantResourceGroupCacheView()).doesNotContainKey(tenantId);
    assertThat(getDeploymentCache(resolver)).doesNotContainKey(resourceGroupId);
    assertThat(getDeploymentLocks(resolver)).doesNotContainKey(resourceGroupId);
  }

  @Test
  void invalidateTenantLeavesOtherTenantsUntouched() throws Exception {
    String tenantA = "tenant-a";
    String resourceGroupA = "cds-tenant-a";
    String tenantB = "tenant-b";
    String resourceGroupB = "cds-tenant-b";

    DeploymentResolver resolver = freshResolver();
    putInTenantCache(resolver, tenantA, resourceGroupA);
    putInTenantCache(resolver, tenantB, resourceGroupB);
    putInDeploymentCache(resolver, resourceGroupA, "deployment-a");
    putInDeploymentCache(resolver, resourceGroupB, "deployment-b");
    putInDeploymentLocks(resolver, resourceGroupA);
    putInDeploymentLocks(resolver, resourceGroupB);

    resolver.invalidateTenant(tenantA);

    assertThat(resolver.getTenantResourceGroupCacheView())
        .doesNotContainKey(tenantA)
        .containsKey(tenantB);
    assertThat(getDeploymentCache(resolver))
        .doesNotContainKey(resourceGroupA)
        .containsKey(resourceGroupB);
    assertThat(getDeploymentLocks(resolver))
        .doesNotContainKey(resourceGroupA)
        .containsKey(resourceGroupB);
  }

  @Test
  void invalidateTenantIsNoOpForUnknownTenant() throws Exception {
    String resourceGroupId = "cds-tenant-1";

    DeploymentResolver resolver = freshResolver();
    putInDeploymentCache(resolver, resourceGroupId, "deployment-id");
    putInDeploymentLocks(resolver, resourceGroupId);

    resolver.invalidateTenant("unknown-tenant");

    assertThat(getDeploymentCache(resolver)).containsKey(resourceGroupId);
    assertThat(getDeploymentLocks(resolver)).containsKey(resourceGroupId);
  }

  private static DeploymentResolver freshResolver() {
    DeploymentApi deploymentApi = mock(DeploymentApi.class);
    return new DeploymentResolver(CONFIG, deploymentApi);
  }

  @SuppressWarnings("unchecked")
  private static void putInTenantCache(DeploymentResolver resolver, String key, String value)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("tenantResourceGroupCache");
    field.setAccessible(true);
    ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver))
        .put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static void putInDeploymentCache(DeploymentResolver resolver, String key, String value)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentCache");
    field.setAccessible(true);
    ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver))
        .put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static java.util.Map<String, String> getDeploymentCache(DeploymentResolver resolver)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentCache");
    field.setAccessible(true);
    return ((com.github.benmanes.caffeine.cache.Cache<String, String>) field.get(resolver)).asMap();
  }

  private static void putInDeploymentLocks(DeploymentResolver resolver, String key)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentLocks");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, Object> locks =
        (ConcurrentHashMap<String, Object>) field.get(resolver);
    locks.put(key, new Object());
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentHashMap<String, Object> getDeploymentLocks(DeploymentResolver resolver)
      throws Exception {
    Field field = DeploymentResolver.class.getDeclaredField("deploymentLocks");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, Object>) field.get(resolver);
  }
}
