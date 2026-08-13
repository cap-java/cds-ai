# cds-feature-ai-core

Bridges CAP Java applications to [SAP AI Core](https://help.sap.com/docs/sap-ai-core), providing resource group management, deployment lifecycle, configuration CRUD, and a prediction API.

## Features

- **`AICore` CDS Service** - Exposes resource groups, deployments, and configurations as CDS entities with full CRUD support
- **Multi-Tenancy** - Automatic per-tenant resource group creation/deletion on subscribe/unsubscribe
- **Deployment Management** - Auto-creates configurations and deployments for AI Core models with retry and backoff
- **Inference Client Factory** - Provides ready-to-use `ApiClient` instances scoped to a deployment for downstream foundation-model SDKs
- **Mock Fallback** - When no AI Core binding is detected, a mock implementation enables local development

## Setup

### Maven

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-feature-ai-core</artifactId>
    <version>${cds-ai.version}</version>
    <scope>runtime</scope>
</dependency>
```

The plugin auto-registers via Java's `ServiceLoader` mechanism - no code changes required.

### AI Core Binding

In production, bind an SAP AI Core service instance to your application. Supported methods:

- **Service binding** (Cloud Foundry / Kubernetes)
- **Environment variable** `AICORE_SERVICE_KEY` - for local hybrid testing (via `cds bind --exec`)

Without a binding the plugin registers a mock implementation.

## Configuration

All configuration is under the `cds.ai.core` namespace in `application.yaml`:

```yaml
cds:
  ai:
    core:
      resourceGroup: default # Resource group for single-tenant mode
      resourceGroupPrefix: "cds-" # Prefix for auto-created tenant resource groups
      maxRetries: 10 # Max retry attempts for transient AI Core errors
      initialDelayMs: 300 # Initial backoff delay (ms)
```

Multi-tenancy is auto-detected from CAP Java's standard `cds.multiTenancy.sidecar.url` setting
and the presence of a `DeploymentService`. No additional configuration flag is required.

## CDS Service: `AICore`

The plugin registers a CAP service named `AICore` that proxies AI Core REST APIs as CDS entities:

### Entities

| Entity                  | Operations           | Description                                                      |
| ----------------------- | -------------------- | ---------------------------------------------------------------- |
| `AICore.resourceGroups` | READ, CREATE, DELETE | Resource group lifecycle, supports label filtering by `tenantId` |
| `AICore.deployments`    | READ, CREATE, DELETE | Deployment management with status tracking                       |
| `AICore.configurations` | READ, CREATE         | Configuration management for scenarios and executables           |

### Programmatic API

```java
// Get the resource group for the current tenant
ResourceGroupContext rgCtx = ResourceGroupContext.create();
aiCoreService.emit(rgCtx);
String resourceGroup = rgCtx.getResult();

// Get (or auto-create) a deployment ID for a model spec in the given resource group
DeploymentIdContext depCtx = DeploymentIdContext.create();
depCtx.setResourceGroupId(resourceGroup);
depCtx.setSpec(RptModelSpec.rpt1());
aiCoreService.emit(depCtx);
String deploymentId = depCtx.getResult();
```

## Multi-Tenancy

When multi-tenancy is active (detected via `cds.multiTenancy.sidecar.url`):

1. **Subscribe** - Creates resource group `{prefix}{tenantId}` with label `ext.ai.sap.com/CDS_TENANT_ID`
2. **Unsubscribe** - Deletes the tenant's resource group
3. **Isolation** - Each tenant's predictions use their own resource group and deployment

The lifecycle hooks are registered automatically when multi-tenancy is enabled.

## Programmatic Usage

```java
// Obtain the AICore service (registered as a RemoteService by the plugin)
RemoteService aiCoreService = runtime.getServiceCatalog()
    .getService(RemoteService.class, "AICore");

// Use for entity operations (RemoteService extends CqnService)
Result rgs = aiCoreService.run(Select.from("AICore.resourceGroups"));

// Resolve resource group, deployment, and inference client via CDS events
ResourceGroupContext rgCtx = ResourceGroupContext.create();
aiCoreService.emit(rgCtx);
String resourceGroup = rgCtx.getResult();

DeploymentIdContext depCtx = DeploymentIdContext.create();
depCtx.setResourceGroupId(resourceGroup);
depCtx.setSpec(RptModelSpec.rpt1());
aiCoreService.emit(depCtx);
String deploymentId = depCtx.getResult();

InferenceClientContext infCtx = InferenceClientContext.create();
infCtx.setResourceGroupId(resourceGroup);
infCtx.setDeploymentId(deploymentId);
aiCoreService.emit(infCtx);
ApiClient client = infCtx.getResult();
```

The `ApiClient` returned via `InferenceClientContext` is preconfigured with the AI Core
destination and the deployment URL; use it to construct foundation-model SDK
clients (for example `RptInferenceClient` from `cds-feature-recommendations`).

See [`RecommendationConfiguration.java`](../cds-feature-recommendations/src/main/java/com/sap/cds/feature/recommendation/RecommendationConfiguration.java) for a real-world example of this pattern.

## Related

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP AI SDK for Java](https://github.com/SAP/ai-sdk-java)

## Sample

A runnable CAP Java sample demonstrating the AI plugins (which depend on this module) lives at [`samples/bookshop`](../samples/bookshop).
