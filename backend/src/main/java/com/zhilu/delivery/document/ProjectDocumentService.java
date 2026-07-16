package com.zhilu.delivery.document;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.DeliveryStage;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

@Service
public class ProjectDocumentService {
  private static final List<String> STAGE_TITLES = Arrays.asList(
      "01 项目启动", "02 需求采集", "03 二开实施", "04 上线切换",
      "05 试运行与移交", "06 标准化评估", "07 项目收尾");

  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;

  public ProjectDocumentService(JdbcTemplate jdbc, DocumentCenterService documents) {
    this.jdbc = jdbc;
    this.documents = documents;
  }

  public void snapshotTemplates(long projectId, long organizationId) {
    List<Map<String, Object>> projects = jdbc.queryForList(
        "select document_snapshot_at from delivery_project where id=? and organization_id=?",
        projectId, organizationId);
    if (projects.isEmpty()) throw new NotFoundException("项目不存在");
    if (projects.get(0).get("document_snapshot_at") != null) return;
    for (Map<String, Object> template : templates(organizationId)) {
      Snapshot snapshot = publishedSnapshot(template, organizationId);
      ensureProjectDocument(
          projectId, ((Number) template.get("id")).longValue(),
          String.valueOf(template.get("stage_code")),
          ((Number) template.get("published_revision")).longValue(),
          String.valueOf(template.get("requirement")),
          snapshot.title, snapshot.markdown);
    }
    jdbc.update("update delivery_project set document_snapshot_at=current_timestamp,"
            + "updated_at=current_timestamp where id=? and organization_id=? "
            + "and document_snapshot_at is null",
        projectId, organizationId);
  }

  public void initialize(long projectId) {
    Map<String, Object> project = project(projectId);
    long organizationId = ((Number) project.get("organization_id")).longValue();
    if (project.get("document_snapshot_at") == null) {
      snapshotTemplates(projectId, organizationId);
    }
    jdbc.update("update delivery_project set document_space_status='INITIALIZING',"
            + "document_space_error=null,updated_at=current_timestamp where id=?",
        projectId);

    long rootLinkId = documents.ensureIndex(
        organizationId, "PROJECT_ROOT", "项目文档", null);
    long projectLinkId = documents.ensureIndex(
        organizationId, "PROJECT:" + projectId,
        project.get("code") + " " + project.get("name"), Long.valueOf(rootLinkId));
    Map<String, Long> stages = new LinkedHashMap<String, Long>();
    DeliveryStage[] values = DeliveryStage.values();
    for (int index = 0; index < values.length; index++) {
      DeliveryStage stage = values[index];
      long stageLinkId = documents.ensureIndex(
          organizationId, "PROJECT:" + projectId + ":STAGE:" + stage.name(),
          STAGE_TITLES.get(index), Long.valueOf(projectLinkId));
      stages.put(stage.name(), Long.valueOf(stageLinkId));
    }

    for (Map<String, Object> template : snapshots(projectId)) {
      copyTemplate(projectId, organizationId, template, stages);
    }
    jdbc.update("update delivery_project set document_space_status='READY',"
            + "document_space_error=null,updated_at=current_timestamp where id=?",
        projectId);
  }

  public void markFailure(long projectId, RuntimeException failure) {
    jdbc.update("update delivery_project set document_space_status='FAILED',"
            + "document_space_error=?,updated_at=current_timestamp where id=?",
        truncate(failure.getMessage()), projectId);
  }

  public List<Map<String, Object>> list(long projectId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    long organizationId = ((Number) project(projectId).get("organization_id")).longValue();
    List<Long> ids = jdbc.queryForList(
        "select id from project_document where project_id=? "
            + "order by stage_code,id",
        Long.class, projectId);
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Long id : ids) result.add(refresh(id.longValue(), projectId, organizationId));
    return result;
  }

  public Map<String, Object> confirm(
      long projectId, long projectDocumentId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    Map<String, Object> project = project(projectId);
    long managerUserId = ((Number) project.get("manager_user_id")).longValue();
    if (user.getId().longValue() != managerUserId
        && !user.getPermissions().contains("system:manage")) {
      throw new NotFoundException("项目文档不存在或无权确认");
    }
    long organizationId = ((Number) project.get("organization_id")).longValue();
    Map<String, Object> current = refresh(
        projectDocumentId, projectId, organizationId);
    if ("COMPLETED".equals(current.get("status"))) return current;
    if (!"PENDING_CONFIRMATION".equals(current.get("status"))) {
      throw new ConflictException("项目文档尚未填写完成，不能确认");
    }
    long revision = ((Number) current.get("revision")).longValue();
    jdbc.update("update project_document set status='COMPLETED',confirmed_revision=?,"
            + "confirmed_by=?,confirmed_at=current_timestamp,last_error=null,"
            + "updated_at=current_timestamp,version=version+1 where id=? and project_id=?",
        revision, user.getId(), projectDocumentId, projectId);
    return refresh(projectDocumentId, projectId, organizationId);
  }

  public List<Map<String, Object>> incompleteRequired(
      long projectId, DeliveryStage stage) {
    Map<String, Object> project = project(projectId);
    long organizationId = ((Number) project.get("organization_id")).longValue();
    List<Long> ids = jdbc.queryForList(
        "select id from project_document where project_id=? and stage_code=? "
            + "and requirement='REQUIRED' order by id",
        Long.class, projectId, stage.name());
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Long id : ids) {
      Map<String, Object> item = refresh(id.longValue(), projectId, organizationId);
      if (!"COMPLETED".equals(item.get("status"))) result.add(item);
    }
    return result;
  }

  private void copyTemplate(
      long projectId, long organizationId, Map<String, Object> template,
      Map<String, Long> stages) {
    long templateId = ((Number) template.get("id")).longValue();
    String stageCode = String.valueOf(template.get("stage_code"));
    Long stageLinkId = stages.get(stageCode);
    if (stageLinkId == null) {
      throw new ConflictException("文档模版阶段不存在：" + stageCode);
    }
    long projectDocumentId = ((Number) template.get("project_document_id")).longValue();
    Snapshot snapshot = projectSnapshot(template, organizationId, projectDocumentId);
    String businessKey = "PROJECT:" + projectId + ":DOC:" + templateId;
    try {
      long linkId = documents.createDocument(
          organizationId, businessKey, "PROJECT_DOCUMENT",
          snapshot.title, snapshot.markdown, stageLinkId.longValue());
      jdbc.update("update project_document set outline_link_id=?,status='PENDING',"
              + "last_synced_at=current_timestamp,last_error=null,updated_at=current_timestamp,"
              + "version=version+1 where id=?",
          linkId, projectDocumentId);
    } catch (RuntimeException failure) {
      Long linkId = documents.findLinkId(organizationId, businessKey);
      jdbc.update("update project_document set outline_link_id=?,status='FAILED',last_error=?,"
              + "updated_at=current_timestamp,version=version+1 where id=?",
          linkId, truncate(failure.getMessage()), projectDocumentId);
      throw failure;
    }
  }

  private long ensureProjectDocument(
      long projectId, long templateId, String stageCode, long revision, String requirement,
      String titleSnapshot, String markdownSnapshot) {
    List<Long> existing = jdbc.queryForList(
        "select id from project_document where project_id=? and source_template_id=?",
        Long.class, projectId, templateId);
    if (!existing.isEmpty()) return existing.get(0).longValue();
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("project_id", projectId);
    values.put("stage_code", stageCode);
    values.put("source_template_id", templateId);
    values.put("source_template_revision", revision);
    values.put("source_title_snapshot", titleSnapshot);
    values.put("source_markdown_snapshot", markdownSnapshot);
    values.put("requirement", requirement);
    values.put("status", "PENDING");
    try {
      return new SimpleJdbcInsert(jdbc).withTableName("project_document")
          .usingColumns(values.keySet().toArray(new String[values.size()]))
          .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    } catch (DuplicateKeyException concurrentInsert) {
      return jdbc.queryForObject(
          "select id from project_document where project_id=? and source_template_id=?",
          Long.class, projectId, templateId);
    }
  }

  private Map<String, Object> project(long projectId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,organization_id,code,name,manager_user_id,document_snapshot_at "
            + "from delivery_project where id=?",
        projectId);
    if (values.isEmpty()) throw new NotFoundException("项目不存在");
    return values.get(0);
  }

  private List<Map<String, Object>> templates(long organizationId) {
    return jdbc.queryForList(
        "select k.id,c.stage_code,c.requirement,c.published_revision,"
            + "c.published_title_snapshot,c.published_markdown_snapshot,"
            + "k.outline_link_id,k.title "
            + "from knowledge_item k join document_template_config c on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.enabled=true and c.published_revision is not null order by k.id",
        organizationId);
  }

  private List<Map<String, Object>> snapshots(long projectId) {
    return jdbc.queryForList(
        "select pd.id project_document_id,pd.source_template_id id,"
            + "pd.source_template_revision published_revision,pd.stage_code,pd.requirement,"
            + "pd.source_title_snapshot,pd.source_markdown_snapshot,pd.outline_link_id,"
            + "project_link.outline_document_id project_outline_document_id,"
            + "k.outline_link_id source_outline_link_id,k.title "
            + "from project_document pd join knowledge_item k on k.id=pd.source_template_id "
            + "left join outline_document_link project_link on project_link.id=pd.outline_link_id "
            + "where pd.project_id=? order by pd.id",
        projectId);
  }

  private Snapshot publishedSnapshot(Map<String, Object> template, long organizationId) {
    if (template.get("published_title_snapshot") != null
        && template.get("published_markdown_snapshot") != null) {
      return new Snapshot(
          String.valueOf(template.get("published_title_snapshot")),
          String.valueOf(template.get("published_markdown_snapshot")));
    }
    long templateId = ((Number) template.get("id")).longValue();
    long revision = ((Number) template.get("published_revision")).longValue();
    Snapshot recovered = recoverSnapshot(
        template.get("outline_link_id"), organizationId, revision,
        "已发布文档模版“" + template.get("title")
            + "”缺少升级快照且 Outline 修订已变化，请重新发布模版");
    int changed = jdbc.update(
        "update document_template_config set published_title_snapshot=?,"
            + "published_markdown_snapshot=?,updated_at=current_timestamp,version=version+1 "
            + "where knowledge_item_id=? and published_revision=? "
            + "and (published_title_snapshot is null or published_markdown_snapshot is null)",
        recovered.title, recovered.markdown, templateId, revision);
    if (changed == 0) {
      Map<String, Object> current = jdbc.queryForMap(
          "select published_revision,published_title_snapshot,published_markdown_snapshot "
              + "from document_template_config where knowledge_item_id=?",
          templateId);
      if (((Number) current.get("published_revision")).longValue() != revision
          || current.get("published_title_snapshot") == null
          || current.get("published_markdown_snapshot") == null) {
        throw new ConflictException("文档模版发布状态已变化，请刷新后重试");
      }
      return new Snapshot(
          String.valueOf(current.get("published_title_snapshot")),
          String.valueOf(current.get("published_markdown_snapshot")));
    }
    return recovered;
  }

  private Snapshot projectSnapshot(
      Map<String, Object> template, long organizationId, long projectDocumentId) {
    if (template.get("source_title_snapshot") != null
        && template.get("source_markdown_snapshot") != null) {
      return new Snapshot(
          String.valueOf(template.get("source_title_snapshot")),
          String.valueOf(template.get("source_markdown_snapshot")));
    }
    if (template.get("project_outline_document_id") != null) {
      return new Snapshot(String.valueOf(template.get("title")), "");
    }
    long revision = ((Number) template.get("published_revision")).longValue();
    Snapshot recovered = recoverSnapshot(
        template.get("source_outline_link_id"), organizationId, revision,
        "项目文档模版“" + template.get("title")
            + "”缺少升级快照且 Outline 修订已变化，无法安全初始化；请重新创建项目");
    int changed = jdbc.update(
        "update project_document set source_title_snapshot=?,source_markdown_snapshot=?,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where id=? and source_template_revision=? "
            + "and (source_title_snapshot is null or source_markdown_snapshot is null)",
        recovered.title, recovered.markdown, projectDocumentId, revision);
    if (changed == 0) {
      Map<String, Object> current = jdbc.queryForMap(
          "select source_template_revision,source_title_snapshot,source_markdown_snapshot "
              + "from project_document where id=?",
          projectDocumentId);
      if (((Number) current.get("source_template_revision")).longValue() != revision
          || current.get("source_title_snapshot") == null
          || current.get("source_markdown_snapshot") == null) {
        throw new ConflictException("项目文档快照状态已变化，请重试");
      }
      return new Snapshot(
          String.valueOf(current.get("source_title_snapshot")),
          String.valueOf(current.get("source_markdown_snapshot")));
    }
    return recovered;
  }

  private Snapshot recoverSnapshot(
      Object linkValue, long organizationId, long expectedRevision, String conflictMessage) {
    if (linkValue == null) {
      throw new ConflictException(conflictMessage);
    }
    DocumentView current;
    try {
      current = documents.readLink(((Number) linkValue).longValue(), organizationId);
    } catch (ConflictException missingDocument) {
      throw new ConflictException(conflictMessage);
    }
    if (current.getRevision() != expectedRevision) {
      throw new ConflictException(conflictMessage);
    }
    return new Snapshot(current.getTitle(), current.getMarkdown());
  }

  private Map<String, Object> refresh(
      long projectDocumentId, long projectId, long organizationId) {
    for (int attempt = 0; attempt < 4; attempt++) {
      Map<String, Object> row = record(projectDocumentId, projectId);
      Object linkValue = row.get("outline_link_id");
      if (linkValue == null) {
        return view(row, null, row.get("status"), row.get("last_error"));
      }
      long expectedVersion = ((Number) row.get("version")).longValue();
      try {
        DocumentView document = documents.readLink(
            ((Number) linkValue).longValue(), organizationId);
        Long confirmedRevision = nullableLong(row.get("confirmed_revision"));
        String status = contentStatus(document.getTitle(), document.getMarkdown());
        boolean completed = "PENDING_CONFIRMATION".equals(status)
            && confirmedRevision != null
            && confirmedRevision.longValue() == document.getRevision();
        int changed;
        if (completed) {
          status = "COMPLETED";
          changed = jdbc.update(
              "update project_document set status='COMPLETED',"
                  + "last_synced_at=current_timestamp,last_error=null,"
                  + "updated_at=current_timestamp,version=version+1 "
                  + "where id=? and version=?",
              projectDocumentId, expectedVersion);
        } else {
          changed = jdbc.update(
              "update project_document set status=?,confirmed_revision=null,"
                  + "confirmed_by=null,confirmed_at=null,last_synced_at=current_timestamp,"
                  + "last_error=null,updated_at=current_timestamp,version=version+1 "
                  + "where id=? and version=?",
              status, projectDocumentId, expectedVersion);
        }
        if (changed == 0) continue;
        return view(record(projectDocumentId, projectId), document, status, null);
      } catch (OutlineException failure) {
        int changed = jdbc.update(
            "update project_document set status='FAILED',last_error=?,"
                + "updated_at=current_timestamp,version=version+1 "
                + "where id=? and version=?",
            truncate(failure.getMessage()), projectDocumentId, expectedVersion);
        if (changed == 0) continue;
        return view(record(projectDocumentId, projectId), null, "FAILED", failure.getMessage());
      } catch (ConflictException failure) {
        int changed = jdbc.update(
            "update project_document set status='FAILED',last_error=?,"
                + "updated_at=current_timestamp,version=version+1 "
                + "where id=? and version=?",
            truncate(failure.getMessage()), projectDocumentId, expectedVersion);
        if (changed == 0) continue;
        return view(record(projectDocumentId, projectId), null, "FAILED", failure.getMessage());
      }
    }
    throw new ConflictException("项目文档状态正在更新，请重试");
  }

  private Map<String, Object> record(long projectDocumentId, long projectId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select pd.*,k.title template_title,l.title_cache,l.revision cached_revision,"
            + "l.outline_url_id,u.display_name confirmed_by_name "
            + "from project_document pd join knowledge_item k on k.id=pd.source_template_id "
            + "left join outline_document_link l on l.id=pd.outline_link_id "
            + "left join app_user u on u.id=pd.confirmed_by "
            + "where pd.id=? and pd.project_id=?",
        projectDocumentId, projectId);
    if (values.isEmpty()) throw new NotFoundException("项目文档不存在");
    return values.get(0);
  }

  private Map<String, Object> view(
      Map<String, Object> row, DocumentView document, Object status, Object lastError) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", ((Number) row.get("id")).longValue());
    result.put("stageCode", row.get("stage_code"));
    result.put("title", document == null
        ? value(row.get("title_cache"), row.get("template_title")) : document.getTitle());
    result.put("requirement", row.get("requirement"));
    result.put("status", status);
    result.put("revision", document == null ? row.get("cached_revision") : document.getRevision());
    result.put("confirmedRevision", row.get("confirmed_revision"));
    result.put("confirmedBy", row.get("confirmed_by"));
    result.put("confirmedByName", row.get("confirmed_by_name"));
    result.put("confirmedAt", localDateTime(row.get("confirmed_at")));
    result.put("outlineUrl", document == null ? null : document.getOutlineUrl());
    result.put("lastError", lastError);
    result.put("lastSyncedAt", localDateTime(row.get("last_synced_at")));
    result.put("sourceTemplateId", row.get("source_template_id"));
    result.put("sourceTemplateRevision", row.get("source_template_revision"));
    return result;
  }

  private String contentStatus(String title, String markdown) {
    if (markdown == null || markdown.trim().isEmpty()) return "TODO";
    String expectedTitle = plain(title);
    for (String sourceLine : markdown.split("\\r?\\n")) {
      if (sourceLine.matches("^\\s{0,3}#{1,6}(\\s+.*)?$")) continue;
      String line = plain(sourceLine);
      if (line.isEmpty() || line.equals(expectedTitle) || hint(line)) continue;
      if (line.matches("^[-:|\\s]+$") || line.endsWith("：") || line.endsWith(":")) continue;
      return "PENDING_CONFIRMATION";
    }
    return "TODO";
  }

  private String plain(String value) {
    if (value == null) return "";
    return value.replaceAll("<!--.*?-->", "")
        .replaceFirst("^\\s{0,3}#{1,6}\\s*", "")
        .replaceFirst("^\\s*[-*+]\\s+", "")
        .replace("**", "").replace("__", "").replace("`", "").trim();
  }

  private boolean hint(String line) {
    String lower = line.toLowerCase();
    return lower.contains("请补充") || lower.contains("待补充")
        || lower.contains("请填写") || lower.contains("待填写")
        || lower.contains("在此填写") || lower.contains("todo") || lower.contains("tbd");
  }

  private void assertProjectAccess(long projectId, CurrentUser user) {
    Integer project = jdbc.queryForObject(
        "select count(*) from delivery_project where id=? and organization_id=?",
        Integer.class, projectId, user.getOrganizationId());
    if (project == null || project == 0) {
      throw new NotFoundException("项目不存在或无权访问");
    }
    if (user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO")
        || user.getPermissions().contains("system:manage")) return;
    Integer member = jdbc.queryForObject(
        "select count(*) from project_member where project_id=? and user_id=?",
        Integer.class, projectId, user.getId());
    if (member == null || member == 0) {
      throw new NotFoundException("项目不存在或无权访问");
    }
  }

  private Object value(Object preferred, Object fallback) {
    return preferred == null ? fallback : preferred;
  }

  private Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private LocalDateTime localDateTime(Object value) {
    return value == null ? null : ((Timestamp) value).toLocalDateTime();
  }

  private String truncate(String message) {
    if (message == null) return "项目文档空间初始化失败";
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  private static final class Snapshot {
    private final String title;
    private final String markdown;

    private Snapshot(String title, String markdown) {
      this.title = title;
      this.markdown = markdown;
    }
  }
}
