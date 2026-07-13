package com.zhilu.delivery.knowledge;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
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
  private static final List<String> TYPES = Arrays.asList("CASE", "CODE", "TRAINING");
  private final JdbcTemplate jdbc;
  private final SimpleJdbcInsert insert;

  public KnowledgeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.insert = new SimpleJdbcInsert(jdbc).withTableName("knowledge_item")
        .usingColumns("organization_id", "type", "title", "summary", "content_text",
            "tags_text", "product_id", "product_version_id", "visibility", "owner_user_id")
        .usingGeneratedKeyColumns("id");
  }

  public List<Map<String, Object>> search(
      CurrentUser user, String keyword, String type, String tag, boolean publishedOnly) {
    StringBuilder sql = new StringBuilder(
        "select k.*,p.name product_name,v.version_name,u.display_name owner_name,"
            + "c.language,c.code_text,c.usage_notes,t.audience,t.duration_minutes,t.file_object_id,"
            + "f.original_name file_original_name,f.file_version,f.size_bytes file_size_bytes "
            + "from knowledge_item k left join product p on p.id=k.product_id "
            + "left join product_version v on v.id=k.product_version_id "
            + "join app_user u on u.id=k.owner_user_id "
            + "left join code_snippet c on c.knowledge_item_id=k.id "
            + "left join training_material t on t.knowledge_item_id=k.id "
            + "left join file_object f on f.id=t.file_object_id and f.organization_id=k.organization_id where k.organization_id=?");
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
      sql.append(" and (lower(k.title) like ? or lower(k.summary) like ? or lower(k.content_text) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase() + "%";
      args.add(pattern); args.add(pattern); args.add(pattern);
    }
    if (!blank(tag)) { sql.append(" and lower(k.tags_text) like ?"); args.add("%" + tag.trim().toLowerCase() + "%"); }
    sql.append(" order by case k.status when 'PUBLISHED' then 1 else 2 end,k.updated_at desc,k.id desc");
    return jdbc.query(sql.toString(), (row, index) -> detail(row), args.toArray());
  }

  public Map<String, Object> get(long id, CurrentUser user) {
    List<Map<String, Object>> values = jdbc.query(
        "select k.*,p.name product_name,v.version_name,u.display_name owner_name,"
            + "c.language,c.code_text,c.usage_notes,t.audience,t.duration_minutes,t.file_object_id,"
            + "f.original_name file_original_name,f.file_version,f.size_bytes file_size_bytes "
            + "from knowledge_item k left join product p on p.id=k.product_id "
            + "left join product_version v on v.id=k.product_version_id join app_user u on u.id=k.owner_user_id "
            + "left join code_snippet c on c.knowledge_item_id=k.id "
            + "left join training_material t on t.knowledge_item_id=k.id "
            + "left join file_object f on f.id=t.file_object_id and f.organization_id=k.organization_id where k.id=? and k.organization_id=?",
        (row, index) -> detail(row), id, user.getOrganizationId());
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
    validate(type, title, summary, content);
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", user.getOrganizationId()); values.put("type", type);
    values.put("title", title); values.put("summary", summary); values.put("content_text", content);
    values.put("tags_text", tags); values.put("product_id", productId);
    values.put("product_version_id", productVersionId);
    values.put("visibility", blank(visibility) ? "ORGANIZATION" : visibility);
    values.put("owner_user_id", user.getId());
    long id = insert.executeAndReturnKey(values).longValue();
    saveDetail(id, type, language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId);
    return get(id, user);
  }

  @Transactional
  public Map<String, Object> update(
      long id, CurrentUser user, String type, String title, String summary, String content,
      String tags, Long productId, Long productVersionId, String visibility,
      String language, String codeText, String audienceOrUsageNotes, Integer durationMinutes,
      Long fileObjectId, long version) {
    Map<String, Object> current = get(id, user);
    if (((Number) current.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) throw new ConflictException("只能维护自己的知识条目");
    validate(type, title, summary, content);
    int changed = jdbc.update("update knowledge_item set type=?,title=?,summary=?,content_text=?,tags_text=?,product_id=?,product_version_id=?,visibility=?,status='DRAFT',published_at=null,updated_at=current_timestamp,version=version+1 where id=? and version=?",
        type,title,summary,content,tags,productId,productVersionId,blank(visibility)?"ORGANIZATION":visibility,id,version);
    if (changed == 0) throw new ConflictException("知识条目已被更新，请刷新后重试");
    jdbc.update("delete from code_snippet where knowledge_item_id=?", id);
    jdbc.update("delete from training_material where knowledge_item_id=?", id);
    saveDetail(id, type, language, codeText, audienceOrUsageNotes, durationMinutes, fileObjectId);
    return get(id, user);
  }

  @Transactional
  public Map<String, Object> publish(long id, CurrentUser user) {
    Map<String, Object> value = get(id, user);
    if (((Number) value.get("ownerUserId")).longValue() != user.getId()
        && !user.getPermissions().contains("system:manage")) throw new ConflictException("只有条目负责人可发布");
    if ("CODE".equals(value.get("type")) && blank(String.valueOf(value.get("codeText"))))
      throw new ConflictException("代码片段为空，不能发布");
    jdbc.update("update knowledge_item set status='PUBLISHED',published_at=current_timestamp,updated_at=current_timestamp,version=version+1 where id=?", id);
    return get(id, user);
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

  private void validate(String type, String title, String summary, String content) {
    if (!TYPES.contains(type)) throw new IllegalArgumentException("知识类型不受支持");
    if (blank(title) || blank(summary) || blank(content)) throw new IllegalArgumentException("标题、摘要和正文不能为空");
  }

  private Map<String, Object> item(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id")); value.put("organizationId", row.getLong("organization_id"));
    value.put("type", row.getString("type")); value.put("title", row.getString("title"));
    value.put("summary", row.getString("summary")); value.put("content", row.getString("content_text"));
    value.put("tags", row.getString("tags_text")); value.put("productId", nullableLong(row, "product_id"));
    value.put("productVersionId", nullableLong(row, "product_version_id"));
    value.put("productName", row.getString("product_name")); value.put("versionName", row.getString("version_name"));
    value.put("visibility", row.getString("visibility")); value.put("status", row.getString("status"));
    value.put("ownerUserId", row.getLong("owner_user_id")); value.put("ownerName", row.getString("owner_name"));
    value.put("publishedAt", row.getTimestamp("published_at")); value.put("updatedAt", row.getTimestamp("updated_at"));
    value.put("version", row.getLong("version")); return value;
  }

  private Map<String, Object> detail(ResultSet row) throws SQLException {
    Map<String, Object> value = item(row);
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
  private boolean blank(String value) { return value == null || value.trim().isEmpty() || "null".equals(value); }
}
