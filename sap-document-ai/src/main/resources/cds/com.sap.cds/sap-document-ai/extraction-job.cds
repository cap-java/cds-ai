namespace sap.document.ai;

using {
    cuid,
    managed
} from '@sap/cds/common';

@assert.unique: { attachmentId: [attachmentId] }
entity ExtractionJob : cuid, managed {
    attachmentId : String;
    status       : String;
    tenantId:String;
}
