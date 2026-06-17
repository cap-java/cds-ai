/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.api;

/**
 * Constants for the AI Core plugin.
 *
 * <p>Use {@link #SERVICE_NAME} for service catalog lookups and {@code @ServiceName} annotations.
 * Use the entity constants for {@code @On(entity = ...)} handler annotations.
 *
 * <p>The service is a {@link com.sap.cds.services.cds.RemoteService RemoteService} auto-created by
 * the CAP Java runtime from the CDS model. Callers interact with it by emitting typed {@link
 * com.sap.cds.services.EventContext EventContext} instances ({@link ResourceGroupContext}, {@link
 * DeploymentIdContext}, {@link InferenceClientContext}) or via CQL on the defined entities.
 */
public final class AICore {

  private AICore() {}

  /** Service name matching the CDS service definition, used for catalog lookup. */
  public static final String SERVICE_NAME = "AICore";

  /** Qualified name of the {@code resourceGroups} entity. */
  public static final String RESOURCE_GROUPS = "AICore.resourceGroups";

  /** Qualified name of the {@code deployments} entity. */
  public static final String DEPLOYMENTS = "AICore.deployments";

  /** Qualified name of the {@code configurations} entity. */
  public static final String CONFIGURATIONS = "AICore.configurations";
}
