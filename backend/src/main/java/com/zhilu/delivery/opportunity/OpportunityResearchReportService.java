package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.document.DocumentView;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpportunityResearchReportService {
  private static final String TYPE = "RESEARCH_REPORT";
  private final OpportunityStageDocumentService documents;

  public OpportunityResearchReportService(OpportunityStageDocumentService documents) {
    this.documents = documents;
  }

  public PreparedReport prepare(long organizationId, long opportunityId, long version) {
    OpportunityStageDocumentService.PreparedDocument prepared =
        documents.prepare(organizationId, opportunityId, TYPE, version);
    return new PreparedReport(prepared.getDocument(), prepared.getSourceTemplateId(),
        prepared.getSourceTemplateRevision());
  }

  public DocumentView read(long organizationId, long opportunityId) {
    return documents.read(organizationId, opportunityId, TYPE);
  }

  public DocumentView saveDraft(
      long organizationId, long opportunityId, String title, String markdown, long revision) {
    return documents.saveDraft(
        organizationId, opportunityId, TYPE, title, markdown, revision);
  }

  public Map<String, Object> submit(
      long organizationId, long opportunityId, long actorId, long opportunityVersion,
      String title, String markdown, long revision) {
    return documents.submit(organizationId, opportunityId, actorId, TYPE, opportunityVersion,
        title, markdown, revision).getOpportunity();
  }

  public static final class PreparedReport {
    private final DocumentView document;
    private final long sourceTemplateId;
    private final long sourceTemplateRevision;

    public PreparedReport(
        DocumentView document, long sourceTemplateId, long sourceTemplateRevision) {
      this.document = document;
      this.sourceTemplateId = sourceTemplateId;
      this.sourceTemplateRevision = sourceTemplateRevision;
    }

    public DocumentView getDocument() { return document; }
    public long getSourceTemplateId() { return sourceTemplateId; }
    public long getSourceTemplateRevision() { return sourceTemplateRevision; }
  }
}
