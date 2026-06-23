namespace sap.document.ai;

using {
    cuid,
    managed
} from '@sap/cds/common';

entity SourceDocument : cuid {
    fileName : String;
    mimeType : String @Core.IsMediaType;
    content  : LargeBinary @Core.MediaType: mimeType;
}

entity ExtractionJob : cuid, managed {
    sourceDocument  : Association to SourceDocument;
    status          : String;
    tenantId        : String;
    documentAiJobId : String;
}
