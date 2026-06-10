@protocol: 'none'
service AICore {

  @cds.persistence.skip
  entity resourceGroups {
    key resourceGroupId : String;
        tenantId        : String;
        zoneId          : String;
        @readonly
        createdAt       : Timestamp;
        labels          : BckndResourceGroupLabels;
        @assert.range: true
        @readonly
        status          : String enum {
          PROVISIONED;
          ERROR;
          PROVISIONING;
        };
        statusMessage   : String;
        servicePlan     : String;
  };

  @cds.persistence.skip
  entity deployments {
        @assert.format: '^[\w.-]{4,64}$'
    key id                           : String;
        deploymentUrl                : String;
        @mandatory: true
        @assert.format: '^[\w.-]{4,64}$'
        configurationId              : String;
        @assert.format: '^[\w\s.!?,;:\[\](){}<>"''=+*/\\^&%@~$#|-]*$'
        configurationName            : String(256);
        @assert.format: '^[\w.-]{4,64}$'
        executableId                 : String;
        @assert.format: '^[\w.-]{4,64}$'
        scenarioId                   : String;
        @readonly
        status                       : String enum {
          PENDING;
          RUNNING;
          COMPLETED;
          DEAD;
          STOPPING;
          STOPPED;
          UNKNOWN;
        };
        statusMessage                : String(256);
        @assert.range: true
        targetStatus                 : String enum {
          running;
          STOPPED;
          deleted;
        };
        lastOperation                : String;
        @assert.format: '^[\w.-]{4,64}$'
        latestRunningConfigurationId : String;
        @assert.format: '^[0-9]+[m,M,h,H,d,D]$'
        ttl                          : String;
        details                      : AiDeploymentDetails;
        @readonly
        createdAt                    : Timestamp;
        modifiedAt                   : Timestamp;
        submissionTime               : Timestamp;
        startTime                    : Timestamp;
        completionTime               : Timestamp;
        resourceGroup                : Association to one resourceGroups
                                         on 1 = 1;
  } actions {
    action stop();
  };

  @cds.persistence.skip
  entity configurations {
        @mandatory: true
        @assert.format: '^[\w\s.!?,;:\[\](){}<>"''=+*/\\^&%@~$#|-]*$'
        name                  : String(256);
        @mandatory: true
        @assert.format: '^[\w.-]{4,64}$'
        executableId          : String;
        @mandatory: true
        @assert.format: '^[\w.-]{4,64}$'
        scenarioId            : String;
        parameterBindings     : ParameterArgumentBindingList;
        inputArtifactBindings : ArtifactArgumentBindingList;
        @assert.format: '^[\w.-]{4,64}$'
    key id                    : String;
        @readonly
        createdAt             : Timestamp;
        resourceGroup         : Association to one resourceGroups
                                  on 1 = 1;
  };

  type BckndResourceGroupLabels     : many BckndResourceGroupLabel;

  type BckndResourceGroupLabel {
    @mandatory: true
    ![key] : String(63);
    @mandatory: true
    value  : String(5000);
  };

  type AiBackendDetails {};

  type AiScalingDetails {
    backendDetails : AiBackendDetails;
  };

  type AiResourcesDetails {
    backendDetails : AiBackendDetails;
  };

  type AiDeploymentDetails {
    scaling   : AiScalingDetails;
    resources : AiResourcesDetails;
  };

  type ParameterArgumentBinding {
    @mandatory: true
    ![key] : String(256);
    @mandatory: true
    value  : String(5000);
  };

  type ParameterArgumentBindingList : many ParameterArgumentBinding;

  type ArtifactArgumentBinding {
    @mandatory: true
    ![key]     : String(256);
    @mandatory: true
    @assert.format: '^[\w.-]{4,64}$'
    artifactId : String;
  };

  type ArtifactArgumentBindingList  : many ArtifactArgumentBinding;

}
