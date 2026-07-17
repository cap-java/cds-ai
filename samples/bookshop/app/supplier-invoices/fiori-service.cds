using SupplierInvoicesService as service from '../../srv/supplier-invoices-service';

////////////////////////////////////////////////////////////////////////////
//
//  Supplier Invoices List Page
//
annotate service.SupplierInvoices with @(UI: {
  SelectionFields: [
    invoiceNumber,
    supplier_ID,
    status_code
  ],
  LineItem       : [
    {Value: invoiceNumber, Label: '{i18n>InvoiceNumber}'},
    {Value: supplier.name, Label: '{i18n>Supplier}'},
    {Value: invoiceDate,   Label: '{i18n>InvoiceDate}'},
    {Value: totalAmount,   Label: '{i18n>TotalAmount}'},
    {Value: currency_code, Label: '{i18n>Currency}'},
    {Value: status.name,   Label: '{i18n>Status}'}
  ]
});

////////////////////////////////////////////////////////////////////////////
//
//  Supplier Invoices Object Page
//
annotate service.SupplierInvoices with @(UI: {
  HeaderInfo: {
    TypeName      : '{i18n>SupplierInvoice}',
    TypeNamePlural: '{i18n>SupplierInvoices}',
    Title         : {Value: invoiceNumber},
    Description   : {Value: supplier.name}
  },
  Identification: [{Value: invoiceNumber}],
  Facets        : [
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'InvoiceDetails',
      Label : '{i18n>InvoiceDetails}',
      Target: '@UI.FieldGroup#Details'
    },
    {
      $Type : 'UI.ReferenceFacet',
      ID    : 'AttachmentsFacet',
      Label : '{i18n>Attachments}',
      Target: 'attachments/@UI.LineItem'
    }
  ],
  FieldGroup #Details: {Data: [
    {Value: invoiceNumber, Label: '{i18n>InvoiceNumber}'},
    {Value: invoiceDate,   Label: '{i18n>InvoiceDate}'},
    {Value: supplier_ID,   Label: '{i18n>Supplier}'},
    {Value: totalAmount,   Label: '{i18n>TotalAmount}'},
    {Value: currency_code, Label: '{i18n>Currency}'},
    {Value: status_code,   Label: '{i18n>Status}'}
  ]}
});

// Action button on Object Page header
annotate service.SupplierInvoices with @(
  UI.Identification : [
    {
      $Type  : 'UI.DataFieldForAction',
      Action : 'SupplierInvoicesService.extractInvoiceData',
      Label  : 'Extract Invoice Data'
    }
  ]
);

annotate service.SupplierInvoices with {
  invoiceNumber @title: '{i18n>InvoiceNumber}';
  supplier      @title: '{i18n>Supplier}';
  status        @title: '{i18n>Status}';
}

annotate service.SupplierInvoices with {
  supplier @Common: {
    Text           : supplier.name,
    TextArrangement: #TextOnly,
    ValueList      : {
      CollectionPath: 'Suppliers',
      Parameters    : [{
        $Type            : 'Common.ValueListParameterInOut',
        ValueListProperty: 'ID',
        LocalDataProperty: supplier_ID
      }]
    }
  };
  status @Common: {
    Text                    : status.name,
    TextArrangement         : #TextOnly,
    ValueListWithFixedValues: true
  };
}
