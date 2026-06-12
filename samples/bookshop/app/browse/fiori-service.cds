using {CatalogService} from '../../srv/cat-service.cds';

////////////////////////////////////////////////////////////////////////////
//
//	Books Object Page
//
annotate CatalogService.Books with @(UI: {
    HeaderInfo       : {
        TypeName      : '{i18n>Book}',
        TypeNamePlural: '{i18n>Books}',
        Title         : {Value: title},
        Description   : {Value: author}
    },
    Facets           : [{
        $Type : 'UI.CollectionFacet',
        Label : '{i18n>Details}',
        Facets: [
            {
                $Type : 'UI.ReferenceFacet',
                Target: '@UI.FieldGroup#Descr'
            },
            {
                $Type : 'UI.ReferenceFacet',
                Target: '@UI.FieldGroup#Price'
            }
        ]
    }],
    FieldGroup #Descr : {Data: [{Value: descr, ![@UI.MultiLineText]: true}]},
    FieldGroup #Price: {Data: [
        {Value: price},
        {Value: currency_code}
    ]}
});

////////////////////////////////////////////////////////////////////////////
//
//	Books List Page
//
annotate CatalogService.Books with @(UI: {
    SelectionFields: [
        ID,
        price,
        currency_code
    ],
    LineItem       : [
        {
            Value: ID,
            Label: '{i18n>Title}'
        },
        {
            Value: author,
            Label: '{i18n>Author}'
        },
        {Value: genre.name},
        {Value: price},
        {Value: currency_code}
    ]
});
