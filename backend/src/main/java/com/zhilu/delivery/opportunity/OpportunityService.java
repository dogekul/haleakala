package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.customer.CustomerService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityService {
  private static final Set<String> STATUSES =
      new HashSet<String>(Arrays.asList("OPEN", "WON", "LOST"));
  private final JdbcTemplate jdbc;
  private final CustomerService customers;

  public OpportunityService(JdbcTemplate jdbc, CustomerService customers) {
    this.jdbc = jdbc;
    this.customers = customers;
  }

  public List<Map<String, Object>> list(long organizationId, String keyword, Long customerId,
      Long productId, Long commercialOwnerUserId, Long solutionOwnerUserId,
      Long projectManagerUserId, Long operationOwnerUserId, String stage, String status) {
    validateStageAndStatus(stage, status);
    StringBuilder sql = new StringBuilder(selectSql()).append(" where o.organization_id=?");
    List<Object> args = new ArrayList<Object>();
    args.add(organizationId);
    if (!blank(keyword)) {
      sql.append(" and (lower(o.title) like ? or lower(coalesce(c.name,o.customer_name_snapshot)) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(pattern); args.add(pattern);
    }
    appendFilter(sql, args, "o.customer_id", customerId);
    appendFilter(sql, args, "o.product_id", productId);
    appendFilter(sql, args, "o.commercial_owner_user_id", commercialOwnerUserId);
    appendFilter(sql, args, "o.solution_owner_user_id", solutionOwnerUserId);
    appendFilter(sql, args, "o.project_manager_user_id", projectManagerUserId);
    appendFilter(sql, args, "o.operation_owner_user_id", operationOwnerUserId);
    if (!blank(stage)) { sql.append(" and o.stage=?"); args.add(stage); }
    if (!blank(status)) { sql.append(" and o.status=?"); args.add(status); }
    sql.append(" order by o.updated_at desc,o.id desc");
    return jdbc.query(sql.toString(), (row, index) -> row(row), args.toArray());
  }

  public Map<String, Object> get(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        selectSql() + " where o.organization_id=? and o.id=?",
        (row, index) -> row(row), organizationId, id);
    if (values.isEmpty()) throw new NotFoundException("商机不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> create(long organizationId, long actorId, Input input) {
    References references = validateReferences(organizationId, input);
    Map<String, Object> values = editableValues(input, references);
    values.put("organization_id", organizationId);
    values.put("stage", OpportunityStage.LEAD.name());
    values.put("status", "OPEN");
    values.put("created_by", actorId);
    String[] columns = values.keySet().toArray(new String[values.size()]);
    long id = new SimpleJdbcInsert(jdbc).withTableName("sales_opportunity")
        .usingColumns(columns).usingGeneratedKeyColumns("id")
        .executeAndReturnKey(values).longValue();
    return get(organizationId, id);
  }

  @Transactional
  public Map<String, Object> update(
      long organizationId, long id, long version, Input input) {
    Map<String, Object> current = get(organizationId, id);
    if (((Number) current.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    References references = validateReferences(organizationId, input);
    int changed = jdbc.update("update sales_opportunity set customer_id=?,"
            + "customer_name_snapshot=?,title=?,note=?,amount=?,product_id=?,product_version_id=?,"
            + "commercial_owner_user_id=?,solution_owner_user_id=?,project_manager_user_id=?,"
            + "operation_owner_user_id=?,updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and version=?",
        input.customerId, references.customerName, input.title.trim(), clean(input.note),
        input.amount == null ? BigDecimal.ZERO : input.amount, input.productId,
        input.productVersionId, input.commercialOwnerUserId, input.solutionOwnerUserId,
        input.projectManagerUserId, input.operationOwnerUserId, id, organizationId, version);
    if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    return get(organizationId, id);
  }

  public List<Map<String, Object>> ownerOptions(long organizationId) {
    return jdbc.query("select id,display_name from app_user "
            + "where organization_id=? and status='ACTIVE' order by id",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("displayName", row.getString("display_name"));
          return value;
        }, organizationId);
  }

  private References validateReferences(long organizationId, Input input) {
    Map<String, Object> customer = customers.get(organizationId, input.customerId.longValue());
    if (!"ACTIVE".equals(customer.get("status"))) {
      throw new IllegalArgumentException("客户已停用，不能创建商机");
    }
    validateOwner(organizationId, input.commercialOwnerUserId);
    validateOwner(organizationId, input.solutionOwnerUserId);
    validateOwner(organizationId, input.projectManagerUserId);
    validateOwner(organizationId, input.operationOwnerUserId);
    validateProduct(organizationId, input.productId, input.productVersionId);
    return new References(String.valueOf(customer.get("name")));
  }

  private void validateOwner(long organizationId, Long userId) {
    if (userId == null) return;
    List<String> statuses = jdbc.queryForList(
        "select status from app_user where id=? and organization_id=?",
        String.class, userId, organizationId);
    if (statuses.isEmpty()) throw new NotFoundException("负责人不存在");
    if (!"ACTIVE".equals(statuses.get(0))) throw new IllegalArgumentException("负责人已停用");
  }

  private void validateProduct(long organizationId, Long productId, Long versionId) {
    if (productId == null && versionId == null) return;
    if (productId == null) throw new IllegalArgumentException("选择产品版本前必须选择产品");
    Integer products = jdbc.queryForObject(
        "select count(*) from product where id=? and organization_id=?",
        Integer.class, productId, organizationId);
    if (products == null || products == 0) throw new NotFoundException("产品不存在");
    if (versionId == null) return;
    Integer versions = jdbc.queryForObject(
        "select count(*) from product_version where id=? and product_id=?",
        Integer.class, versionId, productId);
    if (versions == null || versions == 0) {
      throw new IllegalArgumentException("产品版本不属于所选产品");
    }
  }

  private Map<String, Object> editableValues(Input input, References references) {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("customer_id", input.customerId);
    values.put("customer_name_snapshot", references.customerName);
    values.put("title", input.title.trim());
    values.put("note", clean(input.note));
    values.put("amount", input.amount == null ? BigDecimal.ZERO : input.amount);
    values.put("product_id", input.productId);
    values.put("product_version_id", input.productVersionId);
    values.put("commercial_owner_user_id", input.commercialOwnerUserId);
    values.put("solution_owner_user_id", input.solutionOwnerUserId);
    values.put("project_manager_user_id", input.projectManagerUserId);
    values.put("operation_owner_user_id", input.operationOwnerUserId);
    return values;
  }

  private String selectSql() {
    return "select o.*,coalesce(c.name,o.customer_name_snapshot) customer_display_name,"
        + "p.name product_name,pv.version_name product_version_name,"
        + "cu.display_name commercial_owner_name,su.display_name solution_owner_name,"
        + "pu.display_name project_manager_name,ou.display_name operation_owner_name,"
        + "dp.name project_name from sales_opportunity o "
        + "left join customer c on c.id=o.customer_id "
        + "left join product p on p.id=o.product_id "
        + "left join product_version pv on pv.id=o.product_version_id "
        + "left join app_user cu on cu.id=o.commercial_owner_user_id "
        + "left join app_user su on su.id=o.solution_owner_user_id "
        + "left join app_user pu on pu.id=o.project_manager_user_id "
        + "left join app_user ou on ou.id=o.operation_owner_user_id "
        + "left join delivery_project dp on dp.id=o.project_id";
  }

  private Map<String, Object> row(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("organizationId", row.getLong("organization_id"));
    value.put("customerId", row.getLong("customer_id"));
    value.put("customerName", row.getString("customer_display_name"));
    value.put("title", row.getString("title"));
    value.put("note", row.getString("note"));
    value.put("amount", row.getBigDecimal("amount"));
    value.put("productId", nullableLong(row, "product_id"));
    value.put("productName", row.getString("product_name"));
    value.put("productVersionId", nullableLong(row, "product_version_id"));
    value.put("productVersionName", row.getString("product_version_name"));
    value.put("commercialOwnerUserId", nullableLong(row, "commercial_owner_user_id"));
    value.put("commercialOwnerName", row.getString("commercial_owner_name"));
    value.put("solutionOwnerUserId", nullableLong(row, "solution_owner_user_id"));
    value.put("solutionOwnerName", row.getString("solution_owner_name"));
    value.put("projectManagerUserId", nullableLong(row, "project_manager_user_id"));
    value.put("projectManagerName", row.getString("project_manager_name"));
    value.put("operationOwnerUserId", nullableLong(row, "operation_owner_user_id"));
    value.put("operationOwnerName", row.getString("operation_owner_name"));
    value.put("stage", row.getString("stage"));
    value.put("status", row.getString("status"));
    value.put("projectId", nullableLong(row, "project_id"));
    value.put("projectName", row.getString("project_name"));
    value.put("stageEnteredAt", row.getTimestamp("stage_entered_at").toLocalDateTime());
    value.put("createdAt", row.getTimestamp("created_at").toLocalDateTime());
    value.put("updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
    value.put("version", row.getLong("version"));
    return value;
  }

  private Long nullableLong(ResultSet row, String column) throws SQLException {
    long value = row.getLong(column);
    return row.wasNull() ? null : value;
  }

  private void validateStageAndStatus(String stage, String status) {
    if (!blank(stage)) {
      try { OpportunityStage.valueOf(stage); }
      catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("商机阶段不受支持"); }
    }
    if (!blank(status) && !STATUSES.contains(status)) {
      throw new IllegalArgumentException("商机状态不受支持");
    }
  }

  private void appendFilter(StringBuilder sql, List<Object> args, String column, Long value) {
    if (value != null) { sql.append(" and ").append(column).append("=?"); args.add(value); }
  }

  private String clean(String value) { return blank(value) ? null : value.trim(); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

  public static class Input {
    @NotNull public Long customerId;
    @NotBlank @Size(max = 180) public String title;
    @Size(max = 10000) public String note;
    @DecimalMin("0") public BigDecimal amount = BigDecimal.ZERO;
    public Long productId;
    public Long productVersionId;
    public Long commercialOwnerUserId;
    public Long solutionOwnerUserId;
    public Long projectManagerUserId;
    public Long operationOwnerUserId;
  }

  private static final class References {
    private final String customerName;
    private References(String customerName) { this.customerName = customerName; }
  }
}
