package com.zhilu.delivery.opportunity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.CreateProjectCommand;
import com.zhilu.delivery.project.ProjectService;
import com.zhilu.delivery.project.ProjectView;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:crm-implementation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class CrmImplementationQueryIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  private long opportunityId;

  @BeforeEach
  void seed() {
    jdbc.execute("set referential_integrity false");
    for (String table : Arrays.asList(
        "customer_operation", "opportunity_artifact", "opportunity_activity", "sales_opportunity",
        "project_activity", "project_artifact", "template_instance", "milestone", "project_risk",
        "project_member", "stage_instance", "delivery_project", "customer", "product_version",
        "product", "app_user", "organization")) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("set referential_integrity true");
    organization(700, "CRM-IMPL-A", "华东银行", "CRM", "V1");
    organization(701, "CRM-IMPL-B", "其他客户", "OTHER", "OTHER-V1");
    ProjectView project = projects.create(new CreateProjectCommand(700, "PRJ-IMPL-001",
        "财务中台实施", 700, 700, 700, 700, 700, LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31), "BLOCK"));
    jdbc.update("update delivery_project set current_stage='CUSTOM_DEV',risk_level='RED' where id=?",
        project.getId());
    jdbc.update("update stage_instance set status='PENDING' where project_id=?", project.getId());
    jdbc.update("update stage_instance set status='ACTIVE',started_at=current_timestamp "
        + "where project_id=? and stage_code='CUSTOM_DEV'", project.getId());
    jdbc.update("insert into project_risk(project_id,title,category,probability,impact,risk_level,status) "
        + "values (?,'数据迁移风险','TECHNICAL',5,5,'RED','OPEN')", project.getId());
    jdbc.update("insert into milestone(project_id,name,due_date,status,progress) "
        + "values (?,'首轮上线','2000-01-01','PENDING',60)", project.getId());
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,amount,stage,status,project_id,created_by) "
            + "values (700,700,'华东银行','财务中台升级',800000,'CONTRACT','WON',?,700)",
        project.getId());
    opportunityId = jdbc.queryForObject(
        "select id from sales_opportunity where title='财务中台升级'", Long.class);

    ProjectView other = projects.create(new CreateProjectCommand(701, "PRJ-OTHER-001",
        "其他项目", 701, 701, 701, 701, 701, LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31), "BLOCK"));
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,amount,stage,status,project_id,created_by) "
            + "values (701,701,'其他客户','其他组织商机',1,'CONTRACT','WON',?,701)", other.getId());
  }

  @Test
  void readsProjectStageRiskAndMilestonesWithoutDuplicatingImplementationState() throws Exception {
    mvc.perform(get("/api/v1/crm/implementation").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].opportunityTitle").value("财务中台升级"))
        .andExpect(jsonPath("$[0].projectStage").value("CUSTOM_DEV"))
        .andExpect(jsonPath("$[0].riskLevel").value("RED"))
        .andExpect(jsonPath("$[0].openRiskCount").value(1))
        .andExpect(jsonPath("$[0].redRiskCount").value(1))
        .andExpect(jsonPath("$[0].overdueMilestoneCount").value(1))
        .andExpect(jsonPath("$[0].nextMilestoneName").value("首轮上线"));

    mvc.perform(get("/api/v1/crm/implementation-cockpit").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.implementationProjects").value(1))
        .andExpect(jsonPath("$.redRiskProjects").value(1))
        .andExpect(jsonPath("$.overdueMilestones").value(1))
        .andExpect(jsonPath("$.closingProjects").value(0))
        .andExpect(jsonPath("$.items[0].projectStage").value("CUSTOM_DEV"));
  }

  @Test
  void returnsCustomerOpportunityProjectAndOptionalOperationAsOneFullLink() throws Exception {
    mvc.perform(get("/api/v1/opportunities/{id}/full-link", opportunityId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customer.name").value("华东银行"))
        .andExpect(jsonPath("$.opportunity.title").value("财务中台升级"))
        .andExpect(jsonPath("$.project.name").value("财务中台实施"))
        .andExpect(jsonPath("$.project.stage").value("CUSTOM_DEV"))
        .andExpect(jsonPath("$.operation").doesNotExist());

    jdbc.update("insert into customer_operation(organization_id,customer_id,customer_name_snapshot,"
            + "title,stage,status,project_id,opportunity_id,created_by) "
            + "select 700,700,'华东银行','财务中台运营','MAINTENANCE','OPEN',project_id,id,700 "
            + "from sales_opportunity where id=?", opportunityId);
    mvc.perform(get("/api/v1/opportunities/{id}/full-link", opportunityId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operation.title").value("财务中台运营"))
        .andExpect(jsonPath("$.operation.stage").value("MAINTENANCE"));
  }

  @Test
  void hidesOtherOrganizationImplementationAndFullLinks() throws Exception {
    long other = jdbc.queryForObject(
        "select id from sales_opportunity where organization_id=701", Long.class);
    mvc.perform(get("/api/v1/opportunities/{id}/full-link", other).with(reader()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/crm/implementation").with(actor(701, 701, "crm:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].opportunityTitle").value("其他组织商机"));
  }

  private void organization(long id, String code, String customer, String product, String version) {
    jdbc.update("insert into organization(id,name,code) values (?,'组织',?)", id, code);
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (?,?,?,'交付负责人','ACTIVE')", id, id, "user-" + id);
    jdbc.update("insert into customer(id,organization_id,name,status) values (?,?,?,'ACTIVE')",
        id, id, customer);
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (?,?,?,?,?,'ACTIVE')", id, id, id, product, product);
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (?,?,?,'RELEASED')", id, id, version);
  }

  private RequestPostProcessor reader() { return actor(700, 700, "crm:read"); }
  private RequestPostProcessor actor(long id, long organizationId, String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(id, organizationId, "user-" + id, "交付负责人",
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
