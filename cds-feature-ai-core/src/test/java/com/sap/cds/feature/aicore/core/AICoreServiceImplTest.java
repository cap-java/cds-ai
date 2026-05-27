/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
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
import java.time.Duration;
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
  void deploymentLocksFieldIsBoundedCaffeineCache() throws NoSuchFieldException {
    Field field = AICoreServiceImpl.class.getDeclaredField("deploymentLocks");
    assertThat(field.getType()).isEqualTo(Cache.class);
  }

  @Test
  void caffeineGetReturnsSameLockObjectForSameKey() {
    Cache<String, Object> locks =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();

    Object lock1 = locks.get("rg-1", k -> new Object());
    Object lock2 = locks.get("rg-1", k -> new Object());
    Object differentRg = locks.get("rg-2", k -> new Object());

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
    Cache<String, Object> deploymentLocks = readCache(service, "deploymentLocks");

    tenantCache.put(tenantId, resourceGroupId);
    deploymentCache.put(resourceGroupId, "deployment-id");
    deploymentLocks.put(resourceGroupId, new Object());

    service.clearTenantCache(tenantId);

    assertThat(tenantCache.asMap()).doesNotContainKey(tenantId);
    assertThat(deploymentCache.asMap()).doesNotContainKey(resourceGroupId);
    assertThat(deploymentLocks.asMap()).doesNotContainKey(resourceGroupId);
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
    Cache<String, Object> deploymentLocks = readCache(service, "deploymentLocks");

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
    assertThat(deploymentLocks.asMap())
        .doesNotContainKey(resourceGroupA)
        .containsKey(resourceGroupB);
  }

  @Test
  void clearTenantCacheIsNoOpForUnknownTenant() throws Exception {
    String resourceGroupId = "cds-tenant-1";

    AICoreServiceImpl service = freshService();
    Cache<String, String> deploymentCache = readCache(service, "resourceGroupDeploymentCache");
    Cache<String, Object> deploymentLocks = readCache(service, "deploymentLocks");

    deploymentCache.put(resourceGroupId, "deployment-id");
    deploymentLocks.put(resourceGroupId, new Object());

    service.clearTenantCache("unknown-tenant");

    assertThat(deploymentCache.asMap()).containsKey(resourceGroupId);
    assertThat(deploymentLocks.asMap()).containsKey(resourceGroupId);
  }

  private static AICoreServiceImpl freshService() throws Exception {
    AICoreServiceImpl service = mock(AICoreServiceImpl.class, CALLS_REAL_METHODS);
    setField(service, "tenantResourceGroupCache", Caffeine.newBuilder().build());
    setField(service, "resourceGroupDeploymentCache", Caffeine.newBuilder().build());
    setField(service, "deploymentLocks", Caffeine.newBuilder().build());
    return service;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Cache<K, V> readCache(AICoreServiceImpl service, String fieldName)
      throws Exception {
    Field field = AICoreServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Cache<K, V>) field.get(service);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = AICoreServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
