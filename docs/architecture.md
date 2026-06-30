# Implementation Details

## Table of Contents

- [Links](#links)
- [Folder Structure](#folder-structure)
- [Feature](#feature)
  - [CDS Model](#cds-model)
  - [Configuration](#configuration)
  - [Handlers](#handlers)
  - [Services](#services)
  - [Outbox and Polling](#outbox-and-polling)
  - [Exceptions](#exceptions)
- [Extraction Lifecycle](#extraction-lifecycle)
- [Status State Machine](#status-state-machine)
- [Tests](#tests)
  - [Unit Tests](#unit-tests)
- [Quality Tools](#quality-tools)

---

## Links

- [CAP Java Plugin Concept](https://cap.cloud.sap/docs/java/building-plugins#building-plugins)
- [CAP Java Outbox Documentation](https://cap.cloud.sap/docs/java/outbox#outboxing-cap-service-events)
- [SAP Document AI Documentation](https://help.sap.com/docs/document-ai?locale=en-US)
- [Enabling DIE Service on SAP BTP Cloud Foundry](https://help.sap.com/docs/document-ai/sap-document-ai/enabling-service-in-cloud-foundry-environment?locale=en-US)
- [CAP Java Getting Started](https://cap.cloud.sap/docs/java/getting-started)

---

## Folder Structure

| Folder | Description |
|---|---|
| `sap-document-ai` | Core implementation of the Document AI plugin |
| `sap-document-ai/src/main/java` | Java source files for handlers, services, configuration, and model classes |
| `sap-document-ai/src/main/resources/cds` | CDS model files shipped with the plugin |
| `sap-document-ai/src/main/resources/META-INF/services` | Java `ServiceLoader` registration for `CdsRuntimeConfiguration` |
| `sap-document-ai/src/test/java` | Unit tests |
| `bookshop` | Sample CAP Java application demonstrating plugin integration |
| `bookshop/srv` | Spring Boot application module for the sample |
| `bookshop/db` | CDS data model for the sample |
| `bookshop/app` | Fiori UI applications for the sample |
| `docs` | Design and architecture documentation |

---

## Feature

The plugin is implemented in the `sap-document-ai` module. The following Java packages make up the implementation:

| Package | Description |
|---|---|
| `com.sap.cds.feature.documentai.configuration` | Bootstraps all plugin components and registers them with the CDS runtime at startup |
| `com.sap.cds.feature.documentai.handlers` | CDS event handlers for document submission and outbox-driven polling |
| `com.sap.cds.feature.documentai.service` | Core extraction service, processing service, status enum, and transition validator |
| `com.sap.cds.feature.documentai.service.client` | HTTP client abstraction for the DIE REST API |
| `com.sap.cds.feature.documentai.service.model` | Immutable value objects used as internal data transfer types |
| `com.sap.cds.feature.documentai.service.exceptions` | Typed exceptions for error classification |
| `com.sap.cds.feature.documentai.service.utils` | Utility classes |

### CDS Model

The CDS model is defined in:

```
sap-document-ai/src/main/resources/cds/com.sap.cds/sap-document-ai/
```

Per the [CAP Java plugin concept](https://cap.cloud.sap/docs/java/building-plugins#building-plugins), this path makes the model available to consuming applications via the `cds-maven-plugin` `resolve` goal.

The model contains the following files:

| File | Description |
|---|---|
| `document-ai-service.cds` | Defines `DocumentAiService` with the `DocumentExtraction` (inbound) and `DocumentExtractionResult` (outbound) events |
| `extraction-job.cds` | Defines the internal `ExtractionJob` entity used to persist job state across the extraction lifecycle |
| `index.cds` | Entry point that imports both files; resolved by the CAP plugin mechanism |

The `ExtractionJob` entity uses `cuid` (auto-generated UUID primary key) and `managed` (auto-populated audit fields). It tracks the job `status`, `tenantId`, the DIE-assigned `documentAiJobId`, and the raw `extractionResult`. The table is deployed automatically as part of the consuming application's CDS schema deployment — no manual DDL is required.

### Configuration

`DocumentAiServiceConfiguration` implements `CdsRuntimeConfiguration` and is the plugin's sole entry point into the CDS runtime. It is discovered automatically via the Java `ServiceLoader` mechanism.

At startup it:
- Registers `ExtractionServiceImpl` as a named CDS service in the service catalog.
- Resolves the DIE service binding from the environment by the label `sap-document-information-extraction`.
- Constructs an OAuth2-authenticated HTTP destination via the SAP Cloud SDK if a binding is found.
- Wires all resolved dependencies into `ExtractionServiceImpl`.
- Registers `DocumentSubmissionHandler` unconditionally.
- Registers `ExtractionPollingHandler` only when a valid DIE client was successfully built.

If no binding is found or the destination cannot be initialised, the plugin starts in degraded mode — events are accepted and jobs are queued as `PENDING`, but no extraction processing occurs.

### Handlers

| Handler | Description |
|---|---|
| `DocumentSubmissionHandler` | Listens for `DocumentExtraction` events on any `ApplicationService`. Service-name-agnostic by design — consumers emit events from their own service without coupling to the plugin's internal service name. Delegates to `ExtractionService` and completes the event context. |
| `ExtractionPollingHandler` | Registered against the persistent unordered outbox. Polls the DIE service for all active jobs on each invocation. Self-reschedules after the configured interval if jobs remain active. Stops automatically when all jobs reach a terminal status. |

### Services

| Service / Class | Description |
|---|---|
| `ExtractionService` | CAP service interface registered in the service catalog. Exposes `triggerExtraction()` for new submissions and `updateExtractionResult()` for poll-driven status updates. |
| `ExtractionServiceImpl` | Central orchestrator. Creates and persists extraction jobs, coordinates submission via the processing service, schedules polling via the outbox, and enforces the status state machine on every update using optimistic locking. |
| `DocumentAiProcessingService` | Abstraction over the HTTP client. Provides an `isAvailable()` check that allows `ExtractionServiceImpl` to degrade gracefully when no DIE binding is present. |
| `DefaultDocumentAiClient` | Concrete HTTP client. Submits documents to DIE via a multipart `POST` and polls job status via `GET`. All DIE communication is authenticated via SAP Cloud SDK OAuth2 destinations. |
| `StatusTransitionValidator` | Stateless utility that enforces the permitted status transitions. Called before every status update to prevent invalid state machine transitions. |

### Outbox and Polling

The plugin uses the CDS **persistent unordered outbox** for all polling scheduling. This design choice means:

- Polling is entirely **event-driven** — it runs only when there are active jobs.
- No background thread or fixed scheduler is active when the system is idle.
- Resilience across restarts is guaranteed — if the application restarts mid-poll, the outbox re-delivers the pending event automatically.
- Polling stops automatically when all jobs reach a terminal status (`DONE` or `FAILED`) and resumes when the next document is submitted.

The poll interval defaults to 3 seconds and is configurable via `cds.document-ai.polling.interval-seconds` in `application.yaml`.

### Exceptions

Errors from DIE interactions are classified into three typed exceptions nested under `DocumentAiException`:

| Exception | Condition |
|---|---|
| `DocumentAiException.Connectivity` | Network-level failure reaching DIE (timeout, DNS, etc.) |
| `DocumentAiException.Request` | Non-2xx HTTP response from DIE; carries the status code and response body |
| `DocumentAiException.Processing` | Malformed or missing fields in the DIE response |

Two additional exceptions govern internal state management:

| Exception | Condition |
|---|---|
| `ConcurrentJobUpdateException` | Raised when an optimistic lock update detects that a concurrent writer has already advanced the job |
| `IllegalStatusTransitionException` | Raised when a requested status transition is not permitted by the state machine |

---

## Extraction Lifecycle

```
Application
  └─ emit DocumentExtraction(fileName, mimeType, content, options)
       │
       ▼
DocumentSubmissionHandler
  └─ ExtractionService.triggerExtraction()
       │
       ├─ Persist ExtractionJob (status=PENDING)
       │
       ├─ DIE unavailable ──► return PENDING result
       │
       └─ DIE available
            └─ POST multipart document to DIE
                 └─ receive dieJobId
                      └─ update job → SUBMITTED
                           └─ submit poll task to outbox
                                │
                                ▼  (after configured interval, via outbox)
                     ExtractionPollingHandler
                       └─ GET DIE job status for each SUBMITTED / RUNNING job
                            ├─ RUNNING  → update job → RUNNING, reschedule
                            ├─ DONE     → update job → DONE
                            │             emit DocumentExtractionResult
                            │               └─ consumer @On handler invoked
                            └─ FAILED   → update job → FAILED (terminal)
```

---

## Status State Machine

```
PENDING ──► SUBMITTED ──► RUNNING ──► DONE
   │             │            │
   └────────►────┴────────►───┴──────► FAILED
```

| Transition | Trigger |
|---|---|
| `PENDING → SUBMITTED` | Document successfully submitted to DIE |
| `PENDING → FAILED` | Unrecoverable error during submission |
| `SUBMITTED → RUNNING` | DIE reports that the job is in progress |
| `SUBMITTED → DONE` | DIE reports completion without an intermediate RUNNING status |
| `SUBMITTED → FAILED` | DIE reports a processing failure |
| `RUNNING → DONE` | DIE processing completed successfully |
| `RUNNING → FAILED` | DIE reports a processing failure |

`DONE` and `FAILED` are terminal states. No further transitions are permitted from either status.

---

## Tests

### Unit Tests

Unit tests are located in `sap-document-ai/src/test/java`. Each production class has a corresponding test class. The following test classes are implemented:

| Test Class | What is tested |
|---|---|
| `DocumentSubmissionHandlerTest` | Event handler delegation, PENDING and FAILED logging |
| `ExtractionServiceImplTest` | Job creation, submission flow, concurrent update handling, failure marking, outbox scheduling |
| `ExtractionPollingHandlerTest` | Poll cycle logic, DIE status mapping, result emission, self-rescheduling, per-job error isolation |
| `DefaultDocumentAiClientTest` | HTTP submit and poll calls, response parsing, error wrapping for all three exception types |
| `DocumentAiServiceConfigurationTest` | Startup wiring, binding resolution, conditional handler registration |
| `StatusTransitionValidatorTest` | All valid and invalid transitions |
| `ExceptionsTest` | Exception message and cause propagation |

Tests use Mockito for dependencies and AssertJ for assertions. The `jacoco-maven-plugin` enforces a minimum instruction coverage of **85%** across the plugin bundle (generated code excluded).

---

## Quality Tools

| Tool | Definition | Description |
|---|---|---|
| Spotless | `sap-document-ai/pom.xml` | Enforces Google Java Format and SAP license headers on all source files |
| PMD / CPD | `sap-document-ai/pom.xml` | Static analysis and copy-paste detection; SAP Cloud SDK ruleset applied; generated code excluded |
| JaCoCo | `sap-document-ai/pom.xml` | Enforces 85% minimum instruction coverage; generated code excluded |
| Maven Compiler | `sap-document-ai/pom.xml` | Enforces Java 17 (`--release 17`) |
