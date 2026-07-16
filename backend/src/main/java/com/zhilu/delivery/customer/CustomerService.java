package com.zhilu.delivery.customer;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private static final Set<String> STATUSES =
      new HashSet<String>(Arrays.asList("ACTIVE", "INACTIVE"));
  private final JdbcTemplate jdbc;

  public CustomerService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(long organizationId, String keyword, String status) {
    validateOptionalStatus(status);
    StringBuilder sql = new StringBuilder(selectSql()).append(" where c.organization_id=?");
    List<Object> args = new ArrayList<Object>();
    args.add(organizationId);
    if (!blank(keyword)) {
      sql.append(" and (lower(c.name) like ? or lower(coalesce(c.short_name,'')) like ?")
          .append(" or lower(coalesce(c.contact_name,'')) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(pattern); args.add(pattern); args.add(pattern);
    }
    if (!blank(status)) {
      sql.append(" and c.status=?");
      args.add(status);
    }
    sql.append(" order by c.status,c.name");
    return jdbc.query(sql.toString(), (row, index) -> row(row), args.toArray());
  }

  public Map<String, Object> get(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        selectSql() + " where c.organization_id=? and c.id=?",
        (row, index) -> row(row), organizationId, id);
    if (values.isEmpty()) throw new NotFoundException("客户不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> create(long organizationId, String name, String shortName,
      String contactName, String phone, String email, String address, String status,
      String remark) {
    validate(name, status);
    String normalized = name.trim();
    try {
      jdbc.update("insert into customer(organization_id,name,short_name,contact_name,phone,email,"
              + "address,status,remark) values (?,?,?,?,?,?,?,?,?)",
          organizationId, normalized, clean(shortName), clean(contactName), clean(phone),
          clean(email), clean(address), status, clean(remark));
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("客户名称已存在");
    }
    Long id = jdbc.queryForObject(
        "select id from customer where organization_id=? and name=?",
        Long.class, organizationId, normalized);
    return get(organizationId, id);
  }

  @Transactional
  public Map<String, Object> update(long organizationId, long id, String name, String shortName,
      String contactName, String phone, String email, String address, String status,
      String remark, long version) {
    Map<String, Object> current = get(organizationId, id);
    if (((Number) current.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    validate(name, status);
    try {
      int changed = jdbc.update("update customer set name=?,short_name=?,contact_name=?,phone=?,"
              + "email=?,address=?,status=?,remark=?,updated_at=current_timestamp,version=version+1 "
              + "where id=? and organization_id=? and version=?",
          name.trim(), clean(shortName), clean(contactName), clean(phone), clean(email),
          clean(address), status, clean(remark), id, organizationId, version);
      if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("客户名称已存在");
    }
    return get(organizationId, id);
  }

  private String selectSql() {
    return "select c.id,c.organization_id,c.name,c.short_name,c.contact_name,c.phone,c.email,"
        + "c.address,c.status,c.remark,c.version,c.updated_at,"
        + "(select count(*) from delivery_project p where p.customer_id=c.id) project_count "
        + "from customer c";
  }

  private Map<String, Object> row(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("organizationId", row.getLong("organization_id"));
    value.put("name", row.getString("name"));
    value.put("shortName", row.getString("short_name"));
    value.put("contactName", row.getString("contact_name"));
    value.put("phone", row.getString("phone"));
    value.put("email", row.getString("email"));
    value.put("address", row.getString("address"));
    value.put("status", row.getString("status"));
    value.put("remark", row.getString("remark"));
    value.put("projectCount", row.getInt("project_count"));
    value.put("updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
    value.put("version", row.getLong("version"));
    return value;
  }

  private void validate(String name, String status) {
    if (blank(name)) throw new IllegalArgumentException("客户名称不能为空");
    if (!STATUSES.contains(status)) throw new IllegalArgumentException("客户状态不受支持");
  }

  private void validateOptionalStatus(String status) {
    if (!blank(status) && !STATUSES.contains(status)) {
      throw new IllegalArgumentException("客户状态不受支持");
    }
  }

  private String clean(String value) { return blank(value) ? null : value.trim(); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
