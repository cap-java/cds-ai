namespace sap.document.ai;

service DocumentAiService {
    event DocumentExtraction {
        fileName : String;
        mimeType : String @Core.IsMediaType;
        content  : LargeBinary @Core.MediaType: mimeType;
        options  : LargeString;
    }

    event DocumentExtractionResult {
        jobId            : String;
        documentAiJobId  : String;
        extractionResult : LargeString;
    }
}
