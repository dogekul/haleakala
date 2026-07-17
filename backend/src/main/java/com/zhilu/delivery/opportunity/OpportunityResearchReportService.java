package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.knowledge.KnowledgeService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OpportunityResearchReportService {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}");
  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;
  private final OpportunityService opportunities;

  public OpportunityResearchReportService(
      JdbcTemplate jdbc, DocumentCenterService documents, OpportunityService opportunities) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.opportunities = opportunities;
  }

  public PreparedReport prepare(long organizationId, long opportunityId, long version) {
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    assertLead(opportunity, version);
    Template template = template(organizationId);
    long root = documents.ensureIndex(
        organizationId, "OPPORTUNITY_ROOT", "商机文档", null);
    long folder = documents.ensureIndex(
        organizationId, "OPPORTUNITY:" + opportunityId,
        opportunity.get("customerName") + " · " + opportunity.get("title"),
        Long.valueOf(root));
    String businessKey = businessKey(opportunityId);
    documents.createDocument(
        organizationId, businessKey, "OPPORTUNITY_RESEARCH",
        replaceKnown(template.title, opportunity), replaceKnown(template.markdown, opportunity),
        folder);
    return new PreparedReport(
        documents.readBusinessDocument(organizationId, businessKey),
        template.id, template.revision);
  }

  public DocumentView read(long organizationId, long opportunityId) {
    opportunities.get(organizationId, opportunityId);
    return documents.readBusinessDocument(organizationId, businessKey(opportunityId));
  }

  public DocumentView saveDraft(
      long organizationId, long opportunityId, String title, String markdown, long revision) {
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    assertLead(opportunity, ((Number) opportunity.get("version")).longValue());
    return documents.updateBusinessDocument(
        organizationId, businessKey(opportunityId), title, markdown, revision);
  }

  public Map<String, Object> submit(
      long organizationId, long opportunityId, long actorId, long opportunityVersion,
      String title, String markdown, long revision) {
    if (blank(markdown)) throw new IllegalArgumentException("需求调研报告正文不能为空");
    if (PLACEHOLDER.matcher(markdown).find()) {
      throw new IllegalArgumentException("需求调研报告仍有未填写的模版字段");
    }
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    if (!"LEAD".equals(opportunity.get("stage"))) {
      Integer submitted = jdbc.queryForObject(
          "select count(*) from opportunity_artifact where organization_id=? "
              + "and opportunity_id=? and artifact_type='RESEARCH_REPORT' "
              + "and outline_link_id is not null",
          Integer.class, organizationId, opportunityId);
      if (submitted != null && submitted > 0) return opportunity;
      throw new ConflictException("只有线索阶段可以提交需求调研报告");
    }
    assertLead(opportunity, opportunityVersion);
    Template template = template(organizationId);
    DocumentView saved = documents.updateBusinessDocument(
        organizationId, businessKey(opportunityId), title, markdown, revision);
    return opportunities.advanceWithResearchReport(
        organizationId, opportunityId, actorId, opportunityVersion, saved.getLinkId(),
        template.id, template.revision, saved.getTitle());
  }

  private Template template(long organizationId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select k.id,c.published_revision,c.published_title_snapshot,"
            + "c.published_markdown_snapshot from knowledge_item k "
            + "join document_template_config c on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.stage_code=? and c.requirement='REQUIRED' and c.enabled=true "
            + "and c.published_revision is not null and c.published_title_snapshot is not null "
            + "and c.published_markdown_snapshot is not null order by k.id",
        organizationId, KnowledgeService.OPPORTUNITY_RESEARCH);
    if (values.isEmpty()) throw new ConflictException("请先发布并启用需求调研报告模版");
    if (values.size() > 1) throw new ConflictException("需求调研报告模版存在多个，请只保留一个启用版本");
    Map<String, Object> value = values.get(0);
    return new Template(
        ((Number) value.get("id")).longValue(),
        ((Number) value.get("published_revision")).longValue(),
        String.valueOf(value.get("published_title_snapshot")),
        String.valueOf(value.get("published_markdown_snapshot")));
  }

  private void assertLead(Map<String, Object> opportunity, long version) {
    if (((Number) opportunity.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    if (!"OPEN".equals(opportunity.get("status"))) {
      throw new ConflictException("终态商机不能继续推进");
    }
    if (!"LEAD".equals(opportunity.get("stage"))) {
      throw new ConflictException("只有线索阶段可以维护需求调研报告");
    }
  }

  private String replaceKnown(String value, Map<String, Object> opportunity) {
    return value.replace("{{客户名称}}", String.valueOf(opportunity.get("customerName")))
        .replace("{{商机名称}}", String.valueOf(opportunity.get("title")))
        .replace("{{商机标题}}", String.valueOf(opportunity.get("title")));
  }

  private String businessKey(long opportunityId) {
    return "OPPORTUNITY:" + opportunityId + ":RESEARCH_REPORT";
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
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

  private static final class Template {
    private final long id;
    private final long revision;
    private final String title;
    private final String markdown;

    private Template(long id, long revision, String title, String markdown) {
      this.id = id;
      this.revision = revision;
      this.title = title;
      this.markdown = markdown;
    }
  }
}
