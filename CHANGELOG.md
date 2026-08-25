# Change Log

- All notable changes to this project are documented in this file.
- The format is based on [Keep a Changelog](https://keepachangelog.com/).
- This project adheres to [Semantic Versioning](https://semver.org/).

## Unreleased

### Added

### Changed

### Fixed

## Version 0.0.2-alpha

### Added

- Working sample application without `cap-js` dependency

### Changed

- Upgraded to CAP Java 5 and Spring Boot 4 (minimum CAP Java version is now 5)

## Version 0.0.1-alpha

Initial release:

- AI Core service integration with automatic resource group management and deployment lifecycle (create, poll, reuse)
- SAP RPT-1 small model deployment and inference via `cds-feature-recommendations`
- `@UI.Recommendations` annotation to expose Fiori-compatible recommendations via OData
- Multi-tenancy support with tenant-scoped AI Core resource groups
- Configurable resource group for single-tenant scenarios (`cds.ai.core.resourceGroup`)
- Retry and backoff logic for transient AI Core errors

