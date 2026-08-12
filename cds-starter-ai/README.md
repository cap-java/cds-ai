# cds-starter-ai

A zero-source convenience module that bundles all SAP AI plugins for CAP Java into a single dependency.

## What it includes

| Module | Scope | Description |
|---|---|---|
| [`cds-feature-ai-core`](../cds-feature-ai-core/README.md) | `compile` | Bridges CAP Java to SAP AI Core — resource groups, deployments, configurations, inference client |
| [`cds-feature-recommendations`](../cds-feature-recommendations/README.md) | `runtime` | AI-powered field recommendations for Fiori Elements draft UIs |

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

Without the respective binding the plugins fall back to mock implementations — useful for local development.

## Related

- [SAP AI Core Documentation](https://help.sap.com/docs/sap-ai-core)
- [SAP AI SDK for Java](https://github.com/SAP/ai-sdk-java)
