using {sap.capire.bookshop as my} from '../db/schema';

service SupplierInvoicesService @(requires: 'any') {
  @odata.draft.enabled
  entity SupplierInvoices as projection on my.SupplierInvoices actions {
    action extractInvoiceData() returns Boolean;
  };

  @readonly entity Suppliers     as projection on my.Suppliers;
  @readonly entity InvoiceStatus as projection on my.InvoiceStatus;
  @readonly entity Books         as projection on my.Books;
}
