# cds-feature-ai-core

Bridges CAP Java applications to [SAP AI Core](https://help.sap.com/docs/sap-ai-core), providing resource group management, deployment lifecycle, configuration CRUD, and a prediction API.

## Features

- **`AICore` CDS Service** - Internal CDS service (annotated `@protocol: 'none'`) modelling resource groups, deployments, and configurations as CDS entities. The plugin does **not** expose this service via OData; it is consumed in-process via `RemoteService`. To expose it externally, project it from your own service or use the `@cap-js/ai` model.
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

In production, bind an SAP AI Core service instance to your application via a standard service binding (Cloud Foundry / Kubernetes). For local hybrid testing against a real AI Core instance, use the CAP CLI:

```bash
cds bind ai-core -2 <your-ai-core-service-instance>
cds bind --exec mvn spring-boot:run
```

Without a binding the plugin registers a mock implementation suitable for local development.

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

The plugin registers a CAP service named `AICore` that proxies AI Core REST APIs as CDS entities.
The service is internal (`@protocol: 'none'`); use a `RemoteService` lookup to interact with it.

### Entities

| Entity                  | Operations                   | Description                                                      |
| ----------------------- | ---------------------------- | ---------------------------------------------------------------- |
| `AICore.resourceGroups` | READ, CREATE, UPDATE, DELETE | Resource group lifecycle, supports label filtering by `tenantId` |
| `AICore.deployments`    | READ, CREATE, UPDATE, DELETE | Deployment management with status tracking; bound action `stop`  |
| `AICore.configurations` | READ, CREATE                 | Configuration management for scenarios and executables           |

## Programmatic API

The plugin exposes its functionality through three event contexts emitted on the `AICore` `RemoteService`. This pattern decouples callers from the implementation and makes it easy to override individual steps in tests.

```java
import com.sap.cds.feature.aicore.api.DeploymentIdContext;
import com.sap.cds.feature.aicore.api.InferenceClientContext;
import com.sap.cds.feature.aicore.api.ResourceGroupContext;
import com.sap.cds.feature.aicore.generated.cds4j.aicore.AICore_;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.feature.recommendation.api.RptModelSpec;

// 1. Obtain the AICore service as a RemoteService
RemoteService aiCore = runtime.getServiceCatalog()
    .getService(RemoteService.class, AICore_.CDS_NAME);

// 2. Resolve the resource group for the current tenant
//    (auto-creates the group on first use in multi-tenant mode)
ResourceGroupContext rgCtx = ResourceGroupContext.create();
aiCore.emit(rgCtx);
String resourceGroupId = rgCtx.getResult();

// 3. Resolve (or create) a deployment for a given model spec
DeploymentIdContext depCtx = DeploymentIdContext.create();
depCtx.setResourceGroupId(resourceGroupId);
depCtx.setSpec(RptModelSpec.rpt1()); // or your own ModelDeploymentSpec
aiCore.emit(depCtx);
String deploymentId = depCtx.getResult();

// 4. Obtain a configured ApiClient for the deployment
InferenceClientContext infCtx = InferenceClientContext.create();
infCtx.setResourceGroupId(resourceGroupId);
infCtx.setDeploymentId(deploymentId);
aiCore.emit(infCtx);
ApiClient client = infCtx.getResult();
```

The `ApiClient` returned by `InferenceClientContext` is preconfigured with the AI Core
destination and the deployment URL; use it to construct foundation-model SDK
clients (for example `RptInferenceClient` from `cds-feature-recommendations`).

Because `RemoteService` extends `CqnService`, you can also run CDS queries against
the entities directly:

```java
Result rgs = aiCore.run(Select.from(AICore_.CDS_NAME + ".resourceGroups"));
```

### Public API

The stable public API of this plugin lives in the `com.sap.cds.feature.aicore.api` package.
Implementation classes in sibling packages may change without notice.

| Type                                                   | Purpose                                                                                                                                |
| ------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| [`ResourceGroupContext`](src/main/java/com/sap/cds/feature/aicore/api/ResourceGroupContext.java)   | Event context for `resourceGroup` - resolves (and auto-creates in MTX mode) the AI Core resource group for the current/explicit tenant |
| [`DeploymentIdContext`](src/main/java/com/sap/cds/feature/aicore/api/DeploymentIdContext.java)     | Event context for `deploymentId` - resolves or creates a deployment matching a `ModelDeploymentSpec` inside a resource group           |
| [`InferenceClientContext`](src/main/java/com/sap/cds/feature/aicore/api/InferenceClientContext.java) | Event context for `inferenceClient` - returns an `ApiClient` preconfigured with the inference destination for a given deployment      |
| [`ModelDeploymentSpec`](src/main/java/com/sap/cds/feature/aicore/api/ModelDeploymentSpec.java)     | Record describing a target deployment (scenario, executable, configuration name, parameter bindings, match predicate)                  |

## Multi-Tenancy

When multi-tenancy is active (detected via `cds.multiTenancy.sidecar.url`):

1. **Subscribe** - Creates resource group `{prefix}{tenantId}` with label `ext.ai.sap.com/CDS_TENANT_ID`
2. **Unsubscribe** - Deletes the tenant's resource group
3. **Isolation** - Each tenant's predictions use their own resource group and deployment

The lifecycle hooks are registered automatically when multi-tenancy is enabled.

## Related

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP AI SDK for Java](https://github.com/SAP/ai-sdk-java)
