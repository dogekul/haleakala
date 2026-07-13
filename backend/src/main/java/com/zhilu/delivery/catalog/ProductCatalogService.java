package com.zhilu.delivery.catalog;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {
  private final JdbcTemplate jdbc;

  public ProductCatalogService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> products() {
    return jdbc.query("select id,owner_user_id,code,name,category,status,version from product "
        + "order by name", (row, index) -> productRow(row));
  }

  public Map<String, Object> product(long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,owner_user_id,code,name,category,status,version from product where id=?",
        (row, index) -> productRow(row), id);
    if (values.isEmpty()) {
      throw new NotFoundException("产品不存在");
    }
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> createProduct(
      long organizationId, Long ownerUserId, String code, String name, String category) {
    validateOwner(organizationId, ownerUserId);
    try {
      jdbc.update("insert into product(owner_user_id,code,name,category,status) "
              + "values (?,?,?,?,'ACTIVE')",
          ownerUserId, code.trim(), name.trim(), category);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("产品编码已存在");
    }
    Long id = jdbc.queryForObject("select id from product where code=?", Long.class, code.trim());
    return product(id);
  }

  @Transactional
  public Map<String, Object> updateProduct(
      long organizationId, long id, Long ownerUserId, String name, String category, String status) {
    validateOwner(organizationId, ownerUserId);
    validateStatus(status, "产品");
    int changed = jdbc.update("update product set owner_user_id=?,name=?,category=?,status=?,"
            + "updated_at=current_timestamp,version=version+1 where id=?",
        ownerUserId, name.trim(), category, status, id);
    if (changed == 0) {
      throw new NotFoundException("产品不存在");
    }
    return product(id);
  }

  public List<Map<String, Object>> versions(long productId) {
    product(productId);
    return jdbc.query("select id,product_id,version_name,release_date,status,version "
            + "from product_version where product_id=? order by release_date desc,id desc",
        (row, index) -> versionRow(row), productId);
  }

  public Map<String, Object> version(long productId, long versionId) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,product_id,version_name,release_date,status,version from product_version "
            + "where id=? and product_id=?",
        (row, index) -> versionRow(row), versionId, productId);
    if (values.isEmpty()) {
      throw new NotFoundException("产品版本不存在");
    }
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> createVersion(
      long productId, String versionName, LocalDate releaseDate) {
    product(productId);
    try {
      jdbc.update("insert into product_version(product_id,version_name,release_date,status) "
              + "values (?,?,?,'ACTIVE')",
          productId, versionName.trim(), releaseDate == null ? null : Date.valueOf(releaseDate));
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("产品版本已存在");
    }
    Long id = jdbc.queryForObject(
        "select id from product_version where product_id=? and version_name=?",
        Long.class, productId, versionName.trim());
    return version(productId, id);
  }

  @Transactional
  public Map<String, Object> updateVersion(
      long productId, long versionId, LocalDate releaseDate, String status) {
    validateStatus(status, "产品版本");
    int changed = jdbc.update("update product_version set release_date=?,status=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and product_id=?",
        releaseDate == null ? null : Date.valueOf(releaseDate), status, versionId, productId);
    if (changed == 0) {
      throw new NotFoundException("产品版本不存在");
    }
    return version(productId, versionId);
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

  private void validateStatus(String status, String label) {
    if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
      throw new IllegalArgumentException(label + "状态不受支持");
    }
  }

  private Map<String, Object> productRow(java.sql.ResultSet row) throws java.sql.SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("ownerUserId", row.getObject("owner_user_id"));
    value.put("code", row.getString("code"));
    value.put("name", row.getString("name"));
    value.put("category", row.getString("category"));
    value.put("status", row.getString("status"));
    value.put("version", row.getLong("version"));
    return value;
  }

  private Map<String, Object> versionRow(java.sql.ResultSet row) throws java.sql.SQLException {
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
