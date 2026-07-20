package com.zhilu.delivery.requirement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementService {
  private static final Set<String> LEVELS = new HashSet<String>(Arrays.asList("L0", "L1", "L2"));
  private final JdbcTemplate jdbc;
  private final AiClient ai;
  private final ObjectMapper json;
  private final AuditService audit;
  private final RequirementDocumentService documents;

  public RequirementService(JdbcTemplate jdbc, AiClient ai, ObjectMapper json, AuditService audit,
      RequirementDocumentService documents) {
    this.jdbc = jdbc; this.ai = ai; this.json = json; this.audit = audit;
    this.documents = documents;
  }

  @Transactional
  public Map<String, Object> collect(long projectId, String title, String description,
      String source, String priority, long actorUserId) {
    Map<String, Object> created = create(
        projectId, title, description, source, priority, actorUserId);
    long requirementId = ((Number) created.get("id")).longValue();
    documents.attach(requirementId, actorUserId);
    return get(requirementId);
  }

  @Transactional
  public Map<String, Object> create(long projectId, String title, String description,
      String source, String priority, long actorUserId) {
    if (blank(title) || blank(description)) throw new IllegalArgumentException("需求标题和描述不能为空");
    Map<String, Object> project = jdbc.queryForMap("select organization_id from delivery_project where id=?", projectId);
    long organizationId = ((Number) project.get("organization_id")).longValue();
    String code = "REQ-" + java.time.Year.now().getValue() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    String warning = meaningfulLength(title + description) < 10 ? "需求描述少于 10 个有效字符，建议补充业务场景和验收条件" : null;
    jdbc.update("insert into requirement_item(organization_id,project_id,requirement_code,title,description,"
            + "source,priority,status,validation_warning,created_by) values (?,?,?,?,?,?,?,'DRAFT',?,?)",
        organizationId, projectId, code, title.trim(), description.trim(), source,
        blank(priority) ? "P2" : priority, warning, actorUserId);
    Long id = jdbc.queryForObject("select id from requirement_item where organization_id=? and requirement_code=?", Long.class, organizationId, code);
    audit.record(organizationId, actorUserId, "REQUIREMENT_CREATED", "REQUIREMENT", String.valueOf(id), title);
    return get(id);
  }

  public List<Map<String, Object>> list(CurrentUser user, Long projectId, String keyword, String status) {
    StringBuilder sql = new StringBuilder("select r.*,p.product_id,p.code project_code,p.name project_name,d.confirmed_level,s.suggested_level,s.confidence,s.reason suggestion_reason "
        + "from requirement_item r join delivery_project p on p.id=r.project_id "
        + "left join classification_decision d on d.requirement_id=r.id "
        + "left join classification_suggestion s on s.id=(select max(s2.id) from classification_suggestion s2 where s2.requirement_id=r.id) "
        + "where r.organization_id=?");
    List<Object> args = new ArrayList<Object>(); args.add(user.getOrganizationId());
    if (!crossScope(user)) { sql.append(" and exists (select 1 from project_member pm where pm.project_id=r.project_id and pm.user_id=?)"); args.add(user.getId()); }
    if (projectId != null) { sql.append(" and r.project_id=?"); args.add(projectId); }
    if (!blank(keyword)) { sql.append(" and (lower(r.title) like ? or lower(r.requirement_code) like ?)"); String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%"; args.add(term); args.add(term); }
    if (!blank(status)) { sql.append(" and r.status=?"); args.add(status); }
    sql.append(" order by case r.priority when 'P0' then 1 when 'P1' then 2 when 'P2' then 3 else 4 end,r.updated_at desc");
    return jdbc.query(sql.toString(), (row, index) -> map(row), args.toArray());
  }

  @Transactional
  public Map<String, Object> update(long id, String title, String description, String source,
      String priority, long expectedVersion, boolean regenerateReport, long actorUserId) {
    if (blank(title) || blank(description)) throw new IllegalArgumentException("需求标题和描述不能为空");
    Map<String, Object> current = get(id);
    String warning = meaningfulLength(title + description) < 10 ? "需求描述少于 10 个有效字符，建议补充业务场景和验收条件" : null;
    int changed = jdbc.update("update requirement_item set title=?,description=?,source=?,priority=?,validation_warning=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and version=? "
            + "and status not in ('MERGED','ABANDONED')",
        title.trim(), description.trim(), source, blank(priority) ? "P2" : priority,
        warning, id, expectedVersion);
    if (changed == 0) throw new ConflictException("需求已被他人更新或已结束，请刷新后重试");
    if (regenerateReport) documents.regenerate(id, actorUserId);
    audit.record(((Number) current.get("organizationId")).longValue(), actorUserId,
        "REQUIREMENT_UPDATED", "REQUIREMENT", String.valueOf(id),
        regenerateReport ? "regenerateReport=true" : "regenerateReport=false");
    return get(id);
  }

  @Transactional
  public Map<String, Object> abandon(long id, long expectedVersion, long actorUserId) {
    Map<String, Object> requirement = get(id);
    int changed = jdbc.update("update requirement_item set status='ABANDONED',"
            + "updated_at=current_timestamp,version=version+1 where id=? and version=? "
            + "and status not in ('MERGED','ABANDONED')",
        id, expectedVersion);
    if (changed == 0) throw new ConflictException("需求已被他人更新或已结束，请刷新后重试");
    audit.record(((Number) requirement.get("organizationId")).longValue(), actorUserId,
        "REQUIREMENT_ABANDONED", "REQUIREMENT", String.valueOf(id), "status=ABANDONED");
    return get(id);
  }

  public Map<String, Object> get(long id) {
    List<Map<String, Object>> values = jdbc.query("select r.*,p.product_id,p.code project_code,p.name project_name,d.confirmed_level,d.suggestion_level decision_suggestion_level,d.override_reason,"
        + "s.suggested_level,s.confidence,s.reason suggestion_reason from requirement_item r join delivery_project p on p.id=r.project_id "
        + "left join classification_decision d on d.requirement_id=r.id left join classification_suggestion s on s.id=(select max(s2.id) from classification_suggestion s2 where s2.requirement_id=r.id) where r.id=?",
        (row, index) -> map(row), id);
    if (values.isEmpty()) throw new NotFoundException("需求不存在");
    Map<String, Object> value = values.get(0);
    value.put("duplicates", duplicates(id));
    return value;
  }

  @Transactional
  public Map<String, Object> classify(long id, long actorUserId) {
    Map<String, Object> requirement = get(id);
    assertActionable(requirement);
    ObjectNode schema = json.createObjectNode(); schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("level").put("type", "string").putArray("enum").add("L0").add("L1").add("L2");
    properties.putObject("confidence").put("type", "number");
    properties.putObject("reason").put("type", "string");
    schema.putArray("required").add("level").add("confidence").add("reason"); schema.put("additionalProperties", false);
    long organizationId = ((Number) requirement.get("organizationId")).longValue();
    JsonNode result = ai.completeJson(organizationId,
        "你是交付需求分类助手。L0=标品已有，L1=需要二开，L2=不在产品范围。只返回符合 schema 的 JSON。",
        "需求标题：" + requirement.get("title") + "\n需求描述：" + requirement.get("description"), schema);
    if (result == null || !result.isObject() || result.size() != 3
        || result.get("level") == null || !result.get("level").isTextual()
        || result.get("confidence") == null || !result.get("confidence").isNumber()
        || result.get("reason") == null || !result.get("reason").isTextual()) {
      throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
    }
    String level = result.get("level").asText();
    double confidence = result.get("confidence").asDouble();
    String reason = result.get("reason").asText();
    if (!LEVELS.contains(level) || Double.isNaN(confidence)
        || confidence < 0 || confidence > 1 || blank(reason)) {
      throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
    }
    return saveSuggestion(id, level, confidence, reason, "AI");
  }

  @Transactional
  public Map<String, Object> saveSuggestion(long id, String level, double confidence, String reason, String source) {
    validateLevel(level); assertActionable(get(id));
    jdbc.update("insert into classification_suggestion(requirement_id,suggested_level,confidence,reason,source) values (?,?,?,?,?)",
        id, level, confidence, reason, blank(source) ? "MANUAL" : source);
    jdbc.update("update requirement_item set status='SUBMITTED',updated_at=current_timestamp,version=version+1 where id=? and status='DRAFT'", id);
    return get(id);
  }

  @Transactional
  public Map<String, Object> confirm(long id, String level, String overrideReason, long actorUserId) {
    validateLevel(level);
    Map<String, Object> requirement = get(id);
    assertActionable(requirement);
    String suggested = value(requirement.get("suggestedLevel"));
    if (!blank(suggested) && !suggested.equals(level) && blank(overrideReason))
      throw new IllegalArgumentException("改判 AI 建议时必须填写原因");
    Integer exists = jdbc.queryForObject("select count(*) from classification_decision where requirement_id=?", Integer.class, id);
    if (exists != null && exists > 0) {
      jdbc.update("update classification_decision set confirmed_level=?,suggestion_level=?,override_reason=?,confirmed_by=?,confirmed_at=current_timestamp,version=version+1 where requirement_id=?",
          level, suggested, overrideReason, actorUserId, id);
    } else {
      jdbc.update("insert into classification_decision(requirement_id,confirmed_level,suggestion_level,override_reason,confirmed_by) values (?,?,?,?,?)",
          id, level, suggested, overrideReason, actorUserId);
    }
    jdbc.update("update requirement_item set status='CONFIRMED',updated_at=current_timestamp,version=version+1 where id=?", id);
    if ("L1".equals(level)) {
      try {
        jdbc.update("insert into custom_dev_task(requirement_id,project_id,title) values (?,?,?)", id,
            ((Number) requirement.get("projectId")).longValue(), requirement.get("title"));
      } catch (DuplicateKeyException ignored) { }
    }
    audit.record(((Number) requirement.get("organizationId")).longValue(), actorUserId,
        "REQUIREMENT_CLASSIFIED", "REQUIREMENT", String.valueOf(id), level);
    return get(id);
  }

  public Map<String, Object> funnel(long organizationId) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (String level : Arrays.asList("L0", "L1", "L2")) {
      Long count = jdbc.queryForObject("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id where r.organization_id=? and r.status='CONFIRMED' and d.confirmed_level=?", Long.class, organizationId, level);
      result.put(level, count == null ? 0L : count);
    }
    return result;
  }

  public Map<String, Object> funnel(CurrentUser user) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (String level : Arrays.asList("L0", "L1", "L2")) {
      String sql = "select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id where r.organization_id=? and r.status='CONFIRMED' and d.confirmed_level=?";
      List<Object> args = new ArrayList<Object>(); args.add(user.getOrganizationId()); args.add(level);
      if (!crossScope(user)) { sql += " and exists (select 1 from project_member pm where pm.project_id=r.project_id and pm.user_id=?)"; args.add(user.getId()); }
      Long count = jdbc.queryForObject(sql, Long.class, args.toArray()); result.put(level, count == null ? 0L : count);
    }
    return result;
  }

  public List<Map<String, Object>> findDuplicates(long id) {
    Map<String, Object> source = get(id); assertActionable(source);
    String sourceText = normalize(source.get("title") + " " + source.get("description"));
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> candidate : jdbc.queryForList("select id,title,description from requirement_item where project_id=? and id<>? and status not in ('MERGED','ABANDONED')",
        ((Number) source.get("projectId")).longValue(), id)) {
      double score = similarity(sourceText, normalize(candidate.get("title") + " " + candidate.get("description")));
      if (score >= 0.25) {
        try { jdbc.update("insert into duplicate_relation(source_requirement_id,target_requirement_id,similarity_score) values (?,?,?)",
            id, ((Number) candidate.get("id")).longValue(), score); } catch (DuplicateKeyException ignored) { }
        Map<String, Object> row = new LinkedHashMap<String, Object>(candidate); row.put("similarityScore", score); result.add(row);
      }
    }
    return result;
  }

  @Transactional
  public Map<String, Object> merge(long sourceId, long targetId, long actorUserId) {
    if (sourceId == targetId) throw new IllegalArgumentException("不能合并到自身");
    Map<String, Object> source = get(sourceId); Map<String, Object> target = get(targetId);
    assertActionable(source); assertActionable(target);
    if (!source.get("projectId").equals(target.get("projectId"))) throw new ConflictException("只能合并同一项目的需求");
    try { jdbc.update("insert into duplicate_relation(source_requirement_id,target_requirement_id,similarity_score,status,resolved_at) values (?,?,1,'MERGED',current_timestamp)", sourceId, targetId); }
    catch (DuplicateKeyException duplicate) { jdbc.update("update duplicate_relation set status='MERGED',resolved_at=current_timestamp where source_requirement_id=? and target_requirement_id=?", sourceId, targetId); }
    jdbc.update("update requirement_item set status='MERGED',merged_into_id=?,updated_at=current_timestamp,version=version+1 where id=?", targetId, sourceId);
    return get(sourceId);
  }

  private List<Map<String, Object>> duplicates(long id) {
    return jdbc.queryForList("select d.*,r.requirement_code,r.title from duplicate_relation d join requirement_item r on r.id=d.target_requirement_id where d.source_requirement_id=? order by d.similarity_score desc", id);
  }
  private Map<String, Object> map(java.sql.ResultSet row) throws java.sql.SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id")); value.put("organizationId", row.getLong("organization_id")); value.put("projectId", row.getLong("project_id")); value.put("productId", row.getLong("product_id"));
    value.put("projectCode", row.getString("project_code")); value.put("projectName", row.getString("project_name")); value.put("code", row.getString("requirement_code"));
    value.put("title", row.getString("title")); value.put("description", row.getString("description")); value.put("source", row.getString("source"));
    value.put("priority", row.getString("priority")); value.put("status", row.getString("status")); value.put("validationWarning", row.getString("validation_warning"));
    value.put("mergedIntoId", nullableLong(row, "merged_into_id")); value.put("version", row.getLong("version"));
    value.put("outlineLinkId", nullableLong(row, "outline_link_id"));
    value.put("sourceTemplateId", nullableLong(row, "source_template_id"));
    value.put("sourceTemplateRevision", nullableLong(row, "source_template_revision"));
    value.put("suggestedLevel", safe(row, "suggested_level")); value.put("confidence", safe(row, "confidence")); value.put("suggestionReason", safe(row, "suggestion_reason"));
    value.put("confirmedLevel", safe(row, "confirmed_level")); value.put("overrideReason", safe(row, "override_reason"));
    return value;
  }
  private Object safe(java.sql.ResultSet row, String column) { try { return row.getObject(column); } catch (java.sql.SQLException missing) { return null; } }
  private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException { long value = row.getLong(column); return row.wasNull() ? null : value; }
  private boolean crossScope(CurrentUser user) { return user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO"); }
  private void assertActionable(Map<String, Object> requirement) {
    String status = value(requirement.get("status"));
    if ("MERGED".equals(status) || "ABANDONED".equals(status)) {
      throw new ConflictException("需求已结束，不能继续操作");
    }
  }
  private void validateLevel(String level) { if (!LEVELS.contains(level)) throw new IllegalArgumentException("分类必须是 L0、L1 或 L2"); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
  private String value(Object value) { return value == null ? null : String.valueOf(value); }
  private int meaningfulLength(String value) { return value.replaceAll("[\\s\\p{Punct}]", "").length(); }
  private String normalize(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{Punct}]", ""); }
  private double similarity(String left, String right) { if (left.isEmpty() || right.isEmpty()) return 0; Set<Integer> a = bigrams(left), b = bigrams(right); Set<Integer> intersection = new HashSet<Integer>(a); intersection.retainAll(b); return 2d * intersection.size() / (a.size() + b.size()); }
  private Set<Integer> bigrams(String text) { Set<Integer> values = new HashSet<Integer>(); if (text.length() == 1) values.add(text.hashCode()); for (int i=0;i<text.length()-1;i++) values.add(text.substring(i,i+2).hashCode()); return values; }
}
