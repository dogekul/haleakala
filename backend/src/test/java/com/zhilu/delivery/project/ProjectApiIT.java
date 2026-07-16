package com.zhilu.delivery.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:project-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
class ProjectApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;

  @BeforeEach
  void seedProject() {
    jdbc.update("delete from project_activity");
    jdbc.update("delete from project_artifact");
    jdbc.update("delete from template_instance");
    jdbc.update("delete from milestone");
    jdbc.update("delete from project_risk");
    jdbc.update("delete from stage_instance");
    jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from customer");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (610,'智鹿科技','ZHILU-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (610,610,'manager','交付负责人','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (610,610,'CRM','智鹿 CRM','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (610,610,'V3.0','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (610,610,'北方银行','ACTIVE')");
    projects.create(new CreateProjectCommand(610, "PRJ-610", "北方银行 CRM", 610,
        610, 610, 610, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 11, 30), "BLOCK"));
  }

  @Test
  void listAndDetailExposeProjectWorkspaceData() throws Exception {
    mvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("PRJ-610"))
        .andExpect(jsonPath("$[0].currentStage").value("START"));

    Long id = jdbc.queryForObject("select id from delivery_project where code='PRJ-610'", Long.class);
    mvc.perform(get("/api/v1/projects/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stages.length()").value(7))
        .andExpect(jsonPath("$.members[0].displayName").value("交付负责人"));
  }
}
