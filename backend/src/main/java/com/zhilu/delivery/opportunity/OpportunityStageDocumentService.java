package com.zhilu.delivery.opportunity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OpportunityStageDocumentService {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}");
  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;
  private final OpportunityService opportunities;
  private final AiClient ai;
  private final ObjectMapper json;

  public OpportunityStageDocumentService(
      JdbcTemplate jdbc, DocumentCenterService documents, OpportunityService opportunities,
      AiClient ai, ObjectMapper json) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.opportunities = opportunities;
    this.ai = ai;
    this.json = json;
  }

  public PreparedDocument prepare(long organizationId, long opportunityId,
      String artifactType, long opportunityVersion) {
    OpportunityDocumentDefinition definition =
        OpportunityDocumentDefinition.forType(artifactType);
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    assertEditable(opportunity, definition, opportunityVersion);
    Template template = template(organizationId, definition);
    long root = documents.ensureIndex(
        organizationId, "OPPORTUNITY_ROOT", "商机文档", null);
    long folder = documents.ensureIndex(
        organizationId, "OPPORTUNITY:" + opportunityId,
        text(opportunity.get("customerName")) + " · " + text(opportunity.get("title")),
        Long.valueOf(root));
    String businessKey = businessKey(opportunityId, artifactType);
    documents.createDocument(
        organizationId, businessKey, definition.getTemplateScene(),
        replaceKnown(template.title, opportunity), replaceKnown(template.markdown, opportunity),
        folder);
    DocumentView document = documents.readBusinessDocument(organizationId, businessKey);
    if (!definition.isAiGenerated()) {
      return prepared(document, template, "MANUAL", null,
          Collections.<String>emptyList());
    }
    if (!replaceKnown(template.markdown, opportunity).equals(document.getMarkdown())) {
      return prepared(document, template, "MANUAL", null,
          Collections.singletonList("已保留现有草稿；如需重新生成，请使用 AI 重试并确认覆盖"));
    }
    return generateInternal(
        organizationId, opportunityId, opportunity, definition, template, document, true);
  }

  public DocumentView read(
      long organizationId, long opportunityId, String artifactType) {
    OpportunityDocumentDefinition.forType(artifactType);
    opportunities.get(organizationId, opportunityId);
    return documents.readBusinessDocument(
        organizationId, businessKey(opportunityId, artifactType));
  }

  public DocumentView saveDraft(long organizationId, long opportunityId, String artifactType,
      String title, String markdown, long revision) {
    OpportunityDocumentDefinition definition =
        OpportunityDocumentDefinition.forType(artifactType);
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    assertEditable(opportunity, definition,
        ((Number) opportunity.get("version")).longValue());
    return documents.updateBusinessDocument(organizationId,
        businessKey(opportunityId, artifactType), title, markdown, revision);
  }

  public PreparedDocument generate(long organizationId, long opportunityId, String artifactType,
      long revision, boolean confirmOverwrite) {
    OpportunityDocumentDefinition definition =
        OpportunityDocumentDefinition.forType(artifactType);
    if (!definition.isAiGenerated()) {
      throw new IllegalArgumentException("当前文档不支持 AI 生成");
    }
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    assertEditable(opportunity, definition,
        ((Number) opportunity.get("version")).longValue());
    Template template = template(organizationId, definition);
    DocumentView current = documents.readBusinessDocument(
        organizationId, businessKey(opportunityId, artifactType));
    String initial = replaceKnown(template.markdown, opportunity);
    if (!confirmOverwrite && !initial.equals(current.getMarkdown())) {
      throw new ConflictException("当前草稿已被编辑，确认覆盖后才能重新生成");
    }
    if (current.getRevision() != revision) {
      throw new ConflictException("文档已被更新，请刷新后重试");
    }
    return generateInternal(
        organizationId, opportunityId, opportunity, definition, template, current, true);
  }

  public SubmitResult submit(long organizationId, long opportunityId, long actorId,
      String artifactType, long opportunityVersion, String title, String markdown, long revision) {
    OpportunityDocumentDefinition definition =
        OpportunityDocumentDefinition.forType(artifactType);
    if (blank(markdown)) {
      throw new IllegalArgumentException(definition.getDisplayName() + "正文不能为空");
    }
    if (PLACEHOLDER.matcher(markdown).find()) {
      throw new IllegalArgumentException(definition.getDisplayName() + "仍有未填写的模版字段");
    }
    Map<String, Object> opportunity = opportunities.get(organizationId, opportunityId);
    if (isSubmitted(organizationId, opportunityId, artifactType)) {
      return new SubmitResult(documents.readBusinessDocument(
          organizationId, businessKey(opportunityId, artifactType)), opportunity);
    }
    assertEditable(opportunity, definition, opportunityVersion);
    Template template = template(organizationId, definition);
    DocumentView saved = documents.updateBusinessDocument(
        organizationId, businessKey(opportunityId, artifactType), title, markdown, revision);
    Map<String, Object> result = opportunities.submitDocumentArtifact(
        organizationId, opportunityId, actorId, opportunityVersion, artifactType,
        definition.getStage(), saved.getLinkId(), template.id, template.revision,
        saved.getTitle(), definition.isAdvanceOnSubmit());
    return new SubmitResult(saved, result);
  }

  private PreparedDocument generateInternal(long organizationId, long opportunityId,
      Map<String, Object> opportunity, OpportunityDocumentDefinition definition,
      Template template, DocumentView current, boolean allowOverwrite) {
    List<String> warnings = new ArrayList<String>();
    String context = generationContext(
        organizationId, opportunityId, opportunity, warnings);
    try {
      JsonNode generated = ai.completeJson(systemPrompt(definition),
          userPrompt(definition, opportunity, context, warnings), responseSchema());
      String title = requiredText(generated, "title");
      String markdown = requiredText(generated, "markdown");
      if (!allowOverwrite) throw new ConflictException("当前草稿不能覆盖");
      DocumentView saved = documents.updateBusinessDocument(
          organizationId, businessKey(opportunityId, definition.getArtifactType()),
          title, markdown, current.getRevision());
      return prepared(saved, template, "AI", null, warnings);
    } catch (RuntimeException failure) {
      return prepared(current, template, "FAILED", message(failure), warnings);
    }
  }

  private String generationContext(long organizationId, long opportunityId,
      Map<String, Object> opportunity, List<String> warnings) {
    StringBuilder context = new StringBuilder();
    context.append("## 已提交的需求调研报告\n");
    List<Map<String, Object>> research = jdbc.queryForList(
        "select outline_link_id from opportunity_artifact where organization_id=? "
            + "and opportunity_id=? and artifact_type='RESEARCH_REPORT' "
            + "and outline_link_id is not null order by id desc",
        organizationId, opportunityId);
    if (research.isEmpty()) {
      warnings.add("未找到已提交的需求调研报告，请人工核对生成内容");
      context.append("（缺失）\n");
    } else {
      try {
        long linkId = ((Number) research.get(0).get("outline_link_id")).longValue();
        context.append(documents.readLink(linkId, organizationId).getMarkdown()).append('\n');
      } catch (RuntimeException unavailable) {
        warnings.add("需求调研报告暂时无法读取，请人工核对生成内容");
        context.append("（暂时无法读取）\n");
      }
    }
    Long productId = number(opportunity.get("productId"));
    Long productVersionId = number(opportunity.get("productVersionId"));
    context.append("\n## 关联产品功能与设计 Spec\n");
    if (productId == null) {
      warnings.add("商机未关联产品，已保留模版供人工填写");
      context.append("（未关联产品）\n");
      return context.toString();
    }
    List<Map<String, Object>> features;
    if (productVersionId != null) {
      features = jdbc.queryForList(
          "select f.id feature_id,f.code feature_code,f.name feature_name,"
              + "f.outline_link_id,pvf.availability from product_version_feature pvf "
              + "join product_feature f on f.id=pvf.product_feature_id "
              + "where pvf.product_version_id=? and f.product_id=? "
              + "and pvf.availability in ('INCLUDED','PLANNED') "
              + "and f.status<>'DEPRECATED' order by f.name,f.id",
          productVersionId, productId);
    } else {
      features = jdbc.queryForList(
          "select f.id feature_id,f.code feature_code,f.name feature_name,"
              + "f.outline_link_id,'ALL' availability from product_feature f "
              + "where f.product_id=? and f.status<>'DEPRECATED' order by f.name,f.id",
          productId);
    }
    if (features.isEmpty()) {
      warnings.add("关联产品没有可用功能，请人工补充产品差异信息");
      context.append("（没有可用功能）\n");
    }
    for (Map<String, Object> feature : features) {
      long featureId = ((Number) feature.get("feature_id")).longValue();
      context.append("\n### [").append(text(feature.get("availability"))).append("] ")
          .append(text(feature.get("feature_code"))).append(" · ")
          .append(text(feature.get("feature_name"))).append('\n');
      if (feature.get("outline_link_id") == null) {
        warnings.add("功能“" + text(feature.get("feature_name")) + "”尚未初始化设计 Spec");
        context.append("（设计 Spec 缺失）\n");
        continue;
      }
      try {
        DocumentView spec = documents.readBusinessDocument(organizationId,
            "PRODUCT:" + productId + ":FEATURE:" + featureId + ":SPEC");
        context.append(spec.getMarkdown()).append('\n');
      } catch (RuntimeException unavailable) {
        warnings.add("功能“" + text(feature.get("feature_name")) + "”的设计 Spec 无法读取");
        context.append("（设计 Spec 暂时无法读取）\n");
      }
    }
    return context.toString();
  }

  private String systemPrompt(OpportunityDocumentDefinition definition) {
    return "你是企业项目售前分析助手。请严格依据提供的资料生成"
        + definition.getDisplayName() + "，不得虚构缺失事实。输出必须符合 JSON Schema。";
  }

  private String userPrompt(OpportunityDocumentDefinition definition,
      Map<String, Object> opportunity, String context, List<String> warnings) {
    StringBuilder value = new StringBuilder();
    value.append("请生成").append(definition.getDisplayName()).append("。\n")
        .append("客户：").append(text(opportunity.get("customerName"))).append("\n")
        .append("商机：").append(text(opportunity.get("title"))).append("\n\n")
        .append(context);
    if (!warnings.isEmpty()) {
      value.append("\n## 已知资料缺口\n");
      for (String warning : warnings) value.append("- ").append(warning).append('\n');
    }
    return value.toString();
  }

  private JsonNode responseSchema() {
    ObjectNode schema = json.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("title").put("type", "string");
    properties.putObject("markdown").put("type", "string");
    ArrayNode required = schema.putArray("required");
    required.add("title"); required.add("markdown");
    return schema;
  }

  private String requiredText(JsonNode generated, String field) {
    JsonNode value = generated == null ? null : generated.get(field);
    if (value == null || !value.isTextual() || blank(value.asText())) {
      throw new IllegalStateException("AI 返回缺少 " + field);
    }
    return value.asText();
  }

  private Template template(long organizationId, OpportunityDocumentDefinition definition) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select k.id,c.published_revision,c.published_title_snapshot,"
            + "c.published_markdown_snapshot from knowledge_item k "
            + "join document_template_config c on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.stage_code=? and c.requirement='REQUIRED' and c.enabled=true "
            + "and c.published_revision is not null and c.published_title_snapshot is not null "
            + "and c.published_markdown_snapshot is not null order by k.id",
        organizationId, definition.getTemplateScene());
    if (values.isEmpty()) {
      throw new ConflictException("请先发布并启用" + definition.getDisplayName() + "模版");
    }
    if (values.size() > 1) {
      throw new ConflictException(
          definition.getDisplayName() + "模版存在多个，请只保留一个启用版本");
    }
    Map<String, Object> value = values.get(0);
    return new Template(((Number) value.get("id")).longValue(),
        ((Number) value.get("published_revision")).longValue(),
        String.valueOf(value.get("published_title_snapshot")),
        String.valueOf(value.get("published_markdown_snapshot")));
  }

  private boolean isSubmitted(long organizationId, long opportunityId, String artifactType) {
    Integer count = jdbc.queryForObject(
        "select count(*) from opportunity_artifact where organization_id=? "
            + "and opportunity_id=? and artifact_type=? and outline_link_id is not null",
        Integer.class, organizationId, opportunityId, artifactType);
    return count != null && count > 0;
  }

  private void assertEditable(Map<String, Object> opportunity,
      OpportunityDocumentDefinition definition, long version) {
    if (((Number) opportunity.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    if (!"OPEN".equals(opportunity.get("status"))) {
      throw new ConflictException("终态商机不能继续推进");
    }
    if (!definition.getStage().name().equals(opportunity.get("stage"))) {
      throw new ConflictException(
          "只有" + stageName(definition.getStage()) + "阶段可以维护"
              + definition.getDisplayName());
    }
  }

  private String replaceKnown(String value, Map<String, Object> opportunity) {
    return value.replace("{{客户名称}}", text(opportunity.get("customerName")))
        .replace("{{商机名称}}", text(opportunity.get("title")))
        .replace("{{商机标题}}", text(opportunity.get("title")))
        .replace("{{产品名称}}", text(opportunity.get("productName")))
        .replace("{{产品版本}}", text(opportunity.get("productVersionName")));
  }

  private PreparedDocument prepared(DocumentView document, Template template,
      String status, String error, List<String> warnings) {
    return new PreparedDocument(document, template.id, template.revision,
        status, error, new ArrayList<String>(warnings));
  }

  private String businessKey(long opportunityId, String artifactType) {
    return "OPPORTUNITY:" + opportunityId + ":" + artifactType;
  }

  private String message(RuntimeException failure) {
    return blank(failure.getMessage()) ? "AI 生成失败，请稍后重试或人工填写"
        : failure.getMessage();
  }

  private Long number(Object value) {
    return value == null ? null : Long.valueOf(((Number) value).longValue());
  }

  private String text(Object value) { return value == null ? "" : String.valueOf(value); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
  private String stageName(OpportunityStage stage) {
    switch (stage) {
      case LEAD: return "线索";
      case OPPORTUNITY: return "商机评审";
      case POC: return "POC";
      case BIDDING: return "投标";
      case CONTRACT: return "合同";
      default: return stage.name();
    }
  }

  public static final class PreparedDocument {
    private final DocumentView document;
    private final long sourceTemplateId;
    private final long sourceTemplateRevision;
    private final String generationStatus;
    private final String generationError;
    private final List<String> warnings;

    public PreparedDocument(DocumentView document, long sourceTemplateId,
        long sourceTemplateRevision, String generationStatus, String generationError,
        List<String> warnings) {
      this.document = document;
      this.sourceTemplateId = sourceTemplateId;
      this.sourceTemplateRevision = sourceTemplateRevision;
      this.generationStatus = generationStatus;
      this.generationError = generationError;
      this.warnings = Collections.unmodifiableList(warnings);
    }

    public DocumentView getDocument() { return document; }
    public long getSourceTemplateId() { return sourceTemplateId; }
    public long getSourceTemplateRevision() { return sourceTemplateRevision; }
    public String getGenerationStatus() { return generationStatus; }
    public String getGenerationError() { return generationError; }
    public List<String> getWarnings() { return warnings; }
  }

  public static final class SubmitResult {
    private final DocumentView document;
    private final Map<String, Object> opportunity;

    public SubmitResult(DocumentView document, Map<String, Object> opportunity) {
      this.document = document;
      this.opportunity = new LinkedHashMap<String, Object>(opportunity);
    }

    public DocumentView getDocument() { return document; }
    public Map<String, Object> getOpportunity() {
      return Collections.unmodifiableMap(opportunity);
    }
  }

  private static final class Template {
    private final long id;
    private final long revision;
    private final String title;
    private final String markdown;

    private Template(long id, long revision, String title, String markdown) {
      this.id = id; this.revision = revision; this.title = title; this.markdown = markdown;
    }
  }
}
