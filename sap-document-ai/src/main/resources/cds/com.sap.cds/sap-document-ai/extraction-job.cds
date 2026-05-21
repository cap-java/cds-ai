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

entity ExtractionJob : cuid, managed {
    attachmentId : String;
    status       : ExtractionStatus;
    tenantId     : String;
}
