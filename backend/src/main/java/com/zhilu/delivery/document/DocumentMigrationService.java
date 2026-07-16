package com.zhilu.delivery.document;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

@Service
public class DocumentMigrationService {
  public static final String KNOWLEDGE_MIGRATION = "KNOWLEDGE_MIGRATION";
  public static final String PROJECT_MIGRATION = "PROJECT_MIGRATION";

  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;
  private final OutlineClient outline;
  private final OutlineProperties properties;

  public DocumentMigrationService(
      JdbcTemplate jdbc, DocumentCenterService documents, OutlineClient outline,
      OutlineProperties properties) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.outline = outline;
    this.properties = properties;
  }

  public Map<String, Object> initialize(long organizationId) {
    long knowledgeRoot = documents.ensureIndex(
        organizationId, "KNOWLEDGE_ROOT", "知识库", null);
    long projectRoot = documents.ensureIndex(
        organizationId, "PROJECT_ROOT", "项目文档", null);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("status", "READY");
    result.put("knowledgeRootLinkId", knowledgeRoot);
    result.put("projectRootLinkId", projectRoot);
    return result;
  }

  public Map<String, Object> startKnowledgeMigration(long organizationId) {
    List<Long> ids = jdbc.queryForList(
        "select id from knowledge_item where organization_id=? and outline_link_id is null "
            + "order by id",
        Long.class, organizationId);
    int enqueued = 0;
    for (Long id : ids) {
      if (enqueue(
          organizationId, KNOWLEDGE_MIGRATION, "KNOWLEDGE:" + id, id)) enqueued++;
    }
    return enqueued(enqueued);
  }

  public Map<String, Object> startProjectMigration(long organizationId) {
    List<Long> ids = jdbc.queryForList(
        "select id from delivery_project where organization_id=? order by id",
        Long.class, organizationId);
    int enqueued = 0;
    for (Long id : ids) {
      if (enqueue(
          organizationId, PROJECT_MIGRATION, "PROJECT:" + id, id)) enqueued++;
    }
    return enqueued(enqueued);
  }

  public void migrateKnowledge(long organizationId, long knowledgeId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,type,title,content_text,outline_link_id from knowledge_item "
            + "where id=? and organization_id=?",
        knowledgeId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("知识条目不存在");
    Map<String, Object> item = values.get(0);
    if (item.get("outline_link_id") != null) return;
    String type = String.valueOf(item.get("type"));
    long root = documents.ensureIndex(organizationId, "KNOWLEDGE_ROOT", "知识库", null);
    long typeRoot = documents.ensureIndex(
        organizationId, "KNOWLEDGE_TYPE:" + type, typeName(type), Long.valueOf(root));
    long linkId = documents.createDocument(
        organizationId, "KNOWLEDGE:" + knowledgeId,
        "TEMPLATE".equals(type) ? "KNOWLEDGE_TEMPLATE" : "KNOWLEDGE_DOCUMENT",
        String.valueOf(item.get("title")), string(item.get("content_text")), typeRoot);
    jdbc.update(
        "update knowledge_item set outline_link_id=?,updated_at=current_timestamp,"
            + "version=version+1 where id=? and organization_id=? and outline_link_id is null",
        linkId, knowledgeId, organizationId);
  }

  public Map<String, Object> status(long organizationId) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    String integrationStatus;
    String connectionError = null;
    if (!outline.isConfigured()) {
      integrationStatus = "NOT_CONFIGURED";
    } else {
      try {
        outline.children(null);
        integrationStatus = "READY";
      } catch (OutlineException failure) {
        integrationStatus = "FAILED";
        connectionError = failure.getMessage();
      }
    }
    result.put("integrationStatus", integrationStatus);
    result.put("collectionId", properties.getCollectionId());
    result.put("knowledgeRoot", rootStatus(organizationId, "KNOWLEDGE_ROOT"));
    result.put("projectRoot", rootStatus(organizationId, "PROJECT_ROOT"));
    result.put("jobs", jobCounts(organizationId));
    result.put("recentError", recentError(organizationId, connectionError));
    return result;
  }

  public Map<String, Object> retry(long organizationId, long jobId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,job_type,business_id,status from document_job "
            + "where id=? and organization_id=?",
        jobId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("文档任务不存在");
    Map<String, Object> job = values.get(0);
    int changed = jdbc.update(
        "update document_job set status='PENDING',attempt_count=0,"
            + "next_attempt_at=current_timestamp,last_error=null,started_at=null,"
            + "completed_at=null,updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and status in ('FAILED','RETRY')",
        jobId, organizationId);
    if (changed == 0 && !"PENDING".equals(job.get("status"))) {
      throw new ConflictException("当前任务状态不能重试");
    }
    if (PROJECT_MIGRATION.equals(job.get("job_type"))
        || DocumentJobService.PROJECT_INIT.equals(job.get("job_type"))) {
      jdbc.update("update delivery_project set document_space_status='PENDING',"
              + "document_space_error=null,updated_at=current_timestamp "
              + "where id=? and organization_id=?",
          job.get("business_id"), organizationId);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", jobId);
    result.put("status", "PENDING");
    return result;
  }

  private boolean enqueue(
      long organizationId, String jobType, String businessKey, long businessId) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("job_type", jobType);
    values.put("business_key", businessKey);
    values.put("business_id", businessId);
    values.put("status", "PENDING");
    try {
      new SimpleJdbcInsert(jdbc).withTableName("document_job")
          .usingColumns(values.keySet().toArray(new String[values.size()]))
          .execute(values);
      return true;
    } catch (DuplicateKeyException existing) {
      return false;
    }
  }

  private Map<String, Object> enqueued(int count) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("enqueued", count);
    return result;
  }

  private Map<String, Object> rootStatus(long organizationId, String businessKey) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,sync_status,last_error from outline_document_link "
            + "where organization_id=? and business_key=?",
        organizationId, businessKey);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    if (values.isEmpty()) {
      result.put("status", "PENDING");
      return result;
    }
    Map<String, Object> value = values.get(0);
    result.put("linkId", value.get("id"));
    result.put("status", value.get("sync_status"));
    result.put("lastError", value.get("last_error"));
    return result;
  }

  private Map<String, Object> jobCounts(long organizationId) {
    Map<String, Object> counts = new LinkedHashMap<String, Object>();
    counts.put("pending", count(
        organizationId, "status in ('PENDING','RETRY')"));
    counts.put("running", count(organizationId, "status='RUNNING'"));
    counts.put("success", count(organizationId, "status='DONE'"));
    counts.put("failed", count(organizationId, "status='FAILED'"));
    return counts;
  }

  private long count(long organizationId, String condition) {
    Long count = jdbc.queryForObject(
        "select count(*) from document_job where organization_id=? and " + condition,
        Long.class, organizationId);
    return count == null ? 0 : count.longValue();
  }

  private String recentError(long organizationId, String connectionError) {
    if (connectionError != null) return connectionError;
    List<String> values = jdbc.queryForList(
        "select last_error from document_job where organization_id=? and last_error is not null "
            + "order by updated_at desc,id desc limit 1",
        String.class, organizationId);
    return values.isEmpty() ? null : values.get(0);
  }

  private String typeName(String type) {
    if ("CASE".equals(type)) return "实施案例";
    if ("CODE".equals(type)) return "代码片段";
    if ("TRAINING".equals(type)) return "培训材料";
    if ("TEMPLATE".equals(type)) return "文档模版";
    return "其他文档";
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
