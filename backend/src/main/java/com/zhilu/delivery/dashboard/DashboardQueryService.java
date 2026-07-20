package com.zhilu.delivery.dashboard;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {
  private final JdbcTemplate jdbc;

  public DashboardQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<String, Object> summary(CurrentUser user, DashboardFilter filter) {
    List<Map<String, Object>> rows = projects(user, filter);
    long active = 0, red = 0, yellow = 0, openRisks = 0, overdue = 0;
    int healthTotal = 0;
    Map<String, Integer> stages = new LinkedHashMap<String, Integer>();
    Map<String, Integer> products = new LinkedHashMap<String, Integer>();
    for (Map<String, Object> row : rows) {
      if ("ACTIVE".equals(row.get("status"))) active++;
      String risk = String.valueOf(row.get("riskLevel"));
      if ("RED".equals(risk)) red++;
      if ("YELLOW".equals(risk)) yellow++;
      healthTotal += "GREEN".equals(risk) ? 100 : "YELLOW".equals(risk) ? 70 : 35;
      openRisks += ((Number) row.get("openRiskCount")).longValue();
      overdue += ((Number) row.get("overdueMilestoneCount")).longValue();
      increment(stages, String.valueOf(row.get("currentStage")));
      increment(products, String.valueOf(row.get("productName")));
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("activeProjects", active);
    result.put("totalProjects", (long) rows.size());
    result.put("redProjects", red);
    result.put("yellowProjects", yellow);
    result.put("healthScore", rows.isEmpty() ? 0 : (int) Math.round(healthTotal / (double) rows.size()));
    result.put("openRisks", openRisks);
    result.put("overdueMilestones", overdue);
    result.put("stageDistribution", stages);
    result.put("productDistribution", products);
    return result;
  }

  public List<Map<String, Object>> projects(CurrentUser user, DashboardFilter filter) {
    Query query = baseQuery(user, filter);
    String sql = "select p.id,p.code,p.name,coalesce(c.name,p.customer_name) customer_name,p.status,p.current_stage,p.risk_level,"
        + "p.start_date,p.planned_end_date,p.product_id,pr.name product_name,pv.version_name,u.display_name manager_name,"
        + "(select count(*) from project_risk r where r.project_id=p.id and r.status='OPEN') open_risk_count,"
        + "(select count(*) from milestone m where m.project_id=p.id and m.status<>'COMPLETED' and m.due_date<current_date) overdue_count "
        + "from delivery_project p left join customer c on c.id=p.customer_id and c.organization_id=p.organization_id "
        + "join product pr on pr.id=p.product_id "
        + "join product_version pv on pv.id=p.product_version_id join app_user u on u.id=p.manager_user_id "
        + query.where + " order by case p.risk_level when 'RED' then 1 when 'YELLOW' then 2 else 3 end,p.updated_at desc";
    return jdbc.query(sql, (row, index) -> {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("id", row.getLong("id")); value.put("code", row.getString("code"));
      value.put("name", row.getString("name")); value.put("customerName", row.getString("customer_name"));
      value.put("status", row.getString("status")); value.put("currentStage", row.getString("current_stage"));
      value.put("riskLevel", row.getString("risk_level")); value.put("productId", row.getLong("product_id"));
      value.put("productName", row.getString("product_name")); value.put("productVersionName", row.getString("version_name"));
      value.put("managerName", row.getString("manager_name"));
      value.put("startDate", row.getDate("start_date") == null ? null : row.getDate("start_date").toLocalDate());
      LocalDate end = row.getDate("planned_end_date") == null ? null : row.getDate("planned_end_date").toLocalDate();
      value.put("plannedEndDate", end); value.put("daysRemaining", end == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), end));
      value.put("openRiskCount", row.getLong("open_risk_count"));
      value.put("overdueMilestoneCount", row.getLong("overdue_count"));
      value.put("progress", progress(row.getString("current_stage")));
      return value;
    }, query.args.toArray());
  }

  public List<Map<String, Object>> riskHeatmap(CurrentUser user) {
    Query query = baseQuery(user, new DashboardFilter());
    String sql = "select p.id project_id,p.code,p.name,r.category,count(*) risk_count,max(r.probability*r.impact) max_score "
        + "from delivery_project p left join customer c on c.id=p.customer_id and c.organization_id=p.organization_id "
        + "join project_risk r on r.project_id=p.id " + query.where
        + " and r.status='OPEN' group by p.id,p.code,p.name,r.category order by max_score desc,risk_count desc";
    return jdbc.query(sql, (row, index) -> {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("projectId", row.getLong("project_id")); value.put("projectCode", row.getString("code"));
      value.put("projectName", row.getString("name")); value.put("category", row.getString("category"));
      value.put("riskCount", row.getLong("risk_count")); value.put("maxScore", row.getInt("max_score"));
      return value;
    }, query.args.toArray());
  }

  public List<Map<String, Object>> matrix(CurrentUser user) {
    Map<String, Map<String, Object>> grouped = new LinkedHashMap<String, Map<String, Object>>();
    for (Map<String, Object> project : projects(user, new DashboardFilter())) {
      String product = String.valueOf(project.get("productName"));
      Map<String, Object> row = grouped.get(product);
      if (row == null) {
        row = new LinkedHashMap<String, Object>(); row.put("productName", product);
        row.put("projects", new ArrayList<Map<String, Object>>()); grouped.put(product, row);
      }
      @SuppressWarnings("unchecked") List<Map<String, Object>> values = (List<Map<String, Object>>) row.get("projects");
      values.add(project);
    }
    return new ArrayList<Map<String, Object>>(grouped.values());
  }

  private Query baseQuery(CurrentUser user, DashboardFilter filter) {
    StringBuilder where = new StringBuilder("where p.organization_id=?");
    List<Object> args = new ArrayList<Object>(); args.add(user.getOrganizationId());
    if (!crossScope(user)) {
      where.append(" and exists (select 1 from project_member scope_m where scope_m.project_id=p.id and scope_m.user_id=?)");
      args.add(user.getId());
    }
    if (filter != null && present(filter.getKeyword())) {
      where.append(" and (lower(p.name) like ? or lower(p.code) like ? or lower(coalesce(c.name,p.customer_name)) like ?)");
      String keyword = "%" + filter.getKeyword().trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(keyword); args.add(keyword); args.add(keyword);
    }
    if (filter != null && present(filter.getStatus())) { where.append(" and p.status=?"); args.add(filter.getStatus()); }
    if (filter != null && present(filter.getRiskLevel())) { where.append(" and p.risk_level=?"); args.add(filter.getRiskLevel()); }
    if (filter != null && filter.getProductId() != null) { where.append(" and p.product_id=?"); args.add(filter.getProductId()); }
    return new Query(where.toString(), args);
  }

  private boolean crossScope(CurrentUser user) {
    return user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO");
  }
  private boolean present(String value) { return value != null && !value.trim().isEmpty(); }
  private int progress(String stage) {
    List<String> stages = ArraysHolder.STAGES; int index = stages.indexOf(stage);
    return index < 0 ? 0 : Math.min(100, (index * 100) / 6);
  }
  private void increment(Map<String, Integer> map, String key) { map.put(key, map.containsKey(key) ? map.get(key) + 1 : 1); }
  private static final class Query { private final String where; private final List<Object> args; private Query(String where, List<Object> args) { this.where = where; this.args = args; } }
  private static final class ArraysHolder { private static final List<String> STAGES = Collections.unmodifiableList(java.util.Arrays.asList("START","REQUIREMENT","CUSTOM_DEV","GO_LIVE","TRIAL_HANDOVER","STANDARDIZATION","CLOSE")); }
}
