using {AdminService} from '../../srv/admin-service.cds';

////////////////////////////////////////////////////////////////////////////
//
//	Books Object Page
//
annotate AdminService.Books with @(UI: {
    HeaderInfo       : {
        TypeName      : '{i18n>Book}',
        TypeNamePlural: '{i18n>Books}',
        Title         : {Value: title},
        Description   : {Value: author.name}
    },
    Facets             : [
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>General}',
      Target: '@UI.FieldGroup#General'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Details}',
      Target: '@UI.FieldGroup#Details'
    },
    {
      $Type : 'UI.ReferenceFacet',
      Label : '{i18n>Admin}',
      Target: '@UI.FieldGroup#Admin'
    }
  ],
  FieldGroup #General: {Data: [
    {Value: title},
    {Value: author_ID},
    {Value: genre_ID},
    {Value: descr}
  ]},
  FieldGroup #Details: {Data: [
    {Value: stock},
    {Value: price},
    {Value: currency_code}
  ]},
  FieldGroup #Admin  : {Data: [
    {Value: createdBy},
    {Value: createdAt},
    {Value: modifiedBy},
    {Value: modifiedAt}
  ]}
});


////////////////////////////////////////////////////////////
//
//  Draft for Localized Data
//
annotate sap.capire.bookshop.Books with @fiori.draft.enabled;
annotate AdminService.Books with @odata.draft.enabled;

// In addition we need to expose Languages through AdminService as a target for ValueList
using {sap} from '@sap/cds/common';

extend service AdminService {
  @readonly
  entity Languages as projection on sap.common.Languages;
}

// Workaround for Fiori popup for asking user to enter a new UUID on Create
annotate AdminService.Books with {
  ID @Core.Computed;
}

// Show Genre as drop down, not a dialog
annotate AdminService.Books with {
  genre @Common.ValueListWithFixedValues;
}

// Show currency also as drop down, not a dialog
annotate AdminService.Books with {
  currency @Common.ValueListWithFixedValues;
}
