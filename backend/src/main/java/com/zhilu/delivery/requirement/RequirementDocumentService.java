package com.zhilu.delivery.requirement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RequirementDocumentService {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}");

  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;
  private final AiClient ai;
  private final ObjectMapper json;

  public RequirementDocumentService(JdbcTemplate jdbc, DocumentCenterService documents,
      AiClient ai, ObjectMapper json) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.ai = ai;
    this.json = json;
  }

  public void attach(long requirementId, long actorUserId) {
    Map<String, Object> requirement = requirement(requirementId, actorUserId);
    long organizationId = number(requirement, "organization_id");
    long projectId = number(requirement, "project_id");
    List<Map<String, Object>> availableTemplates = templates(organizationId);
    if (availableTemplates.isEmpty()) {
      throw new ConflictException("请先在知识库发布并启用需求调研报告模版");
    }
    if (availableTemplates.size() > 1) {
      throw new ConflictException("需求调研报告模版只能启用一个");
    }
    Map<String, Object> template = availableTemplates.get(0);
    GeneratedReport report = generate(requirement, template);

    long rootLinkId = documents.ensureIndex(
        organizationId, "PROJECT_ROOT", "项目文档", null);
    long projectLinkId = documents.ensureIndex(
        organizationId, "PROJECT:" + projectId,
        text(requirement, "project_code") + " " + text(requirement, "project_name"),
        Long.valueOf(rootLinkId));
    long folderLinkId = documents.ensureIndex(
        organizationId, "PROJECT:" + projectId + ":REQUIREMENTS", "需求文档",
        Long.valueOf(projectLinkId));

    String businessKey = "REQUIREMENT:" + requirementId + ":RESEARCH_REPORT";
    long linkId = documents.createDocument(
        organizationId, businessKey, "REQUIREMENT_RESEARCH", report.title,
        report.markdown, folderLinkId);
    jdbc.update("update requirement_item set outline_link_id=?,source_template_id=?,"
            + "source_template_revision=?,updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=?",
        linkId, number(template, "id"), number(template, "published_revision"),
        requirementId, organizationId);
  }

  public void regenerate(long requirementId, long actorUserId) {
    Map<String, Object> requirement = requirement(requirementId, actorUserId);
    if (requirement.get("outline_link_id") == null) {
      attach(requirementId, actorUserId);
      return;
    }
    long organizationId = number(requirement, "organization_id");
    List<Map<String, Object>> availableTemplates = templates(organizationId);
    if (availableTemplates.isEmpty()) {
      throw new ConflictException("请先在知识库发布并启用需求调研报告模版");
    }
    if (availableTemplates.size() > 1) {
      throw new ConflictException("需求调研报告模版只能启用一个");
    }
    Map<String, Object> template = availableTemplates.get(0);
    GeneratedReport report = generate(requirement, template);
    long linkId = number(requirement, "outline_link_id");
    DocumentView current = documents.readLink(linkId, organizationId);
    documents.updateLink(linkId, organizationId, report.title, report.markdown,
        current.getRevision());
    jdbc.update("update requirement_item set source_template_id=?,source_template_revision=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and organization_id=?",
        number(template, "id"), number(template, "published_revision"),
        requirementId, organizationId);
  }

  public DocumentView read(long requirementId, long organizationId) {
    List<Long> linkIds = jdbc.queryForList(
        "select outline_link_id from requirement_item "
            + "where id=? and organization_id=? and outline_link_id is not null",
        Long.class, requirementId, organizationId);
    if (linkIds.isEmpty()) throw new NotFoundException("需求文档不存在");
    return documents.readLink(linkIds.get(0).longValue(), organizationId);
  }

  private Map<String, Object> requirement(long requirementId, long actorUserId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select r.id,r.organization_id,r.project_id,r.requirement_code,r.title,r.outline_link_id,"
            + "r.description,r.source,r.priority,p.code project_code,p.name project_name,"
            + "p.customer_name,pr.name product_name,pv.version_name product_version_name,"
            + "u.display_name actor_name "
            + "from requirement_item r join delivery_project p on p.id=r.project_id "
            + "left join product pr on pr.id=p.product_id "
            + "left join product_version pv on pv.id=p.product_version_id "
            + "join app_user u on u.id=? and u.organization_id=r.organization_id "
            + "where r.id=?",
        actorUserId, requirementId);
    if (values.isEmpty()) throw new NotFoundException("需求不存在");
    return values.get(0);
  }

  private List<Map<String, Object>> templates(long organizationId) {
    return jdbc.queryForList(
        "select k.id,c.published_revision,"
            + "coalesce(c.published_title_snapshot,k.title) template_title,"
            + "coalesce(c.published_markdown_snapshot,k.content_text) template_markdown "
            + "from knowledge_item k join document_template_config c "
            + "on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.stage_code='OPPORTUNITY_RESEARCH' and c.enabled=true "
            + "and c.published_revision is not null order by k.id",
        organizationId);
  }

  private GeneratedReport generate(
      Map<String, Object> requirement, Map<String, Object> template) {
    JsonNode generated = ai.completeJson(number(requirement, "organization_id"),
        systemPrompt(), userPrompt(requirement, template), responseSchema());
    if (generated == null || !generated.isObject() || generated.size() != 2
        || generated.get("title") == null || !generated.get("title").isTextual()
        || generated.get("markdown") == null || !generated.get("markdown").isTextual()) {
      throw incompatibleResponse();
    }
    String title = generated.get("title").asText().trim();
    String markdown = generated.get("markdown").asText().trim();
    if (title.isEmpty() || markdown.isEmpty() || PLACEHOLDER.matcher(markdown).find()) {
      throw incompatibleResponse();
    }
    return new GeneratedReport(shorten(title, 240), markdown);
  }

  private ObjectNode responseSchema() {
    ObjectNode schema = json.createObjectNode();
    schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("title").put("type", "string");
    properties.putObject("markdown").put("type", "string");
    schema.putArray("required").add("title").add("markdown");
    schema.put("additionalProperties", false);
    return schema;
  }

  private String systemPrompt() {
    return "你是资深企业需求分析师。请严格依据提供的模版和事实，生成一份可直接评审、"
        + "可追溯的完整需求调研报告。必须保留并补全模版结构，并覆盖业务背景、相关角色、"
        + "现状流程、问题痛点、建设目标、范围内外、详细需求、数据与接口、非功能要求、"
        + "安全合规、验收标准、风险依赖和待确认事项。不得虚构事实；资料不足处写“待确认”。"
        + "不要输出任何 {{占位符}}，只返回符合 JSON Schema 的对象。";
  }

  private String userPrompt(
      Map<String, Object> requirement, Map<String, Object> template) {
    return "请基于以下模版和采集事实生成需求调研报告。\n\n"
        + "## 已发布模版\n模版标题：" + text(template, "template_title")
        + "\n模版版本：" + number(template, "published_revision")
        + "\n\n" + text(template, "template_markdown")
        + "\n\n## 真实业务上下文\n"
        + "需求编号：" + text(requirement, "requirement_code") + "\n"
        + "需求标题：" + text(requirement, "title") + "\n"
        + "需求描述与验收线索：" + text(requirement, "description") + "\n"
        + "来源：" + text(requirement, "source") + "\n"
        + "优先级：" + text(requirement, "priority") + "\n"
        + "项目编号：" + text(requirement, "project_code") + "\n"
        + "项目名称：" + text(requirement, "project_name") + "\n"
        + "客户：" + text(requirement, "customer_name") + "\n"
        + "产品：" + text(requirement, "product_name") + "\n"
        + "产品版本：" + text(requirement, "product_version_name") + "\n"
        + "采集人：" + text(requirement, "actor_name") + "\n"
        + "采集日期：" + LocalDate.now().toString();
  }

  private AiServiceException incompatibleResponse() {
    return new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
  }

  private long number(Map<String, Object> value, String key) {
    return ((Number) value.get(key)).longValue();
  }

  private String text(Map<String, Object> value, String key) {
    Object current = value.get(key);
    return current == null || String.valueOf(current).trim().isEmpty()
        ? "待确认" : String.valueOf(current).trim();
  }

  private String shorten(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static final class GeneratedReport {
    private final String title;
    private final String markdown;

    private GeneratedReport(String title, String markdown) {
      this.title = title;
      this.markdown = markdown;
    }
  }
}
