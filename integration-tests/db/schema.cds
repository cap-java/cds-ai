namespace itest;

using { managed, cuid } from '@sap/cds/common';

entity Products {
  key ID       : Integer;
      name     : String;
      price    : Decimal;
      category : String;
}

@cds.odata.valuelist
entity Categories {
  key ID   : Integer;
      name : String;
}

@cds.odata.valuelist
entity Priorities {
  key code : String(10);
      name : String;
}

entity Tasks : managed, cuid {
      title       : String(200);
      description : String(1000);
      effort      : Integer;
      category    : Association to Categories;
      priority    : Association to Priorities;
}

entity BooksWithCustomKey : managed {
  key isbn     : String(20);
      title    : String(200);
      price    : Decimal;
      category : Association to Categories;
}

entity OrderItems : managed {
  key order_no  : Integer;
  key item_no   : Integer;
      product   : String(200);
      quantity  : Integer;
      category  : Association to Categories;
}
