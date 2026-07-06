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

// --- Procurement domain (Document AI showcase) ---

entity Suppliers : managed, cuid {
  @mandatory name : String(200);
  country         : String(3);
  email           : String(200);
  invoices        : Association to many SupplierInvoices
                      on invoices.supplier = $self;
}

@cds.odata.valuelist
entity InvoiceStatus : CodeList {
  key code : String(20);
}

entity SupplierInvoices : managed, cuid {
  invoiceNumber   : String(50);
  invoiceDate     : Date;
  supplier        : Association to Suppliers;
  totalAmount     : Decimal;
  currency        : Currency;
  status          : Association to InvoiceStatus;
  documentAiJobId : String;
  lineItems       : Composition of many SupplierInvoiceLineItems
                      on lineItems.invoice = $self;
}

entity SupplierInvoiceLineItems : cuid {
  invoice   : Association to SupplierInvoices;
  description : String(500);
  quantity    : Integer;
  unitPrice   : Decimal;
  book        : Association to Books;
}
