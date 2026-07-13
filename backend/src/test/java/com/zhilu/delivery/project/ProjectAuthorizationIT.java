package com.zhilu.delivery.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:project-auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class ProjectAuthorizationIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  private long projectId;
  private long riskId;
  private long templateId;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {"project_activity", "project_artifact", "template_instance",
        "milestone", "project_risk", "stage_instance", "project_member", "delivery_project",
        "product_version", "product", "app_user", "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(620,'智鹿','ZHILU-PROJECT-AUTH'),(621,'其他组织','OTHER-PROJECT-AUTH')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(620,620,'manager','交付负责人','ACTIVE'),"
        + "(621,620,'outsider','非项目成员','ACTIVE'),"
        + "(622,620,'pmo','PMO','ACTIVE'),"
        + "(623,621,'other-admin','其他组织管理员','ACTIVE')");
    jdbc.update("insert into product(id,code,name,status) values (620,'ERP-AUTH','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (620,620,'V1','ACTIVE')");
    ProjectView project = projects.create(new CreateProjectCommand(620, "PRJ-AUTH", "安全项目",
        "客户", 620, 620, 620, 620, LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 12, 31), "BLOCK"));
    projectId = project.getId();
    jdbc.update("insert into project_risk(project_id,title,category,probability,impact,risk_level,status) "
        + "values (?,'现有风险','DELIVERY',2,2,'GREEN','OPEN')", projectId);
    riskId = jdbc.queryForObject("select id from project_risk where project_id=?", Long.class, projectId);
    jdbc.update("insert into template_instance(project_id,template_key,title,content_markdown,status,updated_by) "
        + "values (?,'existing','现有模板','# existing','DRAFT',620)", projectId);
    templateId = jdbc.queryForObject(
        "select id from template_instance where project_id=?", Long.class, projectId);
  }

  @Test
  void projectWriterWhoIsNotMemberCannotMutateAnyProjectResource() throws Exception {
    RequestPostProcessor outsider = actor(621, 620, "DELIVERY_ENGINEER", "project:write");

    mvc.perform(post("/api/v1/projects/{id}/advance", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStage\":\"REQUIREMENT\",\"mode\":\"WARNING\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/projects/{id}/stages/START/gate", projectId)
            .with(outsider).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"READY\",\"message\":\"guessed\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/projects/{id}/members", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":621,\"projectRole\":\"ENGINEER\",\"allocationPercent\":100}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/projects/{id}/risks", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"guessed\",\"category\":\"DELIVERY\",\"probability\":2,\"impact\":2}"))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/projects/{id}/risks/{riskId}", projectId, riskId)
            .with(outsider).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"CLOSED\",\"mitigation\":\"guessed\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/projects/{id}/milestones", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"guessed\",\"dueDate\":\"2026-12-01\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/projects/{id}/templates", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"guessed\",\"title\":\"guessed\","
                + "\"contentMarkdown\":\"# guessed\",\"status\":\"DRAFT\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/projects/{id}/templates/{templateId}", projectId, templateId)
            .with(outsider).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateKey\":\"existing\",\"title\":\"guessed\","
                + "\"contentMarkdown\":\"# guessed\",\"status\":\"DRAFT\",\"version\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/projects/{id}/settings", projectId).with(outsider).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"guessed\",\"status\":\"ACTIVE\",\"riskLevel\":\"GREEN\","
                + "\"gateMode\":\"BLOCK\",\"version\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void pmoInOrganizationCanMutateProjectWithoutMembership() throws Exception {
    mvc.perform(post("/api/v1/projects/{id}/risks", projectId)
            .with(actor(622, 620, "PMO", "project:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"PMO risk\",\"category\":\"DELIVERY\","
                + "\"probability\":2,\"impact\":2}"))
        .andExpect(status().isCreated());
  }

  @Test
  void blockingGateCannotBeBypassedByWarningInRequestBody() throws Exception {
    jdbc.update("update stage_instance set gate_status='BLOCKING',gate_message='检查未通过' "
        + "where project_id=? and stage_code='START'", projectId);

    mvc.perform(post("/api/v1/projects/{id}/advance", projectId)
            .with(actor(620, 620, "DELIVERY_MANAGER", "project:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStage\":\"REQUIREMENT\",\"mode\":\"WARNING\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void adminFromAnotherOrganizationCannotUseBroadRoleAcrossTenantBoundary() throws Exception {
    mvc.perform(post("/api/v1/projects/{id}/risks", projectId)
            .with(actor(623, 621, "ADMIN", "project:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"cross tenant\",\"category\":\"DELIVERY\","
                + "\"probability\":2,\"impact\":2}"))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor actor(long id, long organizationId, String role, String permission) {
    CurrentUser principal = new CurrentUser(id, organizationId, "actor-" + id, "Actor " + id,
        Collections.singletonList(role), Collections.singletonList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission))));
  }
}
