# SAP Cloud Application Programming Model - AI Plugins for Java

## About this project

This repository contains a collection of AI plugins for [CAP Java](https://cap.cloud.sap/docs/java/) applications, leveraging [SAP AI Core](https://help.sap.com/docs/sap-ai-core) and the SAP-RPT-1 foundation model.

### Plugins

| Module                                                                 | Description                                                                                                               |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| [`cds-feature-ai-core`](cds-feature-ai-core/README.md)                 | Bridges CAP Java to SAP AI Core - resource group management, deployment lifecycle, configuration CRUD, and prediction API |
| [`cds-feature-recommendations`](cds-feature-recommendations/README.md) | AI-powered field recommendations for Fiori UIs in draft-enabled entities                                                  |

### Starter

For the simplest setup, add the **`cds-starter-ai`** dependency which bundles both plugins:

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-starter-ai</artifactId>
    <version>${cds-ai.version}</version>
</dependency>
```

```json
"dependencies": {
    "@cap-js/ai": "^1"
}
```

## Prerequisites

- Java 17+
- CAP Java 4.9+
- Node.js 20+ with `@sap/cds-dk` 9+ (for CDS build tooling)
- An [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding (for production use)

Without an AI Core binding the plugins fall back to mock implementations for local development.

## Samples

In [`samples/bookshop`](samples/bookshop) you can find a complete CAP Java bookshop demonstrating both plugins:

```bash
mvn clean install
cd samples/bookshop
mvn spring-boot:run
```

## Local Development

```bash
mvn clean install     # build all modules
mvn test              # run unit tests
```

For integration tests against a real AI Core instance:

```bash
cds bind ai-core -2 <your-ai-core-service-instance>
cds bind --exec mvn test -pl integration-tests/spring -am
```

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/cds-ai/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Security / Disclosure

If you find any bug that may be a security problem, please follow our instructions at [in our security policy](https://github.com/cap-java/cds-ai/security/policy) on how to report it. Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](https://github.com/cap-java/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2026 SAP SE or an SAP affiliate company and cds-ai contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available via the REUSE tool.
