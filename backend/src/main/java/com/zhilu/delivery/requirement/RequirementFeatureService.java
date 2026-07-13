package com.zhilu.delivery.requirement;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.catalog.ProductCatalogService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
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
public class RequirementFeatureService {
  private static final Set<String> COVERAGE_TYPES =
      new HashSet<String>(Arrays.asList("FULL", "PARTIAL"));

  private final JdbcTemplate jdbc;
  private final ProductCatalogService catalog;
  private final AuditService audit;

  public RequirementFeatureService(
      JdbcTemplate jdbc, ProductCatalogService catalog, AuditService audit) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.audit = audit;
  }

  public Map<String, Object> coverage(long requirementId, CurrentUser user) {
    requirementContext(requirementId, user);
    List<Map<String, Object>> entries = jdbc.query(
        "select f.id feature_id,f.code feature_code,f.name feature_name,m.name module_name,"
            + "rf.coverage_type,rf.source,rf.created_by,rf.created_at "
            + "from requirement_product_feature rf "
            + "join product_feature f on f.id=rf.product_feature_id "
            + "join product_module m on m.id=f.module_id where rf.requirement_id=? order by f.id",
        (row, index) -> {
          Map<String, Object> entry = new LinkedHashMap<String, Object>();
          entry.put("featureId", row.getLong("feature_id"));
          entry.put("featureCode", row.getString("feature_code"));
          entry.put("featureName", row.getString("feature_name"));
          entry.put("moduleName", row.getString("module_name"));
          entry.put("coverageType", row.getString("coverage_type"));
          entry.put("source", row.getString("source"));
          entry.put("createdBy", row.getLong("created_by"));
          entry.put("createdAt", row.getTimestamp("created_at"));
          return entry;
        }, requirementId);
    boolean fullyCovered = false;
    for (Map<String, Object> entry : entries) {
      if ("FULL".equals(entry.get("coverageType"))) fullyCovered = true;
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("requirementId", requirementId);
    result.put("fullyCovered", fullyCovered);
    result.put("entries", entries);
    return result;
  }

  @Transactional
  public Map<String, Object> replaceCoverage(long requirementId, CurrentUser user,
      List<CoverageEntry> entries) {
    lockRequirement(requirementId, user.getOrganizationId());
    Map<String, Object> context = requirementContext(requirementId, user);
    validateFeatures(((Number) context.get("product_id")).longValue(), entries);
    if (containsFull(entries)) {
      Integer candidates = jdbc.queryForObject(
          "select count(*) from standardization_debt_requirement dr "
              + "join standardization_debt d on d.id=dr.standardization_debt_id "
              + "where dr.requirement_id=? and d.status in ('CANDIDATE','PENDING')",
          Integer.class, requirementId);
      if (candidates != null && candidates > 0) {
        throw new ConflictException("需求已进入标准化候选，不能标记为完全覆盖");
      }
    }
    Map<Long, Map<String, Object>> existingTraces = new LinkedHashMap<Long, Map<String, Object>>();
    for (Map<String, Object> trace : jdbc.queryForList(
        "select product_feature_id,source,created_by,created_at "
            + "from requirement_product_feature where requirement_id=?",
        requirementId)) {
      existingTraces.put(((Number) trace.get("product_feature_id")).longValue(), trace);
    }
    jdbc.update("delete from requirement_product_feature where requirement_id=?", requirementId);
    for (CoverageEntry entry : entries) {
      Map<String, Object> trace = existingTraces.get(entry.getFeatureId());
      if (trace == null) {
        jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
                + "coverage_type,source,created_by) values (?,?,?,'MANUAL',?)",
            requirementId, entry.getFeatureId(), entry.getCoverageType(), user.getId());
      } else {
        jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
                + "coverage_type,source,created_by,created_at) values (?,?,?,?,?,?)",
            requirementId, entry.getFeatureId(), entry.getCoverageType(), trace.get("source"),
            trace.get("created_by"), trace.get("created_at"));
      }
    }
    audit.record(user.getOrganizationId(), user.getId(), "REPLACE_COVERAGE", "REQUIREMENT",
        String.valueOf(requirementId), "entries=" + entries.size());
    return coverage(requirementId, user);
  }

  private boolean containsFull(List<CoverageEntry> entries) {
    for (CoverageEntry entry : entries) {
      if ("FULL".equals(entry.getCoverageType())) return true;
    }
    return false;
  }

  private void lockRequirement(long requirementId, long organizationId) {
    List<Long> values = jdbc.query(
        "select id from requirement_item where id=? and organization_id=? for update",
        (row, index) -> row.getLong("id"), requirementId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("需求不存在");
  }

  public Map<String, Object> productCoverage(long organizationId, long productId) {
    catalog.product(organizationId, productId);
    List<Map<String, Object>> features = jdbc.query(
        "select f.id feature_id,f.code feature_code,f.name feature_name,m.name module_name,"
            + "sum(case when rf.coverage_type='FULL' and p.id is not null then 1 else 0 end) full_count,"
            + "sum(case when rf.coverage_type='PARTIAL' and p.id is not null then 1 else 0 end) partial_count "
            + "from product_feature f join product_module m on m.id=f.module_id "
            + "left join requirement_product_feature rf on rf.product_feature_id=f.id "
            + "left join requirement_item r on r.id=rf.requirement_id and r.organization_id=? "
            + "left join delivery_project p on p.id=r.project_id and p.product_id=f.product_id "
            + "and p.organization_id=? "
            + "where f.product_id=? group by f.id,f.code,f.name,m.name order by f.id",
        (row, index) -> {
          Map<String, Object> feature = new LinkedHashMap<String, Object>();
          feature.put("featureId", row.getLong("feature_id"));
          feature.put("featureCode", row.getString("feature_code"));
          feature.put("featureName", row.getString("feature_name"));
          feature.put("moduleName", row.getString("module_name"));
          feature.put("fullCount", row.getLong("full_count"));
          feature.put("partialCount", row.getLong("partial_count"));
          return feature;
        }, organizationId, organizationId, productId);
    List<Map<String, Object>> uncovered = jdbc.query(
        "select r.id requirement_id,r.requirement_code,r.title,p.code project_code,"
            + "case when exists (select 1 from standardization_debt_requirement dr "
            + "where dr.requirement_id=r.id) then true else false end debt_linked "
            + "from requirement_item r join delivery_project p on p.id=r.project_id "
            + "where p.product_id=? and r.organization_id=? and p.organization_id=? "
            + "and not exists "
            + "(select 1 from requirement_product_feature rf where rf.requirement_id=r.id "
            + "and rf.coverage_type='FULL') order by r.id",
        (row, index) -> {
          Map<String, Object> requirement = new LinkedHashMap<String, Object>();
          requirement.put("requirementId", row.getLong("requirement_id"));
          requirement.put("requirementCode", row.getString("requirement_code"));
          requirement.put("title", row.getString("title"));
          requirement.put("projectCode", row.getString("project_code"));
          requirement.put("debtLinked", row.getBoolean("debt_linked"));
          return requirement;
        }, productId, organizationId, organizationId);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("productId", productId);
    result.put("features", features);
    result.put("uncoveredRequirements", uncovered);
    return result;
  }

  private Map<String, Object> requirementContext(long requirementId, CurrentUser user) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select p.product_id from requirement_item r "
            + "join delivery_project p on p.id=r.project_id "
            + "join product pr on pr.id=p.product_id "
            + "where r.id=? and r.organization_id=? and p.organization_id=? "
            + "and pr.organization_id=?",
        requirementId, user.getOrganizationId(), user.getOrganizationId(),
        user.getOrganizationId());
    if (values.isEmpty()) throw new NotFoundException("需求不存在");
    return values.get(0);
  }

  private void validateFeatures(long productId, List<CoverageEntry> entries) {
    if (entries == null) throw new IllegalArgumentException("功能覆盖列表不能为空");
    Set<Long> featureIds = new HashSet<Long>();
    for (CoverageEntry entry : entries) {
      if (entry == null || !COVERAGE_TYPES.contains(entry.getCoverageType())) {
        throw new IllegalArgumentException("覆盖类型必须是 FULL 或 PARTIAL");
      }
      if (!featureIds.add(entry.getFeatureId())) {
        throw new IllegalArgumentException("功能覆盖不能包含重复功能");
      }
      Integer valid = jdbc.queryForObject(
          "select count(*) from product_feature where id=? and product_id=?",
          Integer.class, entry.getFeatureId(), productId);
      if (valid == null || valid != 1) throw new NotFoundException("产品功能不存在");
    }
  }

  public static final class CoverageEntry {
    private final long featureId;
    private final String coverageType;

    public CoverageEntry(long featureId, String coverageType) {
      this.featureId = featureId;
      this.coverageType = coverageType;
    }

    public long getFeatureId() {
      return featureId;
    }

    public String getCoverageType() {
      return coverageType;
    }
  }
}
