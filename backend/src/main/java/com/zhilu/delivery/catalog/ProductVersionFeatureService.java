package com.zhilu.delivery.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductVersionFeatureService {
  private static final Set<String> AVAILABILITIES = new HashSet<String>(
      Arrays.asList("INCLUDED", "PLANNED", "REMOVED"));

  private final JdbcTemplate jdbc;
  private final ProductCatalogService catalog;
  private final AuditService audit;

  public ProductVersionFeatureService(
      JdbcTemplate jdbc, ProductCatalogService catalog, AuditService audit) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.audit = audit;
  }

  public Map<String, Object> manifest(long organizationId, long productId, long versionId) {
    Map<String, Object> version = catalog.version(organizationId, productId, versionId);
    List<Map<String, Object>> entries = jdbc.query(
        "select product_feature_id,availability from product_version_feature "
            + "where product_version_id=? order by product_feature_id",
        (row, index) -> {
          Map<String, Object> entry = new LinkedHashMap<String, Object>();
          entry.put("featureId", row.getLong("product_feature_id"));
          entry.put("availability", row.getString("availability"));
          return entry;
        }, versionId);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("versionId", versionId);
    result.put("version", version.get("version"));
    result.put("entries", entries);
    return result;
  }

  @Transactional
  public Map<String, Object> replaceManifest(long organizationId, long actorUserId,
      long productId, long versionId, long expectedVersion, List<ManifestEntry> entries) {
    String productStatus = lockProduct(organizationId, productId);
    if (!"ACTIVE".equals(productStatus)) {
      throw new ConflictException("只能修改生效产品的版本清单");
    }
    Map<String, Object> version = lockVersion(productId, versionId);
    if (!"PLANNING".equals(version.get("status"))) {
      throw new ConflictException("只能修改规划中版本的功能清单");
    }
    if (((Number) version.get("version")).longValue() != expectedVersion) {
      throw new ConflictException("版本清单已被更新，请刷新后重试");
    }
    validateEntries(productId, entries);
    int changed = jdbc.update("update product_version set version=version+1,"
            + "updated_at=current_timestamp where id=? and product_id=? and version=?",
        versionId, productId, expectedVersion);
    if (changed == 0) {
      throw new ConflictException("版本清单已被更新，请刷新后重试");
    }
    jdbc.update("delete from product_version_feature where product_version_id=?", versionId);
    for (ManifestEntry entry : entries) {
      jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
              + "availability) values (?,?,?)",
          versionId, entry.getFeatureId(), entry.getAvailability());
    }
    audit.record(organizationId, actorUserId, "REPLACE_MANIFEST", "PRODUCT_VERSION",
        String.valueOf(versionId), "entries=" + entries.size());
    return manifest(organizationId, productId, versionId);
  }

  private String lockProduct(long organizationId, long productId) {
    List<String> values = jdbc.query(
        "select status from product where id=? and organization_id=? for update",
        (row, index) -> row.getString("status"), productId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("产品不存在");
    return values.get(0);
  }

  private Map<String, Object> lockVersion(long productId, long versionId) {
    List<Map<String, Object>> values = jdbc.query(
        "select status,version from product_version where id=? and product_id=? for update",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("status", row.getString("status"));
          value.put("version", row.getLong("version"));
          return value;
        }, versionId, productId);
    if (values.isEmpty()) throw new NotFoundException("产品版本不存在");
    return values.get(0);
  }

  @Transactional
  public void appendPlannedFeature(long organizationId, long productId,
      long versionId, long featureId) {
    catalog.product(organizationId, productId);
    List<String> statuses = jdbc.query(
        "select status from product_version where id=? and product_id=? for update",
        (row, index) -> row.getString("status"), versionId, productId);
    if (statuses.isEmpty()) throw new NotFoundException("产品版本不存在");
    if (!"PLANNING".equals(statuses.get(0))) {
      throw new ConflictException("只能向规划中版本加入候选功能");
    }
    Integer valid = jdbc.queryForObject(
        "select count(*) from product_feature where id=? and product_id=?",
        Integer.class, featureId, productId);
    if (valid == null || valid != 1) throw new NotFoundException("产品功能不存在");
    Integer exists = jdbc.queryForObject(
        "select count(*) from product_version_feature "
            + "where product_version_id=? and product_feature_id=?",
        Integer.class, versionId, featureId);
    if (exists != null && exists == 0) {
      jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
              + "availability) values (?,?,'PLANNED')",
          versionId, featureId);
      jdbc.update("update product_version set version=version+1,updated_at=current_timestamp "
          + "where id=? and product_id=?", versionId, productId);
    }
  }

  private void validateEntries(long productId, List<ManifestEntry> entries) {
    if (entries == null) throw new IllegalArgumentException("版本清单不能为空");
    Set<Long> featureIds = new HashSet<Long>();
    for (ManifestEntry entry : entries) {
      if (entry == null || !AVAILABILITIES.contains(entry.getAvailability())) {
        throw new IllegalArgumentException("功能可用性不受支持");
      }
      if (!featureIds.add(entry.getFeatureId())) {
        throw new IllegalArgumentException("版本清单不能包含重复功能");
      }
      Integer valid = jdbc.queryForObject(
          "select count(*) from product_feature where id=? and product_id=?",
          Integer.class, entry.getFeatureId(), productId);
      if (valid == null || valid != 1) throw new NotFoundException("产品功能不存在");
    }
  }

  public static final class ManifestEntry {
    private final long featureId;
    private final String availability;

    @JsonCreator
    public ManifestEntry(
        @JsonProperty("featureId") long featureId,
        @JsonProperty("availability") String availability) {
      this.featureId = featureId;
      this.availability = availability;
    }

    public long getFeatureId() {
      return featureId;
    }

    public String getAvailability() {
      return availability;
    }
  }
}
