/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-sap-document-ai contributors.
*/
package com.sap.cds.service;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName(ExtractionService.EVENT_START_EXTRACTION)
public interface StartExtractionEventContext extends EventContext {

  static StartExtractionEventContext create() {
    return EventContext.create(StartExtractionEventContext.class, null);
  }

  String getAttachmentId();

  void setAttachmentId(String attachmentId);

  String getContentId();

  void setContentId(String contentId);

  String getTenantId();

  void setTenantId(String tenantId);

  String getFileName();

  void setFileName(String fileName);

  String getMimeType();

  void setMimeType(String mimeType);

  String getAttachmentEntityName();

  void setAttachmentEntityName(String attachmentEntityName);
}
