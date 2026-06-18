# cds-feature-recommendations

AI-powered field recommendations for SAP Fiori UIs in CAP Java applications, leveraging SAP AI Core and the SAP-RPT-1 foundation model.

## How It Works

The plugin generically hooks into any draft-enabled entity that has properties with a value help. When a user edits a draft record, the plugin:

1. Fetches historical records as training context
2. Sends context + current row to the provided model (default: RPT-1 model)
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
- The `SAP_Recommendations` navigation property must be added to the entities that should receive recommendations by
  - either installing the `@cap-js/ai` CDS plugin (automatically provides the model enhancement that adds `SAP_Recommendations` as a navigation property)
  - or adding the `SAP_Recommendations`property manually.
  Without the `SAP_Recommendations` navigation property, the predictions will be computed but not serialized in OData responses.

#### CDS Plugin

Add `@cap-js/ai` to your project's `package.json`:

```json
{
  "dependencies": {
    "@cap-js/ai": "^1",
    "@sap/cds": "^9"
  }
}
```

Then run `npm install`. The plugin hooks into the CDS compiler and automatically adds the `SAP_Recommendations` navigation property to draft-enabled entities that have value-list fields. 

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
#### Adding the SAP_Recommendations navigation property manually

If you cannot use the CDS plugin, add the `SAP_Recommendations` navigation property directly in your CDS model. You need to:

1. **Define a `RecommendationItem_*` type** for each CDS primitive type used by your value-list fields. Each type must contain the four fixed fields shown below — only `RecommendedFieldValue` varies by type.
2. **Extend each target entity** with a `SAP_Recommendations` composition that has one entry per value-list field, using the field name as the property name and the matching `RecommendationItem_*` type.

The property names inside `SAP_Recommendations` must exactly match the field names on the entity (e.g. `genre_ID`, `author_ID`).

```cds
// Define one type per CDS primitive used by your value-list fields
type RecommendationItem_Integer {
  RecommendedFieldValue       : Integer;
  RecommendedFieldDescription : String;
  RecommendedFieldScoreValue  : Decimal;
  RecommendedFieldIsSuggestion: Boolean;
}

type RecommendationItem_UUID {
  RecommendedFieldValue       : UUID;
  RecommendedFieldDescription : String;
  RecommendedFieldScoreValue  : Decimal;
  RecommendedFieldIsSuggestion: Boolean;
}

// Extend your entity — one entry per value-list field
extend my.Books with {
  SAP_Recommendations: Composition of one {
    genre_ID : many RecommendationItem_Integer;
    author_ID: many RecommendationItem_UUID;
  }
}
```

See also the [SAP Fiori Elements – Recommendations documentation](https://help.sap.com/docs/SAPUI5/b2f662dd9d7a4ec680056733050b4d34/1a6324d5ad7f4034a93f911b4e53e080.html).

## Enabling Recommendations

Recommendations are triggered for fields annotated with `@Common.ValueList`, `@Common.ValueListWithFixedValues`, or whose association target has `@cds.odata.valuelist`:

```cds
@odata.draft.enabled
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
> in the Java runtime for it to take effect. By default, the CAP Java runtime strips `@UI.*`
> annotations from the in-memory model to reduce memory consumption (they are typically only
> needed for OData metadata generation, not for runtime logic).
>
> ```yaml
> cds:
>   model:
>     include-ui-annotations: true
> ```

## Configuration

The following configuration applies to the RPT-1 model implementation.

```yaml
cds:
  requires:
    recommendations:
      contextRowLimit: 2000 # Max historical rows used as training context (RPT-1)
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

The following field types are supported by the RPT-1 model implementation:

| Category | Types                                                                  |
| -------- | ---------------------------------------------------------------------- |
| String   | `String`, `LargeString`, `UUID` (treated as string)                    |
| Numeric  | `Integer`, `Int16`, `Int32`, `Int64`, `Integer64`, `Decimal`, `Double` |
| Temporal | `Date`, `Time`, `DateTime`, `Timestamp`                                |
| Other    | `Boolean`                                                              |

Binary, vector, and draft system fields are excluded automatically.

## Local Development

Without an AI Core binding, the plugin uses a `MockAIClient` that returns random predictions from existing context rows - useful for UI development without AI Core access. The `@cap-js/ai` CDS plugin is still required for the model enhancement.

## Related

- [`cds-feature-ai-core`](../cds-feature-ai-core/README.md) - Required dependency for AI Core connectivity
- [SAP Fiori Elements - Intelligent Suggestions](https://experience.sap.com/fiori-design-web/)
