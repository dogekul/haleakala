package com.zhilu.delivery.document;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.project.DeliveryStage;
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

  public void initialize(long projectId) {
    Map<String, Object> project = project(projectId);
    long organizationId = ((Number) project.get("organization_id")).longValue();
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

    for (Map<String, Object> template : templates(organizationId)) {
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

  private void copyTemplate(
      long projectId, long organizationId, Map<String, Object> template,
      Map<String, Long> stages) {
    long templateId = ((Number) template.get("id")).longValue();
    long sourceLinkId = ((Number) template.get("outline_link_id")).longValue();
    long publishedRevision = ((Number) template.get("published_revision")).longValue();
    String stageCode = String.valueOf(template.get("stage_code"));
    Long stageLinkId = stages.get(stageCode);
    if (stageLinkId == null) {
      throw new ConflictException("文档模版阶段不存在：" + stageCode);
    }
    DocumentView source = documents.readLink(sourceLinkId, organizationId);
    if (source.getRevision() != publishedRevision) {
      throw new ConflictException("文档模版已在 Outline 更新，请重新发布：" + source.getTitle());
    }
    long projectDocumentId = ensureProjectDocument(
        projectId, templateId, stageCode, publishedRevision,
        String.valueOf(template.get("requirement")));
    String businessKey = "PROJECT:" + projectId + ":DOC:" + templateId;
    try {
      long linkId = documents.createDocument(
          organizationId, businessKey, "PROJECT_DOCUMENT",
          source.getTitle(), source.getMarkdown(), stageLinkId.longValue());
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
      long projectId, long templateId, String stageCode, long revision, String requirement) {
    List<Long> existing = jdbc.queryForList(
        "select id from project_document where project_id=? and source_template_id=?",
        Long.class, projectId, templateId);
    if (!existing.isEmpty()) return existing.get(0).longValue();
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("project_id", projectId);
    values.put("stage_code", stageCode);
    values.put("source_template_id", templateId);
    values.put("source_template_revision", revision);
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
        "select id,organization_id,code,name from delivery_project where id=?",
        projectId);
    if (values.isEmpty()) throw new NotFoundException("项目不存在");
    return values.get(0);
  }

  private List<Map<String, Object>> templates(long organizationId) {
    return jdbc.queryForList(
        "select k.id,k.outline_link_id,c.stage_code,c.requirement,c.published_revision "
            + "from knowledge_item k join document_template_config c on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.enabled=true and c.published_revision is not null "
            + "and k.outline_link_id is not null order by k.id",
        organizationId);
  }

  private String truncate(String message) {
    if (message == null) return "项目文档空间初始化失败";
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
