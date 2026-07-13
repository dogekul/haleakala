package com.zhilu.delivery.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:dashboard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class DashboardQueryIT {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DashboardQueryService dashboard;

  @BeforeEach
  void seed() {
    jdbc.update("delete from project_risk"); jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project"); jdbc.update("delete from product_version");
    jdbc.update("delete from product"); jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (800,'智鹿','ZHILU-DASH')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (800,800,'one','成员一','ACTIVE'),(801,800,'two','成员二','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (800,800,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (800,800,'V1','RELEASED')");
    project(800, "P-GREEN", "绿色项目", 800, "GREEN", "START");
    project(801, "P-RED", "红色项目", 801, "RED", "CUSTOM_DEV");
    jdbc.update("insert into project_risk(project_id,title,category,probability,impact,risk_level) values (801,'上线延期','进度',5,5,'RED')");
  }

  @Test
  void aggregatesHealthAndFiltersProjectsByMembership() {
    CurrentUser member = new CurrentUser(800L, 800L, "one", "成员一",
        Collections.singletonList("DELIVERY_ENGINEER"), Collections.singletonList("dashboard:read"));
    CurrentUser pmo = new CurrentUser(800L, 800L, "pmo", "PMO",
        Arrays.asList("PMO"), Collections.singletonList("dashboard:read"));

    Map<String, Object> scoped = dashboard.summary(member, new DashboardFilter());
    Map<String, Object> global = dashboard.summary(pmo, new DashboardFilter());

    assertEquals(1L, ((Number) scoped.get("activeProjects")).longValue());
    assertEquals(2L, ((Number) global.get("activeProjects")).longValue());
    assertEquals(1L, ((Number) global.get("redProjects")).longValue());
    assertEquals(68, ((Number) global.get("healthScore")).intValue());
    assertEquals("P-RED", dashboard.projects(pmo, new DashboardFilter()).get(0).get("code"));
  }

  private void project(long id, String code, String name, long member, String risk, String stage) {
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,status,current_stage,risk_level,created_by,start_date,planned_end_date) values (?,?,?,?,?,800,800,800,'ACTIVE',?,?,800,?,?)",
        id, 800, code, name, "客户", stage, risk, java.sql.Date.valueOf(LocalDate.now()), java.sql.Date.valueOf(LocalDate.now().plusMonths(2)));
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (?,?,'ENGINEER')", id, member);
  }
}
