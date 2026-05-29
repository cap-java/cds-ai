namespace sap.document.ai;

using {
    cuid,
    managed
} from '@sap/cds/common';

type ExtractionStatus : String enum {
    Pending;
    Processing;
    Completed;
    Failed;
}

@assert.unique: { attachmentId: [attachmentId] }
entity ExtractionJob : cuid, managed {
    attachmentId : String;
    status       : ExtractionStatus default #Pending;
    tenantId     : String;
}
