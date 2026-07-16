package com.zhilu.delivery.operation;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOperationService {
  private static final List<String> STAGES =
      Arrays.asList("MAINTENANCE", "OPERATING", "REPURCHASE", "CLOSED");
  private static final List<String> STATUSES = Arrays.asList("OPEN", "CLOSED");
  private final JdbcTemplate jdbc;

  public CustomerOperationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(long organizationId, String keyword, Long customerId,
      Long ownerUserId, String stage, String status) {
    validateOptional(stage, STAGES, "运营阶段不受支持");
    validateOptional(status, STATUSES, "运营状态不受支持");
    StringBuilder sql = new StringBuilder(selectSql()).append(" where o.organization_id=?");
    List<Object> args = new ArrayList<Object>();
    args.add(organizationId);
    if (!blank(keyword)) {
      sql.append(" and (lower(o.title) like ? or lower(o.customer_name_snapshot) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(pattern);
      args.add(pattern);
    }
    if (customerId != null) { sql.append(" and o.customer_id=?"); args.add(customerId); }
    if (ownerUserId != null) { sql.append(" and o.owner_user_id=?"); args.add(ownerUserId); }
    if (!blank(stage)) { sql.append(" and o.stage=?"); args.add(stage); }
    if (!blank(status)) { sql.append(" and o.status=?"); args.add(status); }
    sql.append(" order by o.updated_at desc,o.id desc");
    return jdbc.query(sql.toString(), (row, index) -> row(row), args.toArray());
  }

  public Map<String, Object> get(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        selectSql() + " where o.organization_id=? and o.id=?",
        (row, index) -> row(row), organizationId, id);
    if (values.isEmpty()) throw new NotFoundException("客户运营记录不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> create(long organizationId, long actorId, Input input) {
    References references = validateReferences(organizationId, input);
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("customer_id", input.customerId);
    values.put("customer_name_snapshot", references.customerName);
    values.put("title", input.title.trim());
    values.put("stage", "MAINTENANCE");
    values.put("status", "OPEN");
    values.put("owner_user_id", input.ownerUserId);
    values.put("project_id", input.projectId);
    values.put("opportunity_id", input.opportunityId);
    values.put("created_by", actorId);
    try {
      long id = new SimpleJdbcInsert(jdbc).withTableName("customer_operation")
          .usingColumns(values.keySet().toArray(new String[values.size()]))
          .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
      return get(organizationId, id);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("该商机已经进入客户运营");
    }
  }

  @Transactional
  public Map<String, Object> update(
      long organizationId, long id, long version, Input input) {
    Map<String, Object> current = get(organizationId, id);
    assertOpen(current);
    References references = validateReferences(organizationId, input);
    try {
      int changed = jdbc.update("update customer_operation set customer_id=?,"
              + "customer_name_snapshot=?,title=?,owner_user_id=?,project_id=?,opportunity_id=?,"
              + "updated_at=current_timestamp,version=version+1 "
              + "where id=? and organization_id=? and version=? and status='OPEN'",
          input.customerId, references.customerName, input.title.trim(), input.ownerUserId,
          input.projectId, input.opportunityId, id, organizationId, version);
      if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("该商机已经进入客户运营");
    }
    return get(organizationId, id);
  }

  @Transactional
  public Map<String, Object> advance(long organizationId, long id, long version, long actorId) {
    Map<String, Object> current = get(organizationId, id);
    assertOpen(current);
    String stage = String.valueOf(current.get("stage"));
    int index = STAGES.indexOf(stage);
    if (index < 0 || index == STAGES.size() - 1) {
      throw new ConflictException("客户运营已经关闭");
    }
    String target = STAGES.get(index + 1);
    String status = "CLOSED".equals(target) ? "CLOSED" : "OPEN";
    int changed = jdbc.update("update customer_operation set stage=?,status=?,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and version=? and status='OPEN'",
        target, status, id, organizationId, version);
    if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    return get(organizationId, id);
  }

  @Transactional
  public void ensureForClosedProject(long organizationId, long projectId, long actorId) {
    try {
      jdbc.update("insert into customer_operation(organization_id,customer_id,"
              + "customer_name_snapshot,title,stage,status,owner_user_id,project_id,"
              + "opportunity_id,created_by) "
              + "select o.organization_id,o.customer_id,o.customer_name_snapshot,"
              + "concat(o.title,'客户运营'),'MAINTENANCE','OPEN',"
              + "coalesce(o.operation_owner_user_id,p.manager_user_id),p.id,o.id,? "
              + "from sales_opportunity o join delivery_project p on p.id=o.project_id "
              + "where o.organization_id=? and p.organization_id=? and p.id=? and o.status='WON' "
              + "and not exists(select 1 from customer_operation x where x.opportunity_id=o.id)",
          actorId, organizationId, organizationId, projectId);
    } catch (DuplicateKeyException alreadyCreated) {
      // The unique opportunity constraint is the final guard for concurrent retries.
    }
  }

  private References validateReferences(long organizationId, Input input) {
    List<Map<String, Object>> customers = jdbc.queryForList(
        "select name,status from customer where id=? and organization_id=?",
        input.customerId, organizationId);
    if (customers.isEmpty()) throw new NotFoundException("客户不存在");
    Map<String, Object> customer = customers.get(0);
    if (!"ACTIVE".equals(customer.get("status"))) {
      throw new IllegalArgumentException("停用客户不能创建运营记录");
    }
    if (input.ownerUserId != null) {
      List<String> statuses = jdbc.queryForList(
          "select status from app_user where id=? and organization_id=?",
          String.class, input.ownerUserId, organizationId);
      if (statuses.isEmpty()) throw new NotFoundException("负责人不存在");
      if (!"ACTIVE".equals(statuses.get(0))) throw new IllegalArgumentException("负责人已停用");
    }
    if (input.projectId != null) {
      Integer project = jdbc.queryForObject(
          "select count(*) from delivery_project where id=? and organization_id=? and customer_id=?",
          Integer.class, input.projectId, organizationId, input.customerId);
      if (project == null || project == 0) throw new NotFoundException("项目不存在或客户不一致");
    }
    if (input.opportunityId != null) {
      List<Map<String, Object>> opportunities = jdbc.queryForList(
          "select project_id from sales_opportunity where id=? and organization_id=? and customer_id=?",
          input.opportunityId, organizationId, input.customerId);
      if (opportunities.isEmpty()) throw new NotFoundException("商机不存在或客户不一致");
      Object linkedProject = opportunities.get(0).get("project_id");
      if (input.projectId != null && linkedProject != null
          && ((Number) linkedProject).longValue() != input.projectId.longValue()) {
        throw new IllegalArgumentException("商机与项目关联不一致");
      }
    }
    return new References(String.valueOf(customer.get("name")));
  }

  private String selectSql() {
    return "select o.id,o.organization_id,o.customer_id,o.customer_name_snapshot,o.title,"
        + "o.stage,o.status,o.owner_user_id,u.display_name owner_name,o.project_id,p.name project_name,"
        + "o.opportunity_id,s.title opportunity_title,o.created_at,o.updated_at,o.version "
        + "from customer_operation o left join app_user u on u.id=o.owner_user_id "
        + "left join delivery_project p on p.id=o.project_id "
        + "left join sales_opportunity s on s.id=o.opportunity_id";
  }

  private Map<String, Object> row(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("organizationId", row.getLong("organization_id"));
    value.put("customerId", row.getLong("customer_id"));
    value.put("customerName", row.getString("customer_name_snapshot"));
    value.put("title", row.getString("title"));
    value.put("stage", row.getString("stage"));
    value.put("status", row.getString("status"));
    value.put("ownerUserId", nullableLong(row, "owner_user_id"));
    value.put("ownerName", row.getString("owner_name"));
    Long projectId = nullableLong(row, "project_id");
    value.put("projectId", projectId);
    value.put("project", projectId == null ? null
        : map("id", projectId, "name", row.getString("project_name")));
    Long opportunityId = nullableLong(row, "opportunity_id");
    value.put("opportunityId", opportunityId);
    value.put("opportunity", opportunityId == null ? null
        : map("id", opportunityId, "title", row.getString("opportunity_title")));
    value.put("createdAt", row.getTimestamp("created_at").toLocalDateTime());
    value.put("updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
    value.put("version", row.getLong("version"));
    return value;
  }

  private void assertOpen(Map<String, Object> operation) {
    if (!"OPEN".equals(operation.get("status"))) {
      throw new ConflictException("客户运营已经关闭");
    }
  }

  private void validateOptional(String value, List<String> allowed, String message) {
    if (!blank(value) && !allowed.contains(value)) throw new IllegalArgumentException(message);
  }

  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

  private Long nullableLong(ResultSet row, String column) throws SQLException {
    Object value = row.getObject(column);
    return value == null ? null : ((Number) value).longValue();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  private static final class References {
    private final String customerName;
    private References(String customerName) { this.customerName = customerName; }
  }

  public static class Input {
    @NotNull public Long customerId;
    @NotBlank @Size(max = 180) public String title;
    public Long ownerUserId;
    public Long projectId;
    public Long opportunityId;
  }
}
