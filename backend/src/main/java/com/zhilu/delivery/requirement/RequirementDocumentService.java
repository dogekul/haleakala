package com.zhilu.delivery.requirement;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
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

  public RequirementDocumentService(JdbcTemplate jdbc, DocumentCenterService documents) {
    this.jdbc = jdbc;
    this.documents = documents;
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

    long rootLinkId = documents.ensureIndex(
        organizationId, "PROJECT_ROOT", "项目文档", null);
    long projectLinkId = documents.ensureIndex(
        organizationId, "PROJECT:" + projectId,
        text(requirement, "project_code") + " " + text(requirement, "project_name"),
        Long.valueOf(rootLinkId));
    long folderLinkId = documents.ensureIndex(
        organizationId, "PROJECT:" + projectId + ":REQUIREMENTS", "需求文档",
        Long.valueOf(projectLinkId));

    String documentTitle = shorten(text(requirement, "requirement_code") + " · "
        + text(requirement, "title") + " · 需求调研报告", 240);
    String businessKey = "REQUIREMENT:" + requirementId + ":RESEARCH_REPORT";
    long linkId = documents.createDocument(
        organizationId, businessKey, "REQUIREMENT_RESEARCH", documentTitle,
        markdown(requirement, template), folderLinkId);
    jdbc.update("update requirement_item set outline_link_id=?,source_template_id=?,"
            + "source_template_revision=?,updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=?",
        linkId, number(template, "id"), number(template, "published_revision"),
        requirementId, organizationId);
  }

  private Map<String, Object> requirement(long requirementId, long actorUserId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select r.id,r.organization_id,r.project_id,r.requirement_code,r.title,"
            + "r.description,r.source,r.priority,p.code project_code,p.name project_name,"
            + "p.customer_name,u.display_name actor_name "
            + "from requirement_item r join delivery_project p on p.id=r.project_id "
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

  private String markdown(Map<String, Object> requirement, Map<String, Object> template) {
    String projectName = text(requirement, "project_name");
    String customerName = text(requirement, "customer_name");
    String actorName = text(requirement, "actor_name");
    String date = LocalDate.now().toString();
    String filled = text(template, "template_markdown")
        .replace("{{系统/项目名称}}", projectName)
        .replace("{{项目名称}}", projectName)
        .replace("{{单位名称}}", customerName)
        .replace("{{姓名}}", actorName)
        .replace("{{YYYY-MM-DD}}", date)
        .replace("{{现场访谈/线上会议/问卷/材料分析}}", text(requirement, "source"))
        .replace("{{简要说明当前业务背景、现有工作方式及启动本项目的原因。}}",
            text(requirement, "description"));
    filled = PLACEHOLDER.matcher(filled).replaceAll("待确认");
    return "# " + projectName + "需求调研报告\n\n"
        + "> 需求编号：" + text(requirement, "requirement_code") + "  \n"
        + "> 需求标题：" + text(requirement, "title") + "  \n"
        + "> 客户：" + customerName + "  \n"
        + "> 来源：" + text(requirement, "source") + "  \n"
        + "> 优先级：" + text(requirement, "priority") + "  \n"
        + "> 采集人：" + actorName + "  \n"
        + "> 采集日期：" + date + "\n\n"
        + "## 本次采集需求\n\n" + text(requirement, "description")
        + "\n\n---\n\n" + filled;
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
}
