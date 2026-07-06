using {sap.capire.bookshop as my} from '../db/schema';
using {sap.attachments.Attachments} from 'com.sap.cds/cds-feature-attachments';

extend my.SupplierInvoices with {
  attachments : Composition of many Attachments;
}
