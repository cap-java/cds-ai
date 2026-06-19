# Integration Tests

This directory contains integration tests that verify the CAP Java AI plugins against a running Spring Boot application context.

## Test Modules

| Module | Description | Default |
|--------|-------------|---------|
| `spring/` | Core integration tests (AI Core client, recommendations, actions, OData) using H2 | Enabled |
| `mtx-local/` | Multi-tenancy integration tests with a local sidecar and SQLite | Disabled |

## Running Tests

**Default (spring only):**

```bash
mvn verify
```

**Including MTX tests:**

```bash
mvn verify -Pmtx-integration-tests
```

**Skipping all integration tests (source modules only):**

The `with-integration-tests` profile is active by default at the root. Deactivate it to skip both `integration-tests/` and `coverage-report/`:

```bash
mvn install -P-with-integration-tests
```

## Profiles

| Profile | Scope | Effect |
|---------|-------|--------|
| `with-integration-tests` | Root | **Active by default**; includes `integration-tests/` and `coverage-report/`. Deactivate with `-P-with-integration-tests`. |
| `mtx-integration-tests` | `integration-tests/` | Also includes the `mtx-local/srv` module |

## Coverage

Aggregated code coverage is produced by the `coverage-report/` module at the project root.

### How it works

1. Each module that runs tests has the JaCoCo agent attached (`prepare-agent`), which writes a `target/jacoco.exec` file during test execution.
2. The `coverage-report` module (built last in the reactor) merges all `.exec` files into a single `target/jacoco-merged.exec`.
3. It then generates an aggregated HTML/XML report via `jacoco:report-aggregate` and runs `jacoco:check` against configurable thresholds.

### Generating the report

```bash
mvn clean verify
```

The aggregated report is at:

```
coverage-report/target/site/jacoco-aggregate/index.html
```

### Thresholds

Per-module thresholds are defined in `coverage-report/pom.xml`:

| Module | Instruction | Branch | Complexity |
|--------|-------------|--------|------------|
| `cds-feature-ai-core` | 0% | 0% | 0% |
| `cds-feature-recommendations` | 0% | 0% | 0% |

Thresholds are intentionally permissive while the modules are under active development; tighten them in `coverage-report/pom.xml` once the API surfaces stabilise.

### Coverage data sources

The merged report combines execution data from:

- `cds-feature-ai-core/target/jacoco.exec` (unit tests)
- `cds-feature-recommendations/target/jacoco.exec` (unit tests)
- `integration-tests/spring/target/jacoco.exec` (integration tests)
- `integration-tests/mtx-local/srv/target/jacoco.exec` (MTX integration tests, only when profile is active)
