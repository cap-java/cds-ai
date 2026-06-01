using {
  managed,
  cuid,
  sap.common.CodeList
} from '@sap/cds/common';
using { Currency } from '@sap/cds-common-content';

namespace sap.capire.bookshop;

@cds.search: { title , title_embedding }
entity Books : managed, cuid {
  @Search.fuzzinessThreshold: 0.5
  @mandatory title  : String(111);
  descr             : String(1111);
  author : Association to Authors;
  genre             : Association to Genres;
  stock             : Integer;
  price             : Decimal;
  currency          : Currency;
}

entity Authors : managed, cuid {
  @mandatory name : String(111);
  dateOfBirth     : Date;
  dateOfDeath     : Date;
  placeOfBirth    : String;
  placeOfDeath    : String;
  books           : Association to many Books
                      on books.author = $self;
}

/** Hierarchically organized Code List for Genres */
@cds.odata.valuelist
entity Genres : CodeList {
  key ID       : Integer;
      parent   : Association to Genres;
      children : Composition of many Genres
                   on children.parent = $self;
}
