# cds-feature-recommendations

AI-powered field recommendations for SAP Fiori UIs in CAP Java applications, leveraging SAP AI Core and the SAP-RPT-1 foundation model.

## How It Works

The plugin generically hooks into any draft-enabled entity that has properties with a value help. When a user edits a draft record, the plugin:

1. Fetches historical records as training context
2. Sends context + current row to the RPT-1 model
3. Returns predictions as `SAP_Recommendations` in the OData response
4. Fiori Elements renders the recommendations as suggestions in form fields

## Setup

### Maven

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-feature-recommendations</artifactId>
    <version>${cds-ai.version}</version>
    <scope>runtime</scope>
</dependency>
```

Or use the starter that bundles this with `cds-feature-ai-core`:

```xml
<dependency>
    <groupId>com.sap.cds</groupId>
    <artifactId>cds-starter-ai</artifactId>
    <version>${cds-ai.version}</version>
    <scope>runtime</scope>
</dependency>
```

### Prerequisites

- An [SAP AI Core](https://help.sap.com/docs/sap-ai-core) service binding (see [`cds-feature-ai-core`](../cds-feature-ai-core/README.md))
- Entity must be **draft-enabled** (`@odata.draft.enabled`)
- At least one field annotated with a **value list**
- The `@cap-js/ai` CDS plugin must be installed (provides the model enhancement that adds `SAP_Recommendations` as a navigation property)

### CDS Plugin

Add `@cap-js/ai` to your project's `package.json`:

```json
{
  "dependencies": {
    "@cap-js/ai": "^1",
    "@sap/cds": "^9"
  }
}
```

Then run `npm install`. The plugin hooks into the CDS compiler and automatically adds the `SAP_Recommendations` navigation property to draft-enabled entities that have value-list fields. Without this plugin, predictions will be computed but not serialized in OData responses.

Since the Java module `cds-feature-ai-core` already provides the `AICore` service CDS model, disable the duplicate model from `@cap-js/ai` in your `.cdsrc.json`:

```json
{
  "requires": {
    "AICore": {
      "model": false
    }
  }
}
```

## Enabling Recommendations

Recommendations are triggered for fields annotated with `@Common.ValueList`, `@Common.ValueListWithFixedValues`, or whose association target has `@cds.odata.valuelist`:

```cds
entity Books {
  key ID : Integer;
  title  : String(111);
  descr  : String(1111);
  genre  : Association to one Genres;
  status : Association to one Status;
}

// Option 1: Annotate the association target
annotate Genres with @cds.odata.valuelist;

// Option 2: Annotate the field directly
annotate Books with {
  status @Common.ValueList: {
    CollectionPath: 'Status',
    Parameters: [{
      $Type: 'Common.ValueListParameterInOut',
      ValueListProperty: 'code',
      LocalDataProperty: status_code
    }]
  }
}
```

### Adding Text Descriptions

Use `@Common.Text` to show human-readable descriptions alongside recommended values:

```cds
annotate Books with {
  genre @Common.Text: 'genre.name';
}
```

### Disabling Recommendations for a Field

```cds
annotate Books with {
  genre @UI.RecommendationState: 0;
}
```

Fields annotated with `@UI.RecommendationState: 0` are excluded from predictions entirely.
A value of `1` (or omitting the annotation) means the field is eligible for recommendations.

> **Note:** Since `@UI.RecommendationState` is a UI annotation, you must enable UI annotation loading
> in the Java runtime for it to take effect:
>
> ```yaml
> cds:
>   model:
>     include-ui-annotations: true
> ```

## Configuration

```yaml
cds:
  requires:
    recommendations:
      contextRowLimit: 2000 # Max historical rows used as training context
```

See [`cds-feature-ai-core`](../cds-feature-ai-core/README.md) for AI Core connection and multi-tenancy configuration.

## UI Integration

The plugin adds a `SAP_Recommendations` map to OData read responses for draft entities. Each predicted field contains an array of suggestions:

```json
{
  "SAP_Recommendations": {
    "genre_ID": [
      {
        "RecommendedFieldValue": 12,
        "RecommendedFieldDescription": "Science Fiction",
        "RecommendedFieldScoreValue": 0.5,
        "RecommendedFieldIsSuggestion": true
      }
    ]
  }
}
```

SAP Fiori Elements automatically renders these as suggestions in form fields when editing a draft.

## Supported Field Types

| Category | Types                                                     |
| -------- | --------------------------------------------------------- |
| String   | `String`, `LargeString`, `UUID`                           |
| Numeric  | `Integer`, `Int16`, `Int32`, `Int64`, `Decimal`, `Double` |
| Temporal | `Date`, `Time`, `DateTime`, `Timestamp`                   |
| Other    | `Boolean`                                                 |

Binary, vector, and draft system fields are excluded automatically.

## Local Development

Without an AI Core binding, the plugin uses a `MockAIClient` that returns random predictions from existing context rows - useful for UI development without AI Core access. The `@cap-js/ai` CDS plugin is still required for the model enhancement.

## Related

- [`cds-feature-ai-core`](../cds-feature-ai-core/README.md) - Required dependency for AI Core connectivity
- [SAP Fiori Elements - Intelligent Suggestions](https://experience.sap.com/fiori-design-web/)
