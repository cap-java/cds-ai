# SAP Document AI Plugin for SAP Cloud Application Programming Model (CAP) (Alpha Version)

A CAP Java plugin that integrates [SAP Document AI](https://help.sap.com/docs/document-ai?locale=en-US) into CDS applications. The plugin exposes a CDS event-based API for submitting documents, manages asynchronous polling against the Document AI service, and delivers results via a CDS outbound event - backed by the CDS persistent outbox for resilience across restarts.

## Table of Contents

- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Integration Guide](#integration-guide)
- [Usage](#usage)
  - [CDS Model](#cds-model)
- [Multi-Tenancy](#multi-tenancy)
- [Bookshop Sample](#bookshop-sample)
  - [Running without a Document AI service binding](#running-without-a-die-service-binding)
  - [Running with a Document AI service binding (hybrid mode)](#running-with-a-die-service-binding-hybrid-mode)
- [Configuration](#configuration)
  - [Document AI Service Binding](#die-service-binding)
  - [Outbox](#outbox)
  - [Degraded Operation](#degraded-operation)
- [Architecture Overview](docs/architecture.md)
- [Supported Plans and APIs](#supported-plans-and-apis)
- [Known Limitations](#known-limitations)
- [Monitoring and Logging](#monitoring-and-logging)
- [References](#references)
- [Support, Feedback, Contributing](#support-feedback-contributing)
- [Integration Tests](#integration-tests)

---

## Quick Start

1. Add the `sap-document-ai` Maven dependency to your application's `pom.xml`.
2. Enable the CDS persistent outbox scheduler in `application.yaml`.
3. Emit a `DocumentExtraction` event from any `ApplicationService`.
4. Implement a `DocumentExtractionResult` event handler class in your application to process the extracted data.

For a working reference, see the [Bookshop Sample](#bookshop-sample), which demonstrates a complete integration using an in-memory database.

---

## Prerequisites

| Requirement     | Minimum version                                                               |
| --------------- | ----------------------------------------------------------------------------- |
| Java            | 17+                                                                           |
| Maven           | 3.9+                                                                          |
| CAP Java        | 4.9.x (LTS)                                                                   |
| SAP Cloud SDK   | 5.28.0+                                                                       |
| Node.js         | Required only for the build-time `cds` CLI (`@sap/cds-dk`)                    |
| SAP BTP service | Document AI service instance with label `sap-document-information-extraction` |

All plugin dependencies are declared with `provided` scope and are available on the classpath of any standard CAP Spring Boot application.

---

## Repository Structure

| Module                        | What it is                                                           |
| ----------------------------- | -------------------------------------------------------------------- |
| `cds-feature-sap-document-ai` | The plugin itself - handlers, services, CDS models, HTTP client      |
| `samples/bookshop`            | A working reference app showing the plugin integrated end-to-end     |
| `integration-tests/spring`    | Spring Boot integration tests using a stub Document AI client and H2 |

---

## Integration Guide

This section walks through integrating the plugin into an existing CAP Java application from start to finish.

### Step 1 - Add the dependency

Declare the plugin in `srv/pom.xml`:

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>sap-document-ai</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Ensure the `cds-maven-plugin` is configured with the `resolve` goal so the plugin's CDS models are pulled into the build:

```xml
<plugin>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-maven-plugin</artifactId>
    <version>${cds.services.version}</version>
    <executions>
        <execution>
            <id>cds.resolve</id>
            <goals>
                <goal>resolve</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Step 2 - Enable the persistent outbox

Add the following to `src/main/resources/application.yaml`:

```yaml
cds:
  outbox:
    persistent:
      scheduler:
        enabled: true
```

Without this, documents will be submitted to Document AI but results will never be retrieved.

### Step 3 - Bind the Document AI service

**On SAP BTP (Cloud Foundry):** Bind your application to a Document AI service instance. The plugin discovers the binding at startup and activates extraction processing automatically.

**For local development**, use the `cds bind` hybrid profile to forward credentials from a CF-hosted service instance:

```bash
cf login
cds bind --to <your-die-instance-name>
```

This creates a `[hybrid]` profile entry in `.cdsrc-private.json`. Do not commit this file - it contains environment-specific binding references. Then run the application with the hybrid profile:

```bash
cds bind --exec mvn spring-boot:run
```

Without a binding, the plugin starts in degraded mode - extraction events are accepted and jobs are created in `PENDING` status, but no actual processing occurs. See [Degraded Operation](#degraded-operation) for details.

### Step 4 - Emit a DocumentExtraction event

From any event handler or service method in your application, emit a `DocumentExtraction` event:

```java
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtraction;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionContext;

DocumentExtraction payload = DocumentExtraction.create();
payload.setFileName("invoice.pdf");
payload.setMimeType("application/pdf");
payload.setContent(inputStream);
payload.setOptions("{\"schemaId\": \"my-schema-id\"}");

DocumentExtractionContext ctx = DocumentExtractionContext.create();
ctx.setData(payload);
myApplicationService.emit(ctx);
```

The call returns immediately. The plugin handles submission and schedules polling asynchronously.

### Step 5 - Handle the result

Implement an event handler in your application to receive the extraction output once the Document AI service reports the job as complete:

```java
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResult;
import com.sap.cds.feature.documentai.generated.cds4j.sap.document.ai.documentaiservice.DocumentExtractionResultContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

@ServiceName(value = "*", type = ApplicationService.class)
public class MyExtractionResultHandler implements EventHandler {

    @On(event = DocumentExtractionResultContext.CDS_NAME)
    public void onExtractionComplete(DocumentExtractionResultContext context) {
        DocumentExtractionResult result = context.getData();
        String jobId      = result.getJobId();
        String resultJson = result.getExtractionResult();
        // process the extracted data
        context.setCompleted();
    }
}
```

### Step 6 - Build and run

```bash
mvn compile
mvn spring-boot:run
```

Submit a document via your application. The plugin logs progress at `INFO` level - look for `[sap-document-ai]` prefixed entries to trace the job from submission through to result delivery. See [Monitoring and Logging](#monitoring-and-logging) for how to enable debug-level output.

---

## Usage

> **Note:** In the current version, document extraction can only be triggered programmatically via event emission, as shown in the [Integration Guide](#integration-guide). Annotation-based triggering (e.g. declaratively marking an entity field or action to trigger extraction) is not yet supported and is planned for a future release.

## Multi-Tenancy

Multi-tenancy is not implemented in the current version and is planned for a future release. The `tenantId` field is stored on the `ExtractionJob` entity as groundwork.

### CDS Model

The plugin registers its CDS models automatically via the CAP plugin mechanism. No `using` declarations are required in the application model.

The plugin exposes the service `sap.document.ai.DocumentAiService` with two events:

| Event                      | Direction                            | Description                                    |
| -------------------------- | ------------------------------------ | ---------------------------------------------- |
| `DocumentExtraction`       | Inbound - emitted by the application | Triggers document extraction                   |
| `DocumentExtractionResult` | Outbound - emitted by the plugin     | Delivers the extraction result upon completion |

**`DocumentExtraction` payload:**

| Field      | Type          | Description                                              |
| ---------- | ------------- | -------------------------------------------------------- |
| `fileName` | `String`      | File name forwarded to the Document AI service           |
| `mimeType` | `String`      | MIME type of the document (e.g. `application/pdf`)       |
| `content`  | `LargeBinary` | Document byte stream                                     |
| `options`  | `LargeString` | JSON options string passed to Document AI; may be `null` |

The `options` field maps directly to the Document AI API's `options` body parameter. Refer to the [SAP Document AI's API documentation](https://help.sap.com/docs/document-ai/sap-document-ai/upload-document?locale=en-US&q=submit+document) for the full options schema.

**`DocumentExtractionResult` payload:**

| Field              | Type          | Description                                        |
| ------------------ | ------------- | -------------------------------------------------- |
| `jobId`            | `String`      | Plugin-internal extraction job identifier          |
| `documentAiJobId`  | `String`      | Job identifier assigned by the Document AI service |
| `extractionResult` | `LargeString` | Raw JSON extraction result returned by Document AI |

---

## Bookshop Sample

A runnable CAP Java bookshop demonstrating this plugin lives at [`samples/bookshop`](../samples/bookshop). The `SupplierInvoicesService` showcases a realistic document extraction flow: upload a supplier PDF invoice, extract vendor, date, total and line items, and populate the invoice entity.

**Prerequisites:** Java 17, Maven 3.9+, Node.js (required by the `cds` CLI invoked during the Maven build).

### Running without a Document AI service binding

The sample can be started locally without any service binding. Extraction jobs will be created in `PENDING` status and no actual processing will occur, but the full application and UI are functional for integration exploration.

```bash
mvn -f samples/bookshop/pom.xml clean install
cd samples/bookshop/srv
mvn spring-boot:run
```

### Running with a Document AI service binding (hybrid mode)

To run the sample with a real Document AI service instance, the SAP BTP Cloud Foundry environment is used via the `cds bind` hybrid profile.

**Prerequisites:** The `@sap/cds-dk` CLI installed, and CF CLI logged in to the org and space where the Document AI service instance is provisioned.

**Step 1 - Log in to Cloud Foundry:**

```bash
cf login
```

**Step 2 - Bind the Document AI service instance:**

```bash
cd samples/bookshop
cds bind --to <<instance-name>>
```

This creates or updates `.cdsrc-private.json` with a `[hybrid]` profile entry pointing to the CF service instance and its service key. The file should not be committed to version control as it contains environment-specific binding references.

**Step 3 - Compile and run with the hybrid profile:**

```bash
cd samples/bookshop
mvn compile
cds bind --exec mvn spring-boot:run
```

The plugin will resolve the Document AI service binding at startup, construct an OAuth2-authenticated destination, and activate extraction processing.

The `AdminService` exposes a `Books` entity with a bound action `extractDocumentData()` illustrating how to trigger extraction from a CAP action. The `Attachments` composition on `Books` provides a Fiori UI for file upload and is used here purely as a convenient way to supply documents in the sample. The CAP Attachments plugin is not a dependency of this plugin - document storage and retrieval are outside the scope of `cds-feature-sap-document-ai`, which is concerned solely with submitting documents to SAP Document AI and delivering the extracted results.

A sample PDF invoice (`dummy invoice.pdf`) is included in the `samples/bookshop/` directory. You can upload it via the Fiori UI and trigger `extractDocumentData()` to see the full extraction flow end-to-end without needing your own test document.

---

## How It Works

The plugin follows a fire-and-forget event model with asynchronous result delivery:

1. **Application emits** a `DocumentExtraction` event containing the document bytes and extraction options.
2. **Plugin submits** the document to the Document AI REST API and persists an `ExtractionJob` in `SUBMITTED` status.
3. **Plugin polls** the Document AI service via the CDS persistent outbox until the job reaches a terminal status.
4. **Plugin emits** a `DocumentExtractionResult` event containing the raw extraction JSON once the job completes.
5. **Application handles** the result event and processes the extracted fields.

### Job Status Flow

```
PENDING --> SUBMITTED --> RUNNING --> DONE
   |             |            |
   +------------>+----------->+-------> FAILED
```

- `PENDING` - job created, awaiting submission (Document AI may be unavailable)
- `SUBMITTED` - document accepted by Document AI
- `RUNNING` - Document AI is processing the document
- `DONE` - extraction completed successfully; result delivered
- `FAILED` - unrecoverable error at any stage

See [Architecture Overview](docs/architecture.md) for the full component breakdown and lifecycle diagram.

---

## Configuration

### Document AI Service Binding

The plugin resolves Document AI credentials from the SAP BTP service binding environment at startup. It searches for a binding with the service label `sap-document-information-extraction`.

**SAP BTP (Cloud Foundry / Kubernetes):** Bind the application to a Document AI service instance by referring to [Cloud Foundry](https://help.sap.com/docs/document-ai/sap-document-ai/enabling-service-in-cloud-foundry-environment?locale=en-US&q=submit+document) or [Kuberenetes](https://help.sap.com/docs/document-ai/sap-document-ai/enabling-service-in-kyma-environment?locale=en-US&q=submit+document) documentation. The plugin discovers the binding, constructs an OAuth2-authenticated HTTP destination via the SAP Cloud SDK, and activates extraction processing.

**Local development:** The plugin starts in degraded mode when no binding is present (see [Degraded Operation](#degraded-operation)). A local binding can be simulated via `VCAP_SERVICES` or a service binding file bearing the label `sap-document-information-extraction`.

If the binding is present but the destination cannot be initialised (for example, due to a network or configuration error), the plugin logs a warning and disables extraction until the application is restarted.

### Outbox

The plugin relies on the CDS persistent outbox to schedule polling cycles. The following configuration is required in `application.yaml`:

```yaml
cds:
  outbox:
    persistent:
      scheduler:
        enabled: true
```

Without the persistent outbox, documents are submitted to Document AI but results are never retrieved.

The plugin submits a polling task named `document-ai-poll-extraction-jobs` to the outbox at 3-second intervals (default) while active jobs exist. Polling stops automatically once all jobs reach a terminal status (`DONE` or `FAILED`) and resumes upon the next document submission.

The poll interval can be configured in `application.yaml`:

```yaml
cds:
  document-ai:
    polling:
      interval-seconds: 3 # default
```

The outbox retry limit can be adjusted alongside other outbox services:

```yaml
cds:
  outbox:
    services:
      DefaultOutboxUnordered:
        maxAttempts: 10
```

### Degraded Operation

The plugin is designed to accept events and preserve job state even when dependent services are unavailable.

| Condition                                                        | Behaviour                                                                                                  |
| ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| No Document AI service binding found at startup                  | `DocumentExtraction` events are accepted; jobs are created with status `PENDING`; polling is not scheduled |
| Document AI binding present but destination initialisation fails | Same as above; a warning is logged                                                                         |
| Persistent outbox not configured                                 | Documents are submitted to Document AI; the polling task is not persisted and results are not delivered    |
| Document AI returns a non-2xx HTTP response                      | The affected job is marked `FAILED`; an error is logged                                                    |
| Concurrent status update detected                                | The update is skipped; the later writer's state is preserved (optimistic locking)                          |

---

## Architecture Overview

For a detailed description of the plugin's design, component responsibilities, extraction lifecycle, and status state machine, see [here](docs/architecture.md).

---

## Supported Plans and APIs

The plugin communicates with the SAP Document Information Extraction service via its **REST API** (`document-information-extraction/v1`). This is supported across all available Document AI service plans.

| Document AI Service Plan | Supported          |
| ------------------------ | ------------------ |
| All plans                | Yes - via REST API |

**Future:** Support for the Document AI **OData API** is planned for a future release. This would enable richer query capabilities over extraction results directly through the CAP OData layer.

---

## Known Limitations

- **Multi-tenancy** — not implemented; all jobs run in a single-tenant context. Planned for a future release.
- **Annotation-based triggering** — document extraction can only be initiated programmatically via event emission; declarative triggering is not yet supported.

---

## Monitoring and Logging

All plugin log statements are prefixed with `[sap-document-ai]` to facilitate log filtering. The plugin uses SLF4J and is configured through the standard logging framework of the host application.

| Level   | Logged events                                                                                                        |
| ------- | -------------------------------------------------------------------------------------------------------------------- |
| `INFO`  | Service binding resolution, job creation, status transitions, result emission                                        |
| `WARN`  | Missing binding, unavailable outbox, jobs skipped due to missing Document AI job ID, concurrent update conflicts     |
| `ERROR` | Submission failures, non-2xx Document AI responses, polling exceptions                                               |
| `DEBUG` | Per-cycle active job counts, Document AI status poll responses, idempotent update skips, poll schedule confirmations |

To enable debug-level logging for the plugin, add the following to `application.yaml`:

```yaml
logging:
  level:
    com.sap.cds.feature.documentai: DEBUG
```

---

## References

- [Getting Started with CAP](https://cap.cloud.sap/docs/get-started/)
- [CAP Java](https://cap.cloud.sap/docs/java/)
- [Service Consumption using Service Bindings](https://cap.cloud.sap/docs/java/cqn-services/remote-services#native-consumption)
- [Outbox](https://cap.cloud.sap/docs/java/outbox#concepts)
  - [Technical Outbox API](https://cap.cloud.sap/docs/java/outbox#technical-outbox-api)
- [SAP Document AI Docs](https://help.sap.com/docs/document-ai?locale=en-US)
- [Enabling Document AI Service Instance on SAP BTP Cloud Foundry](https://help.sap.com/docs/document-ai/sap-document-ai/enabling-service-in-cloud-foundry-environment?locale=en-US)

---

## Support, Feedback, Contributing

- Bug reports and feature requests should be submitted as issues in this project repository.
- Pull requests are welcome. All contributions must pass `mvn verify`, which enforces Spotless code formatting (Google Java Format), PMD static analysis, and a minimum JaCoCo instruction coverage of 85%.

---

## Integration Tests

Spring Boot tests are implemented in the `integration-tests/` folder. The tests are executed during the build of the project in the GitHub Actions.

The folder contains a simple Spring Boot application backed by an in-memory H2 database. No Document AI service binding is required - the tests use a stub `DocumentAiClient` that returns controlled responses.

The following scenarios are covered:

- Plugin startup - service catalog registration and schema initialisation
- Document submission via the CAP event API
- Full extraction lifecycle (PENDING → SUBMITTED → RUNNING → DONE and FAILED paths)
- Parallel document processing in a single poll cycle
- Poll cycle resilience when one job's Document AI call fails
- Graceful degradation when no Document AI binding is present
- Rejection of invalid state machine transitions
- `DocumentExtractionResult` CAP event emission on job completion

To run the tests locally, first install the plugin snapshot, then run `mvn verify` from the `integration-tests/` folder:

```bash
cd sap-document-ai && mvn install -DskipTests
cd ../integration-tests && npm install && mvn verify
```
