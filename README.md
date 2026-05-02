# SAP Cloud Application Programming Model, AI plugin for Java

This is the Java version of the [SAP CAP AI plugin for Node.js](https://github.com/cap-js/ai).

## About this project

The SAP Cloud Application Programming Model, AI plugin for Java provides AI-powered UI recommendations for CAP Java applications, leveraging SAP AI Core and the SAP-RPT-1 model.

> [!IMPORTANT]
> In multi-tenancy scenarios with a sidecar, the plugin must be included in the sidecar for SAP AI Core handling.

### 1. Use case: Recommendations

Recommendations are implemented leveraging SAP-RPT-1 and AI Core. This plugin generically hooks into any entity which has properties with a value help (detected via `@Common.ValueList` on the property or `@cds.odata.valuelist` on the association target).

```cds 
entity Books {
  key ID : Integer;
  title  : String(111);
  descr  : String(1111);
  genre : Association to one Genres;
  status : Association to one Status;
}
annotate Genres with @cds.odata.valuelist;
annotate Books with {
    status @Common.ValueList : {
        CollectionPath : 'Status',
        Parameters: [
            {
                $Type: 'Common.ValueListParameterInOut'
                ValueListProperty : 'code',
                LocalDataProperty : status_code
            }
        ]
    }
}
```

The annotated fields automatically receive AI-powered recommendations in Fiori draft edit mode. The handler fetches existing rows from the database as training context, calls the RPT-1 model, and writes the predictions into the `SAP_Recommendations` property on the result.

If you do not want recommendations for a specific field, annotate it with `@UI.RecommendationState: 0`.

```cds
annotate Books with {
    genre @UI.RecommendationState : 0;
}
```

### 2. Use case: Simplified AI Core usage

The plugin introduces an `AICore` CAP service that automatically performs some administrative tasks and offers simplified access to AI Core.

#### Automatic operations

- The plugin automatically creates a new SAP AI Core resource group per tenant during tenant onboarding and deletes it during offboarding.
- The plugin automatically creates an RPT-1 deployment per resource group for the recommendations feature.

## Requirements and Setup

### Prerequisites

- Java 17+
- Maven 3.6.3+
- Node.js 20+ (for CDS build tooling)
- `@sap/cds-dk` 9+ (CDS build tooling)
- An [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding (for production)

### Dependencies to add

pom.xml:
```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-feature-ai</artifactId>
    <version>${revision}</version>
</dependency>
```

package.json:
```json
"dependencies": {
    "@cap-js/ai": "1.0.0"
}
```

### AI Core service binding

To use the plugin in production scenarios you need an [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding.
The plugin will automatically create resource groups per tenant labeled with `ext.ai.sap.com/CDS_TENANT_ID` in multi-tenancy scenarios and create an RPT-1 deployment in each for the recommendations feature. For multitenancy, set `cds.multitenancy.enabled=true` (or the environment variable `CDS_MULTITENANCY_ENABLED=true`). 

In single-tenant setups the plugin uses the 'default' resource group and creates an RPT-1 deployment as well if none exists.

For single-tenant deployments you can change the resource group as follow in the `application.yaml`:

```yaml
# application.yaml
cds:
  requires:
    AICore:
      resourceGroup: CUSTOM_RESOURCE_GROUP
```

For Cloud Foundry apps an example config could look like in [samples/bookshop/mta.yaml](samples/bookshop/mta.yaml).

For local development without an AI Core binding, the plugin falls back to a `MockAIClient` that returns random predictions from the existing context rows.


## Test the plugin locally

In `samples/bookshop` you can find a complete CAP Java bookshop that demonstrates the plugin:

```bash
mvn clean install
cd samples/bookshop
mvn spring-boot:run
```

### Local Testing
To execute local tests, simply run:

```bash
mvn test
```

or for a full build including tests:

```bash
mvn clean install
```

To run the integration test [AICoreSetupHandlerTest](https://github.com/cap-java/cds-feature-ai/blob/main/srv/src/test/java/com/sap/cds/feature/ai/client/setup/AICoreSetupHandlerTest.java), you need a [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding.
Then, first  run `cds bind <local-name> -to <name-of-the-binding-on-btp>` in order to make the service binding available locally. This command will use your currently targeted Cloud Foundry space, for more info consult the cds bind documentation at https://cap.cloud.sap/docs/tools/cds-bind.

Then execute the test with `cds bind --exec mvn test`.

If there is no binding, the integration test is skipped automatically.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/cds-feature-ai/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Security / Disclosure

If you find any bug that may be a security problem, please follow our instructions at [in our security policy](https://github.com/cap-java/cds-feature-ai/security/policy) on how to report it. Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](https://github.com/cap-java/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2026 SAP SE or an SAP affiliate company and cds-feature-ai contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available via the REUSE tool.

