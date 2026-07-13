package com.zhilu.delivery.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  public ProductVersionFeatureService(JdbcTemplate jdbc, ProductCatalogService catalog) {
    this.jdbc = jdbc;
    this.catalog = catalog;
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
  public Map<String, Object> replaceManifest(long organizationId, long productId,
      long versionId, long expectedVersion, List<ManifestEntry> entries) {
    catalog.version(organizationId, productId, versionId);
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
    return manifest(organizationId, productId, versionId);
  }

  @Transactional
  public void appendPlannedFeature(long organizationId, long productId,
      long versionId, long featureId) {
    Map<String, Object> version = catalog.version(organizationId, productId, versionId);
    if (!"PLANNING".equals(version.get("status"))) {
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
