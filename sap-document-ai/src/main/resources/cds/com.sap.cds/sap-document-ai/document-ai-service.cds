namespace sap.document.ai;

service DocumentAiService {
    event DocumentExtraction {
        fileName : String;
        mimeType : String @Core.IsMediaType;
        content  : LargeBinary @Core.MediaType: mimeType;
    }
}
