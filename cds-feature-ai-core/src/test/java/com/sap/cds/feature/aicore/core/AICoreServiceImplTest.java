/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sap.cloud.sdk.services.openapi.apache.core.OpenApiRequestException;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class AICoreServiceImplTest {

  @Test
  void notReadyYet_topLevel403_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(403);

    assertThat(AICoreServiceImpl.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel412_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(412);

    assertThat(AICoreServiceImpl.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel404_returnsTrue() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(404);

    assertThat(AICoreServiceImpl.notReadyYet(e)).isTrue();
  }

  @Test
  void notReadyYet_topLevel500_returnsFalse() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(500);

    assertThat(AICoreServiceImpl.notReadyYet(e)).isFalse();
  }

  @Test
  void notReadyYet_topLevel500WrappingInner403_returnsTrue() {
    OpenApiRequestException inner = mock(OpenApiRequestException.class);
    when(inner.statusCode()).thenReturn(403);

    OpenApiRequestException outer = mock(OpenApiRequestException.class);
    when(outer.statusCode()).thenReturn(500);
    when(outer.getCause()).thenReturn(inner);

    assertThat(AICoreServiceImpl.notReadyYet(outer)).isTrue();
  }

  @Test
  void notReadyYet_nullStatusCodeOnAllLevels_returnsFalse() {
    OpenApiRequestException e = mock(OpenApiRequestException.class);
    when(e.statusCode()).thenReturn(null);

    assertThat(AICoreServiceImpl.notReadyYet(e)).isFalse();
  }

  @Test
  void deploymentLocksFieldIsConcurrentHashMap() throws NoSuchFieldException {
    // Locks must live in a non-evicting map: a Caffeine cache could evict an entry between two
    // threads' lookups, causing them to synchronize on different objects for the same cache key
    // and race to create duplicate AI Core deployments.
    Field field = AICoreServiceImpl.class.getDeclaredField("deploymentLocks");
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
  void clearTenantCacheRemovesAllRelatedEntries() throws Exception {
    String tenantId = "tenant-1";
    String resourceGroupId = "cds-tenant-1";

    AICoreServiceImpl service = freshService();
    Cache<String, String> tenantCache = readCache(service, "tenantResourceGroupCache");
    Cache<String, String> deploymentCache = readCache(service, "resourceGroupDeploymentCache");
    ConcurrentHashMap<String, Object> deploymentLocks = readLocks(service);

    tenantCache.put(tenantId, resourceGroupId);
    deploymentCache.put(resourceGroupId, "deployment-id");
    deploymentLocks.put(resourceGroupId, new Object());

    service.clearTenantCache(tenantId);

    assertThat(tenantCache.asMap()).doesNotContainKey(tenantId);
    assertThat(deploymentCache.asMap()).doesNotContainKey(resourceGroupId);
    assertThat(deploymentLocks).doesNotContainKey(resourceGroupId);
  }

  @Test
  void clearTenantCacheLeavesOtherTenantsUntouched() throws Exception {
    String tenantA = "tenant-a";
    String resourceGroupA = "cds-tenant-a";
    String tenantB = "tenant-b";
    String resourceGroupB = "cds-tenant-b";

    AICoreServiceImpl service = freshService();
    Cache<String, String> tenantCache = readCache(service, "tenantResourceGroupCache");
    Cache<String, String> deploymentCache = readCache(service, "resourceGroupDeploymentCache");
    ConcurrentHashMap<String, Object> deploymentLocks = readLocks(service);

    tenantCache.put(tenantA, resourceGroupA);
    tenantCache.put(tenantB, resourceGroupB);
    deploymentCache.put(resourceGroupA, "deployment-a");
    deploymentCache.put(resourceGroupB, "deployment-b");
    deploymentLocks.put(resourceGroupA, new Object());
    deploymentLocks.put(resourceGroupB, new Object());

    service.clearTenantCache(tenantA);

    assertThat(tenantCache.asMap()).doesNotContainKey(tenantA).containsKey(tenantB);
    assertThat(deploymentCache.asMap())
        .doesNotContainKey(resourceGroupA)
        .containsKey(resourceGroupB);
    assertThat(deploymentLocks).doesNotContainKey(resourceGroupA).containsKey(resourceGroupB);
  }

  @Test
  void clearTenantCacheIsNoOpForUnknownTenant() throws Exception {
    String resourceGroupId = "cds-tenant-1";

    AICoreServiceImpl service = freshService();
    Cache<String, String> deploymentCache = readCache(service, "resourceGroupDeploymentCache");
    ConcurrentHashMap<String, Object> deploymentLocks = readLocks(service);

    deploymentCache.put(resourceGroupId, "deployment-id");
    deploymentLocks.put(resourceGroupId, new Object());

    service.clearTenantCache("unknown-tenant");

    assertThat(deploymentCache.asMap()).containsKey(resourceGroupId);
    assertThat(deploymentLocks).containsKey(resourceGroupId);
  }

  private static AICoreServiceImpl freshService() throws Exception {
    AICoreServiceImpl service = mock(AICoreServiceImpl.class, CALLS_REAL_METHODS);
    setField(service, "tenantResourceGroupCache", Caffeine.newBuilder().build());
    setField(service, "resourceGroupDeploymentCache", Caffeine.newBuilder().build());
    setField(service, "deploymentLocks", new ConcurrentHashMap<>());
    return service;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Cache<K, V> readCache(AICoreServiceImpl service, String fieldName)
      throws Exception {
    Field field = AICoreServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Cache<K, V>) field.get(service);
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentHashMap<String, Object> readLocks(AICoreServiceImpl service)
      throws Exception {
    Field field = AICoreServiceImpl.class.getDeclaredField("deploymentLocks");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, Object>) field.get(service);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = AICoreServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
