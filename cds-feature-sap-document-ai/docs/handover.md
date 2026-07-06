# Handover Document - SAP Document AI CAP Java Plugin

This document captures what has been built, where things stand today, and directions for where the project should go next - based on my understanding (Samyuktha Prabhu [samyuktha.prabhu@sap.com]) at the time of handover.

## Table of Contents

- [Getting Started](#getting-started)
- [What Is Built](#what-is-built)
- [Current Capabilities & Limitations](#current-capabilities--limitations)
- [Implementation Notes](#implementation-notes)
- [Suggested Future Work](#suggested-future-work)

---

## Getting Started

### Read the docs in this order

1. **[README.md](../README.md)** - start here. Covers what the plugin does, how to integrate it into a CAP Java application, configuration options, and how to run the bookshop sample locally.
2. **[architecture.md](architecture.md)** - read this second. Covers the component breakdown, the extraction lifecycle diagram, and the job status state machine. Gives you the internal picture once the README has explained the external API.
3. **This document (handover.md)** - read last. Covers current maturity, known limitations, implementation notes, and suggestions for future work. Most relevant if you are continuing development rather than just consuming the plugin.

---

## What Is Built

The plugin is a CAP Java plugin that lets any CAP Spring Boot application send documents to SAP Document AI service on BTP for information extraction and receive the results back - all without writing any HTTP, polling, or job management code.

### Current Maturity: Alpha / MVP

| Implemented                                                         | Not yet implemented         |
| ------------------------------------------------------------------- | --------------------------- |
| Core asynchronous extraction workflow                               | Multitenancy                |
| Event-based API (`DocumentExtraction` / `DocumentExtractionResult`) | Annotation-based triggering |
| Persistent outbox polling with configurable interval                | Automatic field mapping     |
| Unit and integration tests (85% coverage enforced)                  | Job recovery on restart     |
| Working reference app (`bookshop`)                                  | Richer local mock mode      |

---

## Current Capabilities & Limitations

This is an alpha release. The core extraction pipeline works end-to-end, and the foundation has been laid for a number of possible extensions. The table below summarises what is and isn't in scope today. See [Suggested Future Work](#suggested-future-work) for ideas on where things could go next.

| #   | Area                   | Where things stand                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| --- | ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S1  | **Multitenancy**       | `tenantId` is stored on `ExtractionJob` as groundwork, but polling and the HTTP client are not yet tenant-aware. Single-tenant use only for now.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| S2  | **Triggering**         | Programmatic triggering (emit `DocumentExtraction` from code) works fully. Declarative triggering via a CDS annotation + Fiori Elements button is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| S3  | **Document storage**   | The plugin is focused on extraction only. Applications are responsible for storing documents using their preferred mechanism. <br/> **Design Decision**: The plugin accepts document bytes directly on the `DocumentExtraction` event and has no knowledge of where those bytes came from. The plugin's job is extraction, not storage. A hard dependency on `@cap-java/cds-feature-attachments` would couple two independent plugins and create version compatibility overhead. Keeping the plugin storage-agnostic means it works with any document source - the Attachments plugin, a custom entity, an external store, or a direct upload. Each consuming application can choose its own storage strategy and feed documents into the plugin through the same event API regardless. |
| S4  | **Custom schema sync** | Standard document types work out of the box. CDS-annotation-driven sync of custom extraction schemas to Document AI is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| S5  | **Field mapping**      | Results are delivered as raw JSON for maximum flexibility. Automatic mapping to CDS entity properties and Fiori form pre-fill is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| S6  | **Job recovery**       | Graceful degradation works (no binding → jobs stay `PENDING`). Automatic recovery of stuck or in-flight jobs on startup is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| S7  | **Local development**  | Degraded mode works without a binding. A richer mock that returns configurable static results is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| S8  | **Malware scanning**   | Not yet assessed or implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| S9  | **CLI scaffolding**    | Setup is manual and documented in the README. A `cds add document-ai` command is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| S10 | **OData API**          | REST API works across all plans. OData support for higher-tier plans is a future enhancement.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| S11 | **Job cleanup**        | Job records are kept for observability. A configurable retention policy is not yet implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| S12 | **Multiple bindings**  | One Document AI binding is resolved at startup. Routing documents to different instances by type, region, or business unit is not yet supported.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |

---

## Implementation Notes

### Poll cycle queries all tenants

The poll query fetches all active jobs across all tenants. In a multitenant deployment, jobs from different tenants get polled under the same credentials - which is incorrect. This is something to keep in mind if multitenancy becomes a priority.

### Service binding is resolved once at startup

If a binding is added after the app starts, it won't be picked up until a restart. The plugin resolves bindings once, at startup.

### Optimistic lock uses two database round-trips

`ExtractionServiceImpl.updateExtractionJob()` does a `SELECT` to read the current status, then an `UPDATE … WHERE status = currentStatus`. This is correct and safe, but the SELECT is an extra round-trip that could be eliminated by collapsing into a single read-modify-write statement, which would reduce the conflict window under high concurrency.

---

## Suggested Future Work

These are ideas and suggestions and not a fixed plan. The ordering reflects what felt most important at the time of writing, but the incoming team should feel free to reprioritise based on their own context and stakeholder needs.

### 1. Multitenancy _(S1)_

A natural first area to tackle would be making each tenant use its own Document AI credentials with isolated jobs. The polling logic and HTTP client would need to become tenant-aware - the `tenantId` field is already on `ExtractionJob`, so no schema migration is needed.

### 2. Annotation-Based Triggering _(S2)_

One possible enhancement is to allow developers to annotate a CDS entity field with `@DocumentAI` to automatically trigger extraction - removing the need for boilerplate event emission. This could cover both the backend (plugin reacts to annotated field writes) and the Fiori Elements UI (an "Upload & Extract" button injected automatically on the Object Page).

### 3. Job Recovery on Startup _(S6)_

A useful addition could be a startup check for any jobs left in `PENDING`, `SUBMITTED`, or `RUNNING` status from before a restart, resuming polling for them automatically rather than waiting for a new submission to arrive.

### 4. Extraction Progress Indicator

The backend already tracks `SUBMITTED` and `RUNNING` states - it could be worth surfacing that status in the Fiori Elements Object Page as a visible progress indicator or status strip so users have feedback while extraction is running.

### 5. Automatic Field Mapping _(S5)_

One idea is to have the plugin match extracted fields to CDS entity properties by name convention and pre-fill the Fiori form automatically. Fields below a configurable confidence threshold could be visually flagged (e.g. amber highlight) so users know what to double-check before saving.

### 6. Document Viewer with Extraction Highlights and Human-in-the-Loop Verification

A more exploratory idea is to visualise extracted fields on the document itself - bounding boxes colour-coded by confidence level, with click-to-focus between the document viewer and the form. Bounding box coordinates come back from Document AI and would need to be stored alongside the extraction result and exposed to a UI viewer component.

It could also be worth visually marking AI-extracted fields in the Fiori form (e.g. a distinct badge or icon) so users always know which values were filled by the model. This distinction could persist until a human explicitly confirms or edits the value.

A further possibility is a human-in-the-loop confirmation step: the user reviews the extracted fields, corrects any errors, and explicitly confirms the result. This confirmed payload could be submitted back to Document AI as ground-truth feedback to activate the [instant learning](https://help.sap.com/docs/document-ai/sap-document-ai/instant-learning?locale=en-US) feature, improving model accuracy for that schema over time.

### 7. Document AI Outbound Channels - Push-Based Result Delivery

Document AI supports outbound channels at the schema level: notification channels (status pushes) and extension channels (callbacks triggered after prediction). One option worth exploring is registering the plugin as a target so Document AI pushes results to it directly, eliminating the need to poll. The `DocumentAiClient` interface and `ExtractionService.updateExtractionResult()` are already the right place to plug this in.

### 8. Custom Schema Synchronisation _(S4)_

One possible enhancement is to let developers define custom document type extraction schemas in the CDS model via annotations, with the plugin syncing these to Document AI automatically at deploy time or startup - removing the need for manual configuration in the Document AI workspace.

### 9. Customisable Extraction Templates

Right now, every submission requires constructing the Document AI `options` JSON by hand. A template mechanism could let developers define named configurations - document type, schema ID, field selection, confidence thresholds - declaratively in the CDS model or `application.yaml`, and just reference the template name at submission time.

### 10. Local Mock Mode _(S7)_

A mock mode returning configurable static extraction results without a real Document AI binding would make local development more convenient.

### 11. Application-Level Outbound Channels

The plugin currently delivers results only via the `DocumentExtractionResult` CDS event. It could be worth exploring additional delivery channels so consuming applications can receive results through whatever channel fits their architecture.

### 12. `cds add document-ai` Scaffold Command _(S9)_

A `cds add document-ai` CLI command could set up the Document AI service binding in `mta.yaml`, enable the persistent outbox in `application.yaml`, and generate boilerplate handler stubs - lowering the barrier significantly for new adopters.

### 13. OData API Support _(S10)_

For applications on higher-tier plans, it could be worth exploring the Document AI OData API as an alternative transport, enabling richer querying and result navigation.

### 14. Terminal Job Cleanup _(S11)_

A configurable retention policy that deletes or archives `ExtractionJob` rows after they've been in `DONE` or `FAILED` status for a set period would prevent unbounded table growth on high-volume deployments.

### 15. Malware Scanning _(S8)_

It may be worth assessing whether documents should be scanned via SAP Malware Scanning Service before being forwarded to Document AI - particularly for multitenant deployments where uploaded content is less trusted.

### 16. Multiple Service Binding Support _(S12)_

Supporting multiple Document AI bindings could allow applications to route documents to different instances based on context - document type, region, business unit. This would need a binding selection strategy, either convention-based or configurable via annotations or `application.yaml`.

---

The plugin is stable at MVP level and provides a solid foundation for further development. The core extraction lifecycle, persistence model, and event-based architecture are in place. The suggestions in this document reflect the state of the project at the time of handover and are intended to provide context, not prescribe a roadmap.
