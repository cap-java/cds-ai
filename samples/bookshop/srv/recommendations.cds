// Manual SAP_Recommendations model definition.
// This replaces the compile-time enhancement that @cap-js/ai's cds-plugin.js
// would normally generate automatically for all draft-enabled entities with
// value-list fields.

using { AdminService } from './admin-service';

// Virtual entity holding recommendation results for Books.
// The structure mirrors what the node plugin generates: one arrayed element per
// value-list field, each containing the standard recommendation sub-elements.
@cds.persistence.skip
entity AdminService.Books_Recommendations {
  key technicalRecommendationsIdentifier : UUID;
  virtual genre_ID : many {
    RecommendedFieldValue      : Integer;
    RecommendedFieldDescription : String;
    RecommendedFieldScoreValue  : Decimal;
    RecommendedFieldIsSuggestion : Boolean;
  };
  virtual currency_code : many {
    RecommendedFieldValue      : String(3);
    RecommendedFieldDescription : String;
    RecommendedFieldScoreValue  : Decimal;
    RecommendedFieldIsSuggestion : Boolean;
  };
}

// Add SAP_Recommendations navigation property to Books.
// The on-condition is trivial (entity is @cds.persistence.skip, never queried from DB).
// Use 'extend ... with columns' because AdminService.Books is a projection.
extend AdminService.Books with columns {
  SAP_Recommendations : Association to one AdminService.Books_Recommendations
    on 1 = 1
}

// Tells Fiori Elements to auto-expand SAP_Recommendations on draft reads.
annotate AdminService.Books with @UI.Recommendations: SAP_Recommendations;
