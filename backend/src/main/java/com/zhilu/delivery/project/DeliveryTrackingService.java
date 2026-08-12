package com.zhilu.delivery.project;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryTrackingService {
  private static final List<String> CLASSIFICATIONS =
      Arrays.asList("CONFIGURATION", "INTEGRATION", "ENHANCEMENT", "NEW_FEATURE");
  private static final List<String> ENDS = Arrays.asList("C", "B", "BACKEND");
  private static final List<String> COMPLEXITIES = Arrays.asList("S", "M", "L", "XL");
  private static final List<String> DEPENDENCIES =
      Arrays.asList("READY", "PROCESSING", "GAP", "NA");
  private static final List<String> REUSABLE = Arrays.asList("SEED", "GROWING", "MATURE", "NA");
  private static final List<String> STATUSES =
      Arrays.asList("TODO", "IN_PROGRESS", "DONE", "BLOCKED", "CANCELLED");

  private final JdbcTemplate jdbc;

  public DeliveryTrackingService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(long projectId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    return jdbc.query(select() + " where t.project_id=? order by t.sequence_no",
        (row, index) -> item(row), projectId);
  }

  @Transactional
  public Map<String, Object> create(long projectId, Command command, CurrentUser user) {
    Map<String, Object> project = assertProjectAccess(projectId, user);
    validate(command, projectId);
    jdbc.queryForObject("select id from delivery_project where id=? for update",
        Long.class, projectId);
    Integer sequence = jdbc.queryForObject(
        "select coalesce(max(sequence_no),0)+1 from delivery_tracking_item where project_id=?",
        Integer.class, projectId);
    Map<String, Object> values = values(command);
    values.put("organization_id", project.get("organization_id"));
    values.put("project_id", projectId);
    values.put("sequence_no", sequence);
    values.put("created_by", user.getId());
    String[] columns = values.keySet().toArray(new String[values.size()]);
    long id = new SimpleJdbcInsert(jdbc).withTableName("delivery_tracking_item")
        .usingColumns(columns).usingGeneratedKeyColumns("id")
        .executeAndReturnKey(values).longValue();
    activity(projectId, user.getId(), "DELIVERY_TRACKING_CREATED", "新增交付执行项", command.originalRequest);
    return get(projectId, id, user);
  }

  @Transactional
  public Map<String, Object> update(
      long projectId, long itemId, Command command, long version, CurrentUser user) {
    assertProjectAccess(projectId, user);
    validate(command, projectId);
    int changed = jdbc.update("update delivery_tracking_item set original_request=?,"
            + "classification=?,delivery_end=?,feature_point=?,complexity=?,product_dependency=?,"
            + "dependency_status=?,dependency_note=?,extension_point=?,estimated_days=?,"
            + "actual_days=?,reusable_level=?,status=?,owner_user_id=?,notes=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and project_id=? and version=?",
        text(command.originalRequest), command.classification, command.deliveryEnd,
        text(command.featurePoint), command.complexity, blank(command.productDependency),
        command.dependencyStatus, blank(command.dependencyNote), blank(command.extensionPoint),
        command.estimatedDays, command.actualDays, command.reusableLevel, command.status,
        command.ownerUserId, blank(command.notes), itemId, projectId, version);
    if (changed == 0) throw new ConflictException("执行项已被其他成员更新，请刷新后重试");
    activity(projectId, user.getId(), "DELIVERY_TRACKING_UPDATED", "更新交付执行项", String.valueOf(itemId));
    return get(projectId, itemId, user);
  }

  private Map<String, Object> get(long projectId, long itemId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    List<Map<String, Object>> values = jdbc.query(
        select() + " where t.project_id=? and t.id=?", (row, index) -> item(row), projectId, itemId);
    if (values.isEmpty()) throw new NotFoundException("交付执行项不存在");
    return values.get(0);
  }

  private String select() {
    return "select t.*,p.code project_code,u.display_name owner_name "
        + "from delivery_tracking_item t join delivery_project p on p.id=t.project_id "
        + "left join app_user u on u.id=t.owner_user_id";
  }

  private Map<String, Object> item(java.sql.ResultSet row) throws java.sql.SQLException {
    BigDecimal estimated = row.getBigDecimal("estimated_days");
    BigDecimal actual = row.getBigDecimal("actual_days");
    BigDecimal deviation = null;
    if (actual != null && estimated != null && estimated.compareTo(BigDecimal.ZERO) > 0) {
      deviation = actual.subtract(estimated).multiply(new BigDecimal("100"))
          .divide(estimated, 2, RoundingMode.HALF_UP);
    }
    return map("id", row.getLong("id"),
        "itemCode", row.getString("project_code") + "-" + String.format("%03d", row.getInt("sequence_no")),
        "originalRequest", row.getString("original_request"),
        "classification", row.getString("classification"), "deliveryEnd", row.getString("delivery_end"),
        "featurePoint", row.getString("feature_point"), "complexity", row.getString("complexity"),
        "productDependency", row.getString("product_dependency"),
        "dependencyStatus", row.getString("dependency_status"),
        "dependencyNote", row.getString("dependency_note"),
        "extensionPoint", row.getString("extension_point"), "estimatedDays", estimated,
        "actualDays", actual, "deviationPercent", deviation,
        "reusableLevel", row.getString("reusable_level"), "status", row.getString("status"),
        "ownerUserId", row.getObject("owner_user_id"), "ownerName", row.getString("owner_name"),
        "notes", row.getString("notes"), "version", row.getLong("version"),
        "updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
  }

  private Map<String, Object> values(Command command) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("original_request", text(command.originalRequest));
    values.put("classification", command.classification);
    values.put("delivery_end", command.deliveryEnd);
    values.put("feature_point", text(command.featurePoint));
    values.put("complexity", command.complexity);
    values.put("product_dependency", blank(command.productDependency));
    values.put("dependency_status", command.dependencyStatus);
    values.put("dependency_note", blank(command.dependencyNote));
    values.put("extension_point", blank(command.extensionPoint));
    values.put("estimated_days", command.estimatedDays);
    values.put("actual_days", command.actualDays);
    values.put("reusable_level", command.reusableLevel);
    values.put("status", command.status);
    values.put("owner_user_id", command.ownerUserId);
    values.put("notes", blank(command.notes));
    return values;
  }

  private void validate(Command value, long projectId) {
    oneOf(value.classification, CLASSIFICATIONS, "四分类");
    oneOf(value.deliveryEnd, ENDS, "端");
    oneOf(value.complexity, COMPLEXITIES, "复杂度");
    oneOf(value.dependencyStatus, DEPENDENCIES, "标品依赖状态");
    oneOf(value.reusableLevel, REUSABLE, "复用等级");
    oneOf(value.status, STATUSES, "状态");
    if (value.estimatedDays == null || value.estimatedDays.signum() < 0
        || value.actualDays != null && value.actualDays.signum() < 0) {
      throw new IllegalArgumentException("人天不能小于 0");
    }
    if (value.ownerUserId != null) {
      Integer member = jdbc.queryForObject(
          "select count(*) from project_member where project_id=? and user_id=?",
          Integer.class, projectId, value.ownerUserId);
      if (member == null || member == 0) throw new IllegalArgumentException("负责人必须是当前项目成员");
    }
  }

  private Map<String, Object> assertProjectAccess(long projectId, CurrentUser user) {
    List<Map<String, Object>> projects = jdbc.queryForList(
        "select id,organization_id from delivery_project where id=? and organization_id=?",
        projectId, user.getOrganizationId());
    if (projects.isEmpty()) throw new NotFoundException("项目不存在或无权访问");
    if (user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO")) return projects.get(0);
    Integer member = jdbc.queryForObject(
        "select count(*) from project_member where project_id=? and user_id=?",
        Integer.class, projectId, user.getId());
    if (member == null || member == 0) throw new NotFoundException("项目不存在或无权访问");
    return projects.get(0);
  }

  private void oneOf(String value, List<String> supported, String label) {
    if (!supported.contains(value)) throw new IllegalArgumentException(label + "不受支持");
  }

  private String text(String value) { return value == null ? "" : value.trim(); }
  private String blank(String value) { String result = text(value); return result.isEmpty() ? null : result; }

  private void activity(long projectId, long userId, String action, String summary, String details) {
    jdbc.update("insert into project_activity(project_id,actor_user_id,action,summary,details_text) "
        + "values (?,?,?,?,?)", projectId, userId, action, summary, details);
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  public static final class Command {
    public final String originalRequest;
    public final String classification;
    public final String deliveryEnd;
    public final String featurePoint;
    public final String complexity;
    public final String productDependency;
    public final String dependencyStatus;
    public final String dependencyNote;
    public final String extensionPoint;
    public final BigDecimal estimatedDays;
    public final BigDecimal actualDays;
    public final String reusableLevel;
    public final String status;
    public final Long ownerUserId;
    public final String notes;

    public Command(String originalRequest, String classification, String deliveryEnd,
        String featurePoint, String complexity, String productDependency,
        String dependencyStatus, String dependencyNote, String extensionPoint,
        BigDecimal estimatedDays, BigDecimal actualDays, String reusableLevel,
        String status, Long ownerUserId, String notes) {
      this.originalRequest = originalRequest;
      this.classification = classification;
      this.deliveryEnd = deliveryEnd;
      this.featurePoint = featurePoint;
      this.complexity = complexity;
      this.productDependency = productDependency;
      this.dependencyStatus = dependencyStatus;
      this.dependencyNote = dependencyNote;
      this.extensionPoint = extensionPoint;
      this.estimatedDays = estimatedDays;
      this.actualDays = actualDays;
      this.reusableLevel = reusableLevel;
      this.status = status;
      this.ownerUserId = ownerUserId;
      this.notes = notes;
    }
  }
}
