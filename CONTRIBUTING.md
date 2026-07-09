# Contributing

## Code of Conduct

All members of the project community must abide by the [SAP Open Source Code of Conduct](https://github.com/SAP/.github/blob/main/CODE_OF_CONDUCT.md).
Only by respecting each other we can develop a productive, collaborative community.
Instances of abusive, harassing, or otherwise unacceptable behavior may be reported by [opening an issue](https://github.com/cap-java/cds-ai/issues) or by contacting one of the project maintainers listed in the repository.

## Engaging in Our Project

We use GitHub to manage reviews of pull requests.

- If you are a new contributor, see: [Steps to Contribute](#steps-to-contribute)

- Before implementing your change, create an issue that describes the problem you would like to solve or the code that should be enhanced. Please note that you are willing to work on that issue.

- The team will review the issue and decide whether it should be implemented as a pull request. In that case, they will assign the issue to you. If the team decides against picking up the issue, the team will post a comment with an explanation.

## Steps to Contribute

Should you wish to work on an issue, please claim it first by commenting on the GitHub issue that you want to work on. This is to prevent duplicated efforts from other contributors on the same issue.

If you have questions about one of the issues, please comment on them, and one of the maintainers will clarify.

## Contributing Code or Documentation

You are welcome to contribute code in order to fix a bug or to implement a new feature that is logged as an issue.

The following rule governs code contributions:

- Contributions must be licensed under the [Apache 2.0 License](./LICENSE)
- Due to legal reasons, contributors will be asked to accept a Developer Certificate of Origin (DCO) when they create the first pull request to this project. This happens in an automated fashion during the submission process. SAP uses [the standard DCO text of the Linux Foundation](https://developercertificate.org/).

## CI and Release

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
- **Maven Enforcer** — no duplicate dependency versions, requires Maven 3.6.3+ and Java 21+

Integration tests live in [`integration-tests/`](integration-tests/README.md) — see that README for test modules, run commands, and profiles.

### JaCoCo Coverage

Coverage is aggregated in the `coverage-report/` module using JaCoCo's `report-aggregate` goal. Each feature module runs `prepare-agent` to collect `.exec` data; the `coverage-report` module merges all `.exec` files and generates a single HTML report fed into SonarQube. Coverage thresholds (80% on new code) are enforced by SonarQube in the pipeline.

To generate the report locally:

```bash
mvn clean verify
# Report: coverage-report/target/site/jacoco-aggregate/index.html
```

### Release

Releases are triggered by publishing a GitHub Release with a matching tag. The [`release.yml`](.github/workflows/release.yml) workflow gates on two protected environments (`release-approval` then `release`), verifies the POM `revision` matches the tag, runs BlackDuck, builds without integration tests, and deploys to Maven Central with GPG signing.

## Issues and Planning

- We use GitHub issues to track bugs and enhancement requests.

- Please provide as much context as possible when you open an issue. The information you provide must be comprehensive enough to reproduce that issue for the assignee.