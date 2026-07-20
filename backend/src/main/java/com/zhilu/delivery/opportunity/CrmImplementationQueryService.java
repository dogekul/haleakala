package com.zhilu.delivery.opportunity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CrmImplementationQueryService {
  private final JdbcTemplate jdbc;

  public CrmImplementationQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> implementation(long organizationId) {
    return jdbc.query("select o.id opportunity_id,o.title opportunity_title,o.customer_id,"
            + "coalesce(c.name,o.customer_name_snapshot) customer_name,p.id project_id,"
            + "p.code project_code,p.name project_name,p.current_stage,p.status project_status,"
            + "p.manager_user_id,u.display_name manager_name,p.risk_level,p.planned_end_date,"
            + "p.updated_at,"
            + "(select count(*) from project_risk r where r.project_id=p.id and r.status='OPEN') open_risks,"
            + "(select count(*) from project_risk r where r.project_id=p.id and r.status='OPEN' "
            + "and r.risk_level='RED') red_risks,"
            + "(select count(*) from milestone m where m.project_id=p.id and m.status<>'COMPLETED' "
            + "and m.due_date<current_date) overdue_milestones,"
            + "(select m.name from milestone m where m.project_id=p.id and m.status<>'COMPLETED' "
            + "order by m.due_date,m.id limit 1) next_milestone_name,"
            + "(select m.due_date from milestone m where m.project_id=p.id and m.status<>'COMPLETED' "
            + "order by m.due_date,m.id limit 1) next_milestone_due_date "
            + "from sales_opportunity o join delivery_project p on p.id=o.project_id "
            + "left join customer c on c.id=o.customer_id "
            + "join app_user u on u.id=p.manager_user_id "
            + "where o.organization_id=? and p.organization_id=? and o.status='WON' "
            + "order by p.updated_at desc,p.id desc",
        (row, index) -> implementationRow(row), organizationId, organizationId);
  }

  public Map<String, Object> cockpit(long organizationId) {
    List<Map<String, Object>> items = implementation(organizationId);
    int active = 0;
    int red = 0;
    int overdue = 0;
    int closing = 0;
    for (Map<String, Object> item : items) {
      String stage = String.valueOf(item.get("projectStage"));
      if (!"CLOSE".equals(stage)) active++;
      if ("RED".equals(item.get("riskLevel"))) red++;
      overdue += ((Number) item.get("overdueMilestoneCount")).intValue();
      if ("STANDARDIZATION".equals(stage) || "CLOSE".equals(stage)) closing++;
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("implementationProjects", active);
    result.put("redRiskProjects", red);
    result.put("overdueMilestones", overdue);
    result.put("closingProjects", closing);
    result.put("items", items);
    return result;
  }

  private Map<String, Object> implementationRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("opportunityId", row.getLong("opportunity_id"));
    value.put("opportunityTitle", row.getString("opportunity_title"));
    value.put("customerId", row.getLong("customer_id"));
    value.put("customerName", row.getString("customer_name"));
    value.put("projectId", row.getLong("project_id"));
    value.put("projectCode", row.getString("project_code"));
    value.put("projectName", row.getString("project_name"));
    value.put("projectStage", row.getString("current_stage"));
    value.put("projectStatus", row.getString("project_status"));
    value.put("managerUserId", row.getLong("manager_user_id"));
    value.put("managerName", row.getString("manager_name"));
    value.put("riskLevel", row.getString("risk_level"));
    value.put("openRiskCount", row.getInt("open_risks"));
    value.put("redRiskCount", row.getInt("red_risks"));
    value.put("overdueMilestoneCount", row.getInt("overdue_milestones"));
    value.put("nextMilestoneName", row.getString("next_milestone_name"));
    value.put("nextMilestoneDueDate", row.getDate("next_milestone_due_date") == null
        ? null : row.getDate("next_milestone_due_date").toLocalDate());
    value.put("plannedEndDate", row.getDate("planned_end_date") == null
        ? null : row.getDate("planned_end_date").toLocalDate());
    value.put("updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
    value.put("health", health(row));
    return value;
  }

  private String health(ResultSet row) throws SQLException {
    if ("RED".equals(row.getString("risk_level")) || row.getInt("overdue_milestones") > 0) {
      return "RED";
    }
    return "YELLOW".equals(row.getString("risk_level")) || row.getInt("open_risks") > 0
        ? "YELLOW" : "GREEN";
  }
}
