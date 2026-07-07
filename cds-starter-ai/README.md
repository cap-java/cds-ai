# cds-starter-ai

A zero-source convenience module that bundles all SAP AI plugins for CAP Java into a single dependency.

## What it includes

| Module | Scope | Description |
|---|---|---|
| [`cds-feature-ai-core`](../cds-feature-ai-core/README.md) | `compile` | Bridges CAP Java to SAP AI Core — resource groups, deployments, configurations, inference client |
| [`cds-feature-recommendations`](../cds-feature-recommendations/README.md) | `runtime` | AI-powered field recommendations for Fiori Elements draft UIs |

`cds-feature-sap-document-ai` is currently **not** included as the plugin is not yet fully multi-tenant-ready. It will be added to the starter once MT support is complete. Add it as an explicit dependency if you need it today.

## Setup

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-starter-ai</artifactId>
    <version>${cds-ai.version}</version>
</dependency>
```

Both plugins auto-register via Java's `ServiceLoader` — no further code changes are required.

For the Node.js side of `cds-feature-recommendations`, also add the `@cap-js/ai` CDS plugin to your `package.json`:

```json
{
  "dependencies": {
    "@cap-js/ai": "^1"
  }
}
```

## Prerequisites

- Java 21+
- CAP Java 4.9+
- An [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding (for production use)
- A [SAP Document Information Extraction](https://help.sap.com/docs/document-ai) service binding (once `cds-feature-sap-document-ai` is included)

Without the respective bindings the plugins fall back to mock implementations — useful for local development.

## Related

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP AI SDK for Java](https://github.com/SAP/ai-sdk-java)
- [`cds-feature-sap-document-ai`](../cds-feature-sap-document-ai/README.md) — standalone document extraction plugin
