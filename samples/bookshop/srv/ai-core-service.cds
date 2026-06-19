using { AICore } from 'com.sap.cds/ai';

service AICoreShowcaseService @(requires: 'any') {

  // Expose AI Core entities as projections for direct browsing
  entity ResourceGroups as projection on AICore.resourceGroups;
  entity Deployments    as projection on AICore.deployments;
  entity Configurations as projection on AICore.configurations;

  // Resource Group Management
  action   setupTenantResources(tenantId : String)        returns String;
  function getMyResourceGroup()                           returns String;

  // Deployment Lifecycle
  action provisionRpt1(resourceGroupId : String)          returns String;
  action stopDeployment(deploymentId : String, resourceGroupId : String);

  // Configuration Management
  action createConfiguration(
    name : String,
    scenarioId : String,
    executableId : String,
    resourceGroupId : String
  )                                                       returns String;

  // AI Predictions
  action predictCategory(products : array of {
    ID : String; name : String; price : String
  })                                                      returns array of { ID : String; category : String };
}
