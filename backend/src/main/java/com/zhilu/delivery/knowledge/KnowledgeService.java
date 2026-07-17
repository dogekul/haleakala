package com.zhilu.delivery.knowledge;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.document.OutlineConfigurationService;
import com.zhilu.delivery.document.OutlineConnection;
import com.zhilu.delivery.document.OutlineException;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.DeliveryStage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
  private static final List<String> TYPES =
      Arrays.asList("CASE", "CODE", "TRAINING", "TEMPLATE");
  private final JdbcTemplate jdbc;
  private final SimpleJdbcInsert insert;
  private final DocumentCenterService documents;
  private final OutlineConfigurationService configurations;

  public KnowledgeService(
      JdbcTemplate jdbc, DocumentCenterService documents,
      OutlineConfigurationService configurations) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.configurations = configurations;
    this.insert = new SimpleJdbcInsert(jdbc).withTableName("knowledge_item")
        .usingColumns("organization_id", "type", "title", "summary", "content_text",
            "tags_text", "product_id", "product_version_id", "visibility", "owner_user_id")
        .usingGeneratedKeyColumns("id");
  }

  public List<Map<String, Object>> search(
      CurrentUser user, String keyword, String type, String tag, boolean publishedOnly) {
    OutlineConnection connection = configurations.resolve(user.getOrganizationId());
    StringBuilder sql = new StringBuilder(
        "select k.*,p.name product_name,v.version_name,u.display_name owner_name,"
            + "c.language,c.code_text,c.usage_notes,t.audience,t.duration_minutes,t.file_object_id,"
            + "f.original_name file_original_name,f.file_version,f.size_bytes file_size_bytes,"
            + "d.id outline_link_id,coalesce(d.title_cache,k.title) display_title,"
            + "d.sync_status document_sync_status,d.revision document_revision,"
            + "d.last_error document_error,d.outline_url_id,tc.stage_code,tc.requirement,"
            + "tc.enabled,tc.published_revision "
            + "from knowledge_item k left join product p on p.id=k.product_id "
            + "left join product_version v on v.id=k.product_version_id "
            + "join app_user u on u.id=k.owner_user_id "
            + "left join code_snippet c on c.knowledge_item_id=k.id "
            + "left join training_material t on t.knowledge_item_id=k.id "
            + "left join file_object f on f.id=t.file_object_id and f.organization_id=k.organization_id "
            + "left join outline_document_link d on d.id=k.outline_link_id "
            + "left join document_template_config tc on tc.knowledge_item_id=k.id "
            + "where k.organization_id=?");
    List<Object> args = new ArrayList<Object>();
    args.add(user.getOrganizationId());
    if (publishedOnly) {
      sql.append(" and k.status='PUBLISHED'");
    } else if (!user.getPermissions().contains("system:manage")) {
      sql.append(" and (k.status='PUBLISHED' or k.owner_user_id=?)");
      args.add(user.getId());
    }
    if (!blank(type)) { sql.append(" and k.type=?"); args.add(type); }
    if (!blank(keyword)) {
      sql.append(" and (lower(coalesce(d.title_cache,k.title)) like ? or lower(k.summary) like ? "
          + "or lower(coalesce(d.summary_cache,k.content_text)) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase() + "%";
      args.add(pattern); args.add(pattern); args.add(pattern);
    }
    if (!blank(tag)) { sql.append(" and lower(k.tags_text) like ?"); args.add("%" + tag.trim().toLowerCase() + "%"); }
    sql.append(" order by case k.status when 'PUBLISHED' then 1 else 2 end,k.updated_at desc,k.id desc");
    return jdbc.query(
        sql.toString(), (row, index) -> detail(row, connection), args.toArray());
  }

  public Map<String, Object> get(long id, CurrentUser user) {
    OutlineConnection connection = configurations.resolve(user.getOrganizationId());
    List<Map<String, Object>> values = jdbc.query(
        "select k.*,p.name product_name,v.version_name,u.display_name owner_name,"
            + "c.language,c.code_text,c.usage_notes,t.audience,t.duration_minutes,t.file_object_id,"
            + "f.original_name file_original_name,f.file_version,f.size_bytes file_size_bytes,"
            + "d.id outline_link_id,coalesce(d.title_cache,k.title) display_title,"
            + "d.sync_status document_sync_status,d.revision document_revision,"
            + "d.last_error document_error,d.outline_url_id,tc.stage_code,tc.requirement,"
            + "tc.enabled,tc.published_revision "
            + "from knowledge_item k left join product p on p.id=k.product_id "
            + "left join product_version v on v.id=k.product_version_id join app_user u on u.id=k.owner_user_id "
            + "left join code_snippet c on c.knowledge_item_id=k.id "
            + "left join training_material t on t.knowledge_item_id=k.id "
            + "left join file_object f on f.id=t.file_object_id and f.organization_id=k.organization_id "
            + "left join outline_document_link d on d.id=k.outline_link_id "
            + "left join document_template_config tc on tc.knowledge_item_id=k.id "
            + "where k.id=? and k.organization_id=?",
        (row, index) -> detail(row, connection), id, user.getOrganizationId());
    if (values.isEmpty()) throw new NotFoundException("知识条目不存在");
    Map<String, Object> value = values.get(0);
    if (!"PUBLISHED".equals(value.get("status"))
        && ((Number) value.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) {
      throw new NotFoundException("知识条目不存在");
    }
    return value;
  }

  @Transactional
  public Map<String, Object> create(
      CurrentUser user, String type, String title, String summary, String content,
      String tags, Long productId, Long productVersionId, String visibility,
      String language, String codeText, String audienceOrUsageNotes, Integer durationMinutes,
      Long fileObjectId) {
    return create(
        user, type, title, summary, content, tags, productId, productVersionId, visibility,
        language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId,
        null, null, null);
  }

  @Transactional
  public Map<String, Object> create(
      CurrentUser user, String type, String title, String summary, String content,
      String tags, Long productId, Long productVersionId, String visibility,
      String language, String codeText, String audienceOrUsageNotes, Integer durationMinutes,
      Long fileObjectId, String stageCode, String requirement, Boolean enabled) {
    validate(type, title, summary, content);
    validateReferences(user.getOrganizationId(), type, productId, productVersionId, fileObjectId);
    validateTemplate(type, stageCode, requirement);
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", user.getOrganizationId()); values.put("type", type);
    values.put("title", title); values.put("summary", summary); values.put("content_text", content);
    values.put("tags_text", tags); values.put("product_id", productId);
    values.put("product_version_id", productVersionId);
    values.put("visibility", blank(visibility) ? "ORGANIZATION" : visibility);
    values.put("owner_user_id", user.getId());
    long id = insert.executeAndReturnKey(values).longValue();
    saveDetail(id, type, language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId);
    saveTemplate(id, type, stageCode, requirement, enabled);
    initializeDocument(user.getOrganizationId(), id, type, title, content);
    return get(id, user);
  }

  @Transactional
  public Map<String, Object> update(
      long id, CurrentUser user, String type, String title, String summary, String content,
      String tags, Long productId, Long productVersionId, String visibility,
      String language, String codeText, String audienceOrUsageNotes, Integer durationMinutes,
      Long fileObjectId, long version) {
    Map<String, Object> current = get(id, user);
    return update(
        id, user, type, title, summary, content, tags, productId, productVersionId, visibility,
        language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId, version,
        number(current.get("documentRevision")), string(current.get("stageCode")),
        string(current.get("requirement")), booleanValue(current.get("enabled")));
  }

  @Transactional
  public Map<String, Object> update(
      long id, CurrentUser user, String type, String title, String summary, String content,
      String tags, Long productId, Long productVersionId, String visibility,
      String language, String codeText, String audienceOrUsageNotes, Integer durationMinutes,
      Long fileObjectId, long version, Long documentRevision, String stageCode,
      String requirement, Boolean enabled) {
    Map<String, Object> current = get(id, user);
    if (((Number) current.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) throw new ConflictException("只能维护自己的知识条目");
    validateMetadata(type, title, summary);
    if (content != null && blank(content)) {
      throw new IllegalArgumentException("正文不能为空");
    }
    validateReferences(user.getOrganizationId(), type, productId, productVersionId, fileObjectId);
    validateTemplate(type, stageCode, requirement);
    int changed = content == null
        ? jdbc.update("update knowledge_item set type=?,title=?,summary=?,tags_text=?,product_id=?,"
                + "product_version_id=?,visibility=?,status='DRAFT',published_at=null,"
                + "updated_at=current_timestamp,version=version+1 where id=? and version=?",
            type,title,summary,tags,productId,productVersionId,
            blank(visibility)?"ORGANIZATION":visibility,id,version)
        : jdbc.update("update knowledge_item set type=?,title=?,summary=?,content_text=?,tags_text=?,"
                + "product_id=?,product_version_id=?,visibility=?,status='DRAFT',published_at=null,"
                + "updated_at=current_timestamp,version=version+1 where id=? and version=?",
            type,title,summary,content,tags,productId,productVersionId,
            blank(visibility)?"ORGANIZATION":visibility,id,version);
    if (changed == 0) throw new ConflictException("知识条目已被更新，请刷新后重试");
    jdbc.update("delete from code_snippet where knowledge_item_id=?", id);
    jdbc.update("delete from training_material where knowledge_item_id=?", id);
    jdbc.update("delete from document_template_config where knowledge_item_id=?", id);
    saveDetail(id, type, language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId);
    saveTemplate(id, type, stageCode, requirement, enabled);
    if (current.get("outlineLinkId") == null) {
      initializeDocument(
          user.getOrganizationId(), id, type, title,
          content == null ? string(current.get("content")) : content);
    } else {
      if (content == null) {
        DocumentView currentDocument = documents.readKnowledge(id, user);
        documents.updateKnowledge(
            id, title, currentDocument.getMarkdown(), currentDocument.getRevision(), user);
      } else {
        long revision = documentRevision == null
            ? number(current.get("documentRevision")) : documentRevision.longValue();
        documents.updateKnowledge(id, title, content, revision, user);
      }
    }
    return get(id, user);
  }

  @Transactional
  public Map<String, Object> publish(long id, CurrentUser user) {
    Map<String, Object> value = get(id, user);
    if (((Number) value.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) throw new ConflictException("只有条目负责人可发布");
    if ("CODE".equals(value.get("type")) && blank(String.valueOf(value.get("codeText"))))
      throw new ConflictException("代码片段为空，不能发布");
    DocumentView document = documents.readKnowledge(id, user);
    if (blank(document.getMarkdown())) throw new ConflictException("知识正文为空，不能发布");
    jdbc.update("update knowledge_item set status='PUBLISHED',published_at=current_timestamp,updated_at=current_timestamp,version=version+1 where id=?", id);
    if ("TEMPLATE".equals(value.get("type"))) {
      jdbc.update("update document_template_config set published_revision=?,"
              + "published_title_snapshot=?,published_markdown_snapshot=?,"
              + "updated_at=current_timestamp,version=version+1 where knowledge_item_id=?",
          document.getRevision(), document.getTitle(), document.getMarkdown(), id);
    }
    return get(id, user);
  }

  @Transactional
  public DocumentView updateDocument(
      long id, CurrentUser user, String title, String markdown, long revision) {
    Map<String, Object> current = get(id, user);
    if (((Number) current.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) {
      throw new ConflictException("只能维护自己的知识条目");
    }
    DocumentView updated = documents.updateKnowledge(id, title, markdown, revision, user);
    jdbc.update("update knowledge_item set title=?,status='DRAFT',published_at=null,"
            + "updated_at=current_timestamp,version=version+1 where id=? and organization_id=?",
        title, id, user.getOrganizationId());
    jdbc.update("update document_template_config set published_revision=null,"
            + "published_title_snapshot=null,published_markdown_snapshot=null,"
            + "updated_at=current_timestamp,version=version+1 where knowledge_item_id=?",
        id);
    return updated;
  }

  @Transactional
  public Map<String, Object> retryDocument(long id, CurrentUser user) {
    Map<String, Object> current = get(id, user);
    if (((Number) current.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) {
      throw new ConflictException("只能维护自己的知识条目");
    }
    initializeDocument(
        user.getOrganizationId(), id, String.valueOf(current.get("type")),
        String.valueOf(current.get("title")), String.valueOf(current.get("content")));
    return get(id, user);
  }

  private void initializeDocument(
      long organizationId, long id, String type, String title, String content) {
    String businessKey = "KNOWLEDGE:" + id;
    try {
      long root = documents.ensureIndex(organizationId, "KNOWLEDGE_ROOT", "知识库", null);
      long typeRoot = documents.ensureIndex(
          organizationId, "KNOWLEDGE_TYPE:" + type, typeName(type), Long.valueOf(root));
      long linkId = documents.createDocument(
          organizationId, businessKey,
          "TEMPLATE".equals(type) ? "KNOWLEDGE_TEMPLATE" : "KNOWLEDGE_DOCUMENT",
          title, content, typeRoot);
      jdbc.update("update knowledge_item set outline_link_id=? where id=?", linkId, id);
    } catch (OutlineException unavailable) {
      Long linkId = documents.findLinkId(organizationId, businessKey);
      if (linkId != null) {
        jdbc.update("update knowledge_item set outline_link_id=? where id=?", linkId, id);
      }
    }
  }

  private void saveDetail(long id, String type, String language, String codeText,
      String audienceOrUsageNotes, Integer durationMinutes, Long fileObjectId) {
    if ("CODE".equals(type)) {
      if (blank(language) || blank(codeText)) throw new IllegalArgumentException("代码语言和片段内容不能为空");
      jdbc.update("insert into code_snippet(knowledge_item_id,language,code_text,usage_notes) values (?,?,?,?)",
          id, language, codeText, audienceOrUsageNotes);
    } else if ("TRAINING".equals(type)) {
      if (blank(audienceOrUsageNotes)) throw new IllegalArgumentException("培训对象不能为空");
      jdbc.update("insert into training_material(knowledge_item_id,audience,duration_minutes,file_object_id) values (?,?,?,?)",
          id, audienceOrUsageNotes, durationMinutes == null ? 0 : durationMinutes, fileObjectId);
    }
  }

  private void saveTemplate(
      long id, String type, String stageCode, String requirement, Boolean enabled) {
    if (!"TEMPLATE".equals(type)) return;
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
            + "enabled) values (?,?,?,?)",
        id, stageCode, requirement, enabled == null || enabled.booleanValue());
  }

  private void validate(String type, String title, String summary, String content) {
    validateMetadata(type, title, summary);
    if (blank(content)) throw new IllegalArgumentException("正文不能为空");
  }

  private void validateMetadata(String type, String title, String summary) {
    if (!TYPES.contains(type)) throw new IllegalArgumentException("知识类型不受支持");
    if (blank(title) || blank(summary)) throw new IllegalArgumentException("标题和摘要不能为空");
  }

  private void validateReferences(long organizationId, String type, Long productId,
      Long productVersionId, Long fileObjectId) {
    if (productId != null || productVersionId != null) {
      Integer products = productVersionId == null
          ? jdbc.queryForObject(
              "select count(*) from product where id=? and organization_id=?",
              Integer.class, productId, organizationId)
          : jdbc.queryForObject(
              "select count(*) from product_version v join product p on p.id=v.product_id "
                  + "where p.id=? and v.id=? and p.organization_id=?",
              Integer.class, productId, productVersionId, organizationId);
      if (products == null || products != 1) {
        throw new IllegalArgumentException("产品或版本不存在、不属于当前组织或关联不匹配");
      }
    }
    if ("TRAINING".equals(type) && fileObjectId != null) {
      Integer files = jdbc.queryForObject(
          "select count(*) from file_object where id=? and organization_id=?",
          Integer.class, fileObjectId, organizationId);
      if (files == null || files != 1) {
        throw new IllegalArgumentException("培训附件不存在或不属于当前组织");
      }
    }
  }

  private void validateTemplate(String type, String stageCode, String requirement) {
    if (!"TEMPLATE".equals(type)) return;
    try {
      DeliveryStage.valueOf(stageCode);
    } catch (RuntimeException invalidStage) {
      throw new IllegalArgumentException("文档模版必须选择有效的交付阶段");
    }
    if (!"REQUIRED".equals(requirement) && !"OPTIONAL".equals(requirement)) {
      throw new IllegalArgumentException("文档模版必需性只能是 REQUIRED 或 OPTIONAL");
    }
  }

  private Map<String, Object> item(
      ResultSet row, OutlineConnection connection) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    Long outlineLinkId = nullableLong(row, "outline_link_id");
    String documentStatus = outlineLinkId == null ? "PENDING" : row.getString("document_sync_status");
    value.put("id", row.getLong("id")); value.put("organizationId", row.getLong("organization_id"));
    value.put("type", row.getString("type")); value.put("title", row.getString("display_title"));
    value.put("summary", row.getString("summary"));
    value.put("content", "READY".equals(documentStatus) ? null : row.getString("content_text"));
    value.put("tags", row.getString("tags_text")); value.put("productId", nullableLong(row, "product_id"));
    value.put("productVersionId", nullableLong(row, "product_version_id"));
    value.put("productName", row.getString("product_name")); value.put("versionName", row.getString("version_name"));
    value.put("visibility", row.getString("visibility")); value.put("status", row.getString("status"));
    value.put("ownerUserId", row.getLong("owner_user_id")); value.put("ownerName", row.getString("owner_name"));
    value.put("publishedAt", row.getTimestamp("published_at")); value.put("updatedAt", row.getTimestamp("updated_at"));
    value.put("version", row.getLong("version"));
    value.put("outlineLinkId", outlineLinkId);
    value.put("documentStatus", documentStatus);
    value.put("documentRevision", nullableLong(row, "document_revision"));
    value.put("documentError", row.getString("document_error"));
    value.put("outlineUrl",
        configurations.documentUrl(connection, row.getString("outline_url_id")));
    value.put("stageCode", row.getString("stage_code"));
    value.put("requirement", row.getString("requirement"));
    value.put("enabled", row.getObject("enabled"));
    value.put("publishedRevision", nullableLong(row, "published_revision"));
    return value;
  }

  private Map<String, Object> detail(
      ResultSet row, OutlineConnection connection) throws SQLException {
    Map<String, Object> value = item(row, connection);
    value.put("language", row.getString("language")); value.put("codeText", row.getString("code_text"));
    value.put("usageNotes", row.getString("usage_notes")); value.put("audience", row.getString("audience"));
    value.put("durationMinutes", row.getObject("duration_minutes")); value.put("fileObjectId", row.getObject("file_object_id"));
    value.put("fileOriginalName", row.getString("file_original_name")); value.put("fileVersion", row.getObject("file_version"));
    value.put("fileSizeBytes", row.getObject("file_size_bytes"));
    return value;
  }

  private Long nullableLong(ResultSet row, String name) throws SQLException {
    long value = row.getLong(name); return row.wasNull() ? null : value;
  }
  private String typeName(String type) {
    if ("CASE".equals(type)) return "最佳实践";
    if ("CODE".equals(type)) return "代码片段";
    if ("TRAINING".equals(type)) return "培训材料";
    if ("TEMPLATE".equals(type)) return "文档模版";
    throw new IllegalArgumentException("知识类型不受支持");
  }
  private long number(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }
  private String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }
  private Boolean booleanValue(Object value) {
    return value == null ? null : Boolean.valueOf(String.valueOf(value));
  }
  private boolean blank(String value) { return value == null || value.trim().isEmpty() || "null".equals(value); }
}
