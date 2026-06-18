namespace test;

// genre_ID and currency_code are declared as plain scalars here because this is a test model.
// In a real CDS model these would be generated foreign key columns from annotated associations.
@odata.draft.enabled
entity Books {
  key ID         : UUID;
  title          : String;
  @Common.ValueListWithFixedValues
  genre_ID       : Integer;
  @Common.Text   : genre.name
  genre          : Association to Genres;
  @Common.ValueListWithFixedValues
  currency_code  : String(3);
  @(Common.Text: { $value: ![currency.name] })
  currency       : Association to Currencies;
  image          : LargeBinary;
  embedding      : Vector(8);
}

entity Genres {
  key ID   : Integer;
  name     : String;
}

entity Currencies {
  key code : String(3);
  name     : String;
}

@odata.draft.enabled
entity OrderItems {
  key order_ID    : Integer;
  key item_no     : Integer;
  @Common.ValueListWithFixedValues
  category_ID     : Integer;
}

@odata.draft.enabled
entity IsbnBooks {
  key isbn        : String;
  @Common.ValueListWithFixedValues
  category_ID     : Integer;
}

entity PlainEntity {
  key ID   : UUID;
  title    : String;
}

@odata.draft.enabled
entity BooksWithDisabledValueList {
  key ID          : UUID;
  @Common.ValueListWithFixedValues
  genre_ID        : Integer;
  @Common.ValueListWithFixedValues
  @cds.odata.valuelist: false
  suppressed_ID   : Integer;
}

@odata.draft.enabled
entity BooksWithRecommendationState {
  key ID          : UUID;
  @Common.ValueListWithFixedValues
  genre_ID        : Integer;
  @Common.ValueListWithFixedValues
  @UI.RecommendationState: 0
  disabled_ID     : Integer;
  @Common.ValueListWithFixedValues
  @UI.RecommendationState: 1
  enabled_ID      : Integer;
}

service TestService {
  entity Books        as projection on test.Books;
  entity Genres       as projection on test.Genres;
  entity Currencies   as projection on test.Currencies;
  entity OrderItems   as projection on test.OrderItems;
  entity IsbnBooks    as projection on test.IsbnBooks;
  entity PlainEntity  as projection on test.PlainEntity;
  entity BooksWithDisabledValueList as projection on test.BooksWithDisabledValueList;
  entity BooksWithRecommendationState as projection on test.BooksWithRecommendationState;
}
