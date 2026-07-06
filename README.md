[![REUSE status](https://api.reuse.software/badge/github.com/cap-java/cds-ai)](https://api.reuse.software/info/github.com/cap-java/cds-ai)

# SAP Cloud Application Programming Model - AI Plugins for Java

## About this project

This repository contains a collection of AI plugins for [CAP Java](https://cap.cloud.sap/docs/java/) applications, leveraging [SAP AI Core](https://help.sap.com/docs/sap-ai-core), the SAP-RPT-1 foundation model, and SAP Document AI.

### Plugins

| Module                                                                       | Description                                                                                                               |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| [`cds-feature-ai-core`](cds-feature-ai-core/README.md)                       | Bridges CAP Java to SAP AI Core - resource group management, deployment lifecycle, configuration CRUD, and prediction API |
| [`cds-feature-recommendations`](cds-feature-recommendations/README.md)       | AI-powered field recommendations for Fiori UIs in draft-enabled entities                                                  |
| [`cds-feature-sap-document-ai`](cds-feature-sap-document-ai/README.md)       | SAP Document AI integration for asynchronous document information extraction via the DIE service                          |

### Starter

For the simplest setup to get recommendations, add **`cds-starter-ai`** which currently bundles cds-feature-ai-core and cds-feature-recommendations:

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

> Note: [`cds-feature-sap-document-ai`](cds-feature-sap-document-ai/README.md) is not part of `cds-starter-ai` yet - it will be added once the plugin supports multi-tenancy. Add it as an explicit dependency in your application if you need it today.

## Prerequisites

- Java 17+
- CAP Java 4.9+
- Node.js 20+ with `@sap/cds-dk` 9+ (for CDS build tooling)
- An [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding (for `cds-feature-ai-core` and `cds-feature-recommendations`)
- A [SAP Document Information Extraction](https://help.sap.com/docs/document-ai) service binding (for `cds-feature-sap-document-ai`)

Without the respective service binding, each plugin falls back to a mock or degraded mode for local development.

## Samples

A runnable CAP Java bookshop demonstrating all plugins together lives in [`samples/bookshop`](samples/bookshop). It provides an `AdminService` showcasing recommendations on draft-enabled Books, and a `SupplierInvoicesService` showcasing document extraction from supplier invoices. See the sample's own instructions for how to run it.

## Local Development

```bash
mvn clean install     # build all modules
mvn test              # run unit tests
```

For per-plugin details (configuration, programmatic API, multi-tenancy behaviour) see the individual module READMEs. For integration tests against a real AI Core instance see [`integration-tests/`](integration-tests/README.md).

## GitHub Actions

### CI Checks

Every pull request to `main` and every push to `main` runs the following checks (see [`.github/workflows/pipeline.yml`](.github/workflows/pipeline.yml)):

| Job | What it enforces |
|---|---|
| `tests` | Unit tests (`mvn test -P '!with-integration-tests'`) on Java 17 and 21 |
| `integration-tests` | Full integration tests on Java 17 and 21 |
| `local-mtx-tests` | MTX lifecycle tests using a local sidecar (`mvn verify -pl integration-tests/mtx-local/srv`) on Java 17 and 21 |
| `sonarqube-scan` | SonarQube static analysis |
| `codeql` | CodeQL security scanning (`security-extended` queries) on Java/Kotlin and GitHub Actions |
| `blackduck` (main only) | BlackDuck full open-source compliance scan |

Static analysis runs at **compile time on every build** via Maven plugins configured in the root `pom.xml`:

- **Spotless** — Google Java Format + SAP license headers (enforced at `process-sources`)
- **SpotBugs** — `effort=Max` (at `process-test-classes`)
- **PMD + CPD** — SAP Cloud SDK ruleset (at `process-test-classes`)
- **Maven Enforcer** — no duplicate dependency versions, requires Maven 3.6.3+ and Java 17+

### Integration Tests

All integration tests live in [`integration-tests/`](integration-tests/README.md) — see that README for test modules, run commands, and profiles.

### JaCoCo Aggregation

Coverage is aggregated in the `coverage-report/` module using JaCoCo's `report-aggregate` goal. Each feature module runs `prepare-agent` to collect `.exec` data; the `coverage-report` module merges all `.exec` files and generates a single HTML report fed into SonarQube.

Coverage thresholds are enforced by SonarQube in the pipeline, not by JaCoCo check goals.

To generate the report locally:

```bash
mvn clean verify
# Report: coverage-report/target/site/jacoco-aggregate/index.html
```

### Release

Releases are triggered by publishing a GitHub Release with a matching tag. The [`release.yml`](.github/workflows/release.yml) workflow gates on two protected environments (`release-approval` then `release`), verifies the POM `revision` matches the tag, runs BlackDuck, builds without integration tests, and deploys to Maven Central with GPG signing.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/cds-ai/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Security / Disclosure

If you find any bug that may be a security problem, please follow our instructions at [in our security policy](https://github.com/cap-java/cds-ai/security/policy) on how to report it. Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](https://github.com/cap-java/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2026 SAP SE or an SAP affiliate company and cds-ai contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available via the REUSE tool.
