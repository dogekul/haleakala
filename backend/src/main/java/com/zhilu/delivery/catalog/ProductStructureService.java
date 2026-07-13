package com.zhilu.delivery.catalog;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStructureService {
  private static final Set<String> STATUSES = new HashSet<String>(
      Arrays.asList("PLANNING", "ACTIVE", "DEPRECATED"));

  private final JdbcTemplate jdbc;
  private final ProductCatalogService catalog;
  private final AuditService audit;

  public ProductStructureService(
      JdbcTemplate jdbc, ProductCatalogService catalog, AuditService audit) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.audit = audit;
  }

  public List<Map<String, Object>> modules(long organizationId, long productId) {
    catalog.product(organizationId, productId);
    return jdbc.query("select id,product_id,parent_id,owner_user_id,code,name,description,status,"
            + "sort_order,created_at,updated_at,version from product_module where product_id=? "
            + "order by case when parent_id is null then 0 else 1 end,"
            + "coalesce(parent_id,0),sort_order,name,id",
        (row, index) -> moduleRow(row), productId);
  }

  @Transactional
  public Map<String, Object> saveModule(long organizationId, long actorId, long productId,
      Long id, Long parentId, Long ownerUserId, String code, String name,
      String description, String status, int sortOrder, long version) {
    requireWritableProduct(organizationId, productId);
    Map<String, Object> current = id == null ? null : module(productId, id);
    if (current != null) requireCurrentVersion(current, version, "模块");
    validateOwner(organizationId, ownerUserId);
    validateStatus(status, "模块");
    validateInitialStatus(current, status, "模块");
    if (current != null) validateTransition(String.valueOf(current.get("status")), status, "模块");
    int parentDepth = validateParent(productId, id, parentId);
    if (id != null && parentDepth + subtreeHeight(productId, id) > 3) {
      throw invalidTree();
    }
    String normalizedCode = code.trim();
    String normalizedName = name.trim();
    try {
      if (id == null) {
        jdbc.update("insert into product_module(product_id,parent_id,owner_user_id,code,name,"
                + "description,status,sort_order) values (?,?,?,?,?,?,?,?)",
            productId, parentId, ownerUserId, normalizedCode, normalizedName,
            description, status, sortOrder);
        id = jdbc.queryForObject("select id from product_module where product_id=? and code=?",
            Long.class, productId, normalizedCode);
      } else {
        int changed = jdbc.update("update product_module set parent_id=?,owner_user_id=?,code=?,"
                + "name=?,description=?,status=?,sort_order=?,updated_at=current_timestamp,"
                + "version=version+1 where id=? and product_id=? and version=?",
            parentId, ownerUserId, normalizedCode, normalizedName, description, status, sortOrder,
            id, productId, version);
        if (changed == 0) throw stale("模块");
      }
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("模块编码已存在");
    }
    audit.record(organizationId, actorId, current == null ? "CREATE" : "UPDATE",
        "PRODUCT_MODULE", String.valueOf(id), normalizedName);
    return module(productId, id);
  }

  public List<Map<String, Object>> features(
      long organizationId, long productId, Long moduleId) {
    catalog.product(organizationId, productId);
    String sql = "select id,product_id,module_id,owner_user_id,code,name,description,status,"
        + "created_at,updated_at,version from product_feature where product_id=?"
        + (moduleId == null ? "" : " and module_id=?") + " order by name,code,id";
    return moduleId == null
        ? jdbc.query(sql, (row, index) -> featureRow(row), productId)
        : jdbc.query(sql, (row, index) -> featureRow(row), productId, moduleId);
  }

  @Transactional
  public Map<String, Object> saveFeature(long organizationId, long actorId, long productId,
      Long id, long moduleId, Long ownerUserId, String code, String name,
      String description, String status, long version) {
    requireWritableProduct(organizationId, productId);
    Map<String, Object> current = id == null ? null : feature(productId, id);
    if (current != null) requireCurrentVersion(current, version, "功能");
    validateModule(productId, moduleId);
    validateOwner(organizationId, ownerUserId);
    validateStatus(status, "功能");
    validateInitialStatus(current, status, "功能");
    if (current != null) validateTransition(String.valueOf(current.get("status")), status, "功能");
    String normalizedCode = code.trim();
    String normalizedName = name.trim();
    try {
      if (id == null) {
        jdbc.update("insert into product_feature(product_id,module_id,owner_user_id,code,name,"
                + "description,status) values (?,?,?,?,?,?,?)",
            productId, moduleId, ownerUserId, normalizedCode, normalizedName, description, status);
        id = jdbc.queryForObject("select id from product_feature where product_id=? and code=?",
            Long.class, productId, normalizedCode);
      } else {
        int changed = jdbc.update("update product_feature set module_id=?,owner_user_id=?,code=?,"
                + "name=?,description=?,status=?,updated_at=current_timestamp,version=version+1 "
                + "where id=? and product_id=? and version=?",
            moduleId, ownerUserId, normalizedCode, normalizedName, description, status,
            id, productId, version);
        if (changed == 0) throw stale("功能");
      }
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("功能编码已存在");
    }
    audit.record(organizationId, actorId, current == null ? "CREATE" : "UPDATE",
        "PRODUCT_FEATURE", String.valueOf(id), normalizedName);
    return feature(productId, id);
  }

  private void requireWritableProduct(long organizationId, long productId) {
    Map<String, Object> product = catalog.product(organizationId, productId);
    if ("ARCHIVED".equals(product.get("status"))) {
      throw new ConflictException("产品已归档，不能修改模块或功能");
    }
  }

  private Map<String, Object> module(long productId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,product_id,parent_id,owner_user_id,code,name,description,status,sort_order,"
            + "created_at,updated_at,version from product_module where id=? and product_id=?",
        (row, index) -> moduleRow(row), id, productId);
    if (values.isEmpty()) throw new NotFoundException("模块不存在");
    return values.get(0);
  }

  private Map<String, Object> feature(long productId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,product_id,module_id,owner_user_id,code,name,description,status,created_at,"
            + "updated_at,version from product_feature where id=? and product_id=?",
        (row, index) -> featureRow(row), id, productId);
    if (values.isEmpty()) throw new NotFoundException("功能不存在");
    return values.get(0);
  }

  private int validateParent(long productId, Long moduleId, Long parentId) {
    Long cursor = parentId;
    int parentDepth = 0;
    while (cursor != null) {
      if (moduleId != null && moduleId.equals(cursor)) throw invalidTree();
      List<Map<String, Object>> rows = jdbc.queryForList(
          "select parent_id from product_module where id=? and product_id=?", cursor, productId);
      if (rows.isEmpty()) throw new IllegalArgumentException("父模块不属于当前产品");
      cursor = rows.get(0).get("parent_id") == null
          ? null : ((Number) rows.get(0).get("parent_id")).longValue();
      if (++parentDepth >= 3) throw invalidTree();
    }
    return parentDepth;
  }

  private int subtreeHeight(long productId, long moduleId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select id,parent_id from product_module where product_id=?", productId);
    Map<Long, List<Long>> children = new HashMap<Long, List<Long>>();
    for (Map<String, Object> row : rows) {
      if (row.get("parent_id") == null) continue;
      Long parentId = ((Number) row.get("parent_id")).longValue();
      if (!children.containsKey(parentId)) children.put(parentId, new ArrayList<Long>());
      children.get(parentId).add(((Number) row.get("id")).longValue());
    }
    return subtreeHeight(moduleId, children, new HashSet<Long>());
  }

  private int subtreeHeight(
      long moduleId, Map<Long, List<Long>> children, Set<Long> path) {
    if (!path.add(moduleId)) throw invalidTree();
    int height = 1;
    List<Long> values = children.get(moduleId);
    if (values != null) {
      for (Long child : values) {
        height = Math.max(height, 1 + subtreeHeight(child, children, path));
      }
    }
    path.remove(moduleId);
    return height;
  }

  private void validateModule(long productId, long moduleId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from product_module where id=? and product_id=?",
        Integer.class, moduleId, productId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("所属模块不属于当前产品");
    }
  }

  private void validateOwner(long organizationId, Long ownerUserId) {
    if (ownerUserId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from app_user where id=? and organization_id=?",
        Integer.class, ownerUserId, organizationId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("负责人不存在或不属于当前组织");
    }
  }

  private void validateStatus(String status, String label) {
    if (!STATUSES.contains(status)) {
      throw new IllegalArgumentException(label + "状态不受支持");
    }
  }

  private void validateInitialStatus(
      Map<String, Object> current, String status, String label) {
    if (current == null && !"PLANNING".equals(status)) {
      throw new IllegalArgumentException(label + "必须从规划状态创建");
    }
  }

  private void validateTransition(String current, String target, String label) {
    if ("DEPRECATED".equals(current)) {
      throw new ConflictException(label + "已废弃，不能修改");
    }
    if (current.equals(target)
        || ("PLANNING".equals(current) && "ACTIVE".equals(target))
        || ("ACTIVE".equals(current) && "DEPRECATED".equals(target))) {
      return;
    }
    throw new IllegalArgumentException(label + "状态流转不受支持");
  }

  private void requireCurrentVersion(
      Map<String, Object> current, long version, String label) {
    if (((Number) current.get("version")).longValue() != version) throw stale(label);
  }

  private ConflictException stale(String label) {
    return new ConflictException(label + "已被更新，请刷新后重试");
  }

  private IllegalArgumentException invalidTree() {
    return new IllegalArgumentException("模块树最多三级且不能成环");
  }

  private Map<String, Object> moduleRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("productId", row.getLong("product_id"));
    value.put("parentId", row.getObject("parent_id"));
    value.put("ownerUserId", row.getObject("owner_user_id"));
    value.put("code", row.getString("code"));
    value.put("name", row.getString("name"));
    value.put("description", row.getString("description"));
    value.put("status", row.getString("status"));
    value.put("sortOrder", row.getInt("sort_order"));
    value.put("createdAt", row.getTimestamp("created_at"));
    value.put("updatedAt", row.getTimestamp("updated_at"));
    value.put("version", row.getLong("version"));
    return value;
  }

  private Map<String, Object> featureRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("productId", row.getLong("product_id"));
    value.put("moduleId", row.getLong("module_id"));
    value.put("ownerUserId", row.getObject("owner_user_id"));
    value.put("code", row.getString("code"));
    value.put("name", row.getString("name"));
    value.put("description", row.getString("description"));
    value.put("status", row.getString("status"));
    value.put("createdAt", row.getTimestamp("created_at"));
    value.put("updatedAt", row.getTimestamp("updated_at"));
    value.put("version", row.getLong("version"));
    return value;
  }
}
