namespace sap.document.ai;

using {
    cuid,
    managed
} from '@sap/cds/common';

entity ExtractionJob : cuid, managed {
    status          : String;
    tenantId        : String;
    documentAiJobId : String;
}
