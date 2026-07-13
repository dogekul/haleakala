package com.zhilu.delivery.catalog;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
public class ProductCatalogService {
  private static final Set<String> PRODUCT_STATUSES = new HashSet<String>(
      Arrays.asList("PLANNING", "ACTIVE", "SUNSET", "ARCHIVED"));
  private static final Set<String> VERSION_STATUSES = new HashSet<String>(
      Arrays.asList("PLANNING", "RELEASED", "SUNSET", "ARCHIVED"));
  private static final Map<String, String> PRODUCT_NEXT = transitions(
      "PLANNING", "ACTIVE", "ACTIVE", "SUNSET", "SUNSET", "ARCHIVED");
  private static final Map<String, String> VERSION_NEXT = transitions(
      "PLANNING", "RELEASED", "RELEASED", "SUNSET", "SUNSET", "ARCHIVED");

  private final JdbcTemplate jdbc;

  public ProductCatalogService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> products(long organizationId, boolean bindable) {
    String sql = "select p.id,p.organization_id,p.owner_user_id,p.code,p.name,p.category,p.description,p.status,"
        + "p.updated_at,p.version,(select count(*) from product_module m where m.product_id=p.id) module_count,"
        + "(select count(*) from product_feature f where f.product_id=p.id) feature_count,"
        + "(select pv.version_name from product_version pv where pv.product_id=p.id order by pv.release_date desc,pv.id desc limit 1) latest_version_name "
        + "from product p where p.organization_id=?" + (bindable ? " and p.status='ACTIVE'" : "")
        + " order by name";
    return jdbc.query(sql, (row, index) -> productRow(row), organizationId);
  }

  public Map<String, Object> product(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,organization_id,owner_user_id,code,name,category,description,status,version "
            + "from product where id=? and organization_id=?",
        (row, index) -> productRow(row), id, organizationId);
    if (values.isEmpty()) throw new NotFoundException("产品不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> createProduct(long organizationId, Long ownerUserId,
      String code, String name, String category, String description) {
    validateOwner(organizationId, ownerUserId);
    try {
      jdbc.update("insert into product(organization_id,owner_user_id,code,name,category,description,status) "
              + "values (?,?,?,?,?,?,'PLANNING')",
          organizationId, ownerUserId, code.trim(), name.trim(), category, description);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("产品编码已存在");
    }
    Long id = jdbc.queryForObject(
        "select id from product where organization_id=? and code=?",
        Long.class, organizationId, code.trim());
    return product(organizationId, id);
  }

  @Transactional
  public Map<String, Object> updateProduct(long organizationId, long id, Long ownerUserId,
      String name, String category, String description, String status, long version) {
    Map<String, Object> current = product(organizationId, id);
    validateOwner(organizationId, ownerUserId);
    validateStatus(PRODUCT_STATUSES, status, "产品");
    validateTransition(String.valueOf(current.get("status")), status, PRODUCT_NEXT, "产品");
    int changed = jdbc.update("update product set owner_user_id=?,name=?,category=?,description=?,status=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and organization_id=? and version=?",
        ownerUserId, name.trim(), category, description, status, id, organizationId, version);
    if (changed == 0) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    return product(organizationId, id);
  }

  public List<Map<String, Object>> versions(
      long organizationId, long productId, boolean bindable) {
    product(organizationId, productId);
    String sql = "select id,product_id,version_name,release_date,status,version "
        + "from product_version where product_id=?"
        + (bindable ? " and status='RELEASED'" : "")
        + " order by release_date desc,id desc";
    return jdbc.query(sql, (row, index) -> versionRow(row), productId);
  }

  public Map<String, Object> version(long organizationId, long productId, long versionId) {
    product(organizationId, productId);
    List<Map<String, Object>> values = jdbc.query(
        "select id,product_id,version_name,release_date,status,version from product_version "
            + "where id=? and product_id=?",
        (row, index) -> versionRow(row), versionId, productId);
    if (values.isEmpty()) throw new NotFoundException("产品版本不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> createVersion(
      long organizationId, long productId, String versionName, LocalDate releaseDate) {
    product(organizationId, productId);
    try {
      jdbc.update("insert into product_version(product_id,version_name,release_date,status) "
              + "values (?,?,?,'PLANNING')",
          productId, versionName.trim(), releaseDate == null ? null : Date.valueOf(releaseDate));
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("产品版本已存在");
    }
    Long id = jdbc.queryForObject(
        "select id from product_version where product_id=? and version_name=?",
        Long.class, productId, versionName.trim());
    return version(organizationId, productId, id);
  }

  @Transactional
  public Map<String, Object> updateVersion(long organizationId, long productId, long versionId,
      LocalDate releaseDate, String status, long version) {
    Map<String, Object> current = version(organizationId, productId, versionId);
    validateStatus(VERSION_STATUSES, status, "产品版本");
    validateTransition(String.valueOf(current.get("status")), status, VERSION_NEXT, "产品版本");
    requireReleaseable(releaseDate, status);
    int changed = jdbc.update("update product_version set release_date=?,status=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and product_id=? and version=?",
        releaseDate == null ? null : Date.valueOf(releaseDate), status, versionId, productId, version);
    if (changed == 0) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    return version(organizationId, productId, versionId);
  }

  private void requireReleaseable(LocalDate releaseDate, String status) {
    if ("RELEASED".equals(status) && releaseDate == null) {
      throw new IllegalArgumentException("已发布版本必须填写发布日期");
    }
  }

  private void validateOwner(long organizationId, Long ownerUserId) {
    if (ownerUserId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from app_user where id=? and organization_id=?",
        Integer.class, ownerUserId, organizationId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("产品负责人不存在或不属于当前组织");
    }
  }

  private void validateStatus(Set<String> statuses, String status, String label) {
    if (!statuses.contains(status)) {
      throw new IllegalArgumentException(label + "状态不受支持");
    }
  }

  private void validateTransition(
      String current, String target, Map<String, String> next, String label) {
    if ("ARCHIVED".equals(current)) {
      throw new ConflictException(label + "已归档，不能修改");
    }
    if (current.equals(target)
        || ("PLANNING".equals(current) && "ARCHIVED".equals(target))
        || target.equals(next.get(current))) {
      return;
    }
    throw new IllegalArgumentException(label + "状态流转不受支持");
  }

  private static Map<String, String> transitions(String... pairs) {
    Map<String, String> values = new HashMap<String, String>();
    for (int index = 0; index < pairs.length; index += 2) {
      values.put(pairs[index], pairs[index + 1]);
    }
    return Collections.unmodifiableMap(values);
  }

  private Map<String, Object> productRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("organizationId", row.getLong("organization_id"));
    value.put("ownerUserId", row.getObject("owner_user_id"));
    value.put("code", row.getString("code"));
    value.put("name", row.getString("name"));
    value.put("category", row.getString("category"));
    value.put("description", row.getString("description"));
    value.put("status", row.getString("status"));
    if (hasColumn(row, "updated_at")) {
      value.put("updatedAt", row.getTimestamp("updated_at"));
      value.put("moduleCount", row.getLong("module_count"));
      value.put("featureCount", row.getLong("feature_count"));
      value.put("latestVersionName", row.getString("latest_version_name"));
    }
    value.put("version", row.getLong("version"));
    return value;
  }

  private boolean hasColumn(ResultSet row, String name) throws SQLException {
    for (int index = 1; index <= row.getMetaData().getColumnCount(); index++) {
      if (name.equalsIgnoreCase(row.getMetaData().getColumnLabel(index))) return true;
    }
    return false;
  }

  private Map<String, Object> versionRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("productId", row.getLong("product_id"));
    value.put("versionName", row.getString("version_name"));
    value.put("releaseDate", row.getDate("release_date") == null
        ? null : row.getDate("release_date").toLocalDate());
    value.put("status", row.getString("status"));
    value.put("version", row.getLong("version"));
    return value;
  }
}
