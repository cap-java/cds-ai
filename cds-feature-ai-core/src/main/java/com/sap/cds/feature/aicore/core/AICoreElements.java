/*
 * © 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors.
 */
package com.sap.cds.feature.aicore.core;

/** Element name constants for AICore CDS entities, mirroring the CDS model definitions. */
public final class AICoreElements {

  private AICoreElements() {}

  public static final class Deployment {

    private Deployment() {}

    public static final String ID = "id";
    public static final String DEPLOYMENT_URL = "deploymentUrl";
    public static final String CONFIGURATION_ID = "configurationId";
    public static final String CONFIGURATION_NAME = "configurationName";
    public static final String EXECUTABLE_ID = "executableId";
    public static final String SCENARIO_ID = "scenarioId";
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "statusMessage";
    public static final String TARGET_STATUS = "targetStatus";
    public static final String LAST_OPERATION = "lastOperation";
    public static final String LATEST_RUNNING_CONFIGURATION_ID = "latestRunningConfigurationId";
    public static final String TTL = "ttl";
    public static final String CREATED_AT = "createdAt";
    public static final String MODIFIED_AT = "modifiedAt";
    public static final String SUBMISSION_TIME = "submissionTime";
    public static final String START_TIME = "startTime";
    public static final String COMPLETION_TIME = "completionTime";
    public static final String RESOURCE_GROUP = "resourceGroup";
  }

  public static final class ResourceGroup {

    private ResourceGroup() {}

    public static final String RESOURCE_GROUP_ID = "resourceGroupId";
    public static final String TENANT_ID = "tenantId";
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "statusMessage";
    public static final String CREATED_AT = "createdAt";
    public static final String LABELS = "labels";
  }

  public static final class Configuration {

    private Configuration() {}

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String EXECUTABLE_ID = "executableId";
    public static final String SCENARIO_ID = "scenarioId";
    public static final String CREATED_AT = "createdAt";
    public static final String PARAMETER_BINDINGS = "parameterBindings";
    public static final String INPUT_ARTIFACT_BINDINGS = "inputArtifactBindings";
    public static final String RESOURCE_GROUP = "resourceGroup";
  }

  public static final class Label {

    private Label() {}

    public static final String KEY = "key";
    public static final String VALUE = "value";
  }
}
