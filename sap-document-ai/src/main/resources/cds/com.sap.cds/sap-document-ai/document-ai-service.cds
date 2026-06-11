namespace sap.document.ai;

using {sap.document.ai as ai} from './extraction-job';

service DocumentAiService {
  entity SourceDocument as projection on ai.SourceDocument;

  @readonly
  entity ExtractionJob as projection on ai.ExtractionJob
    excluding {
      attachmentId,
      tenantId,
      documentAiJobId
    };

  action startExtraction(sourceDocumentId : UUID) returns ExtractionJob;
}
