package com.zhilu.delivery.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.Arrays;
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
    "spring.datasource.url=jdbc:h2:mem:delivery-tracking;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class DeliveryTrackingApiIT {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  @Autowired private MockMvc mvc;

  private long projectId;
  private CurrentUser manager;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {"delivery_tracking_item", "project_activity",
        "project_artifact", "template_instance", "milestone", "project_risk",
        "stage_instance", "project_member", "delivery_project", "document_job",
        "customer", "product_version", "product", "app_user", "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (810,'智鹿','TRACKING')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (810,810,'manager','项目经理','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (810,810,'CP','消保合规','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (810,810,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (810,810,'华东银行','ACTIVE')");
    manager = new CurrentUser(810L, 810L, "manager", "项目经理",
        Arrays.asList("PROJECT_MANAGER"), Arrays.asList("project:read", "project:write"));
    projectId = projects.create(new CreateProjectCommand(810, "消保合规交付", 810,
        810, 810, 810, 810, LocalDate.now(), null, "BLOCK")).getId();
  }

  @Test
  void createsListsAndUpdatesProjectScopedTrackingItems() throws Exception {
    String body = "{\"originalRequest\":\"催收策略增加失联修复判断节点\","
        + "\"classification\":\"ENHANCEMENT\",\"deliveryEnd\":\"BACKEND\","
        + "\"featurePoint\":\"策略节点-失联修复\",\"complexity\":\"M\","
        + "\"productDependency\":\"策略节点 SPI\",\"dependencyStatus\":\"READY\","
        + "\"extensionPoint\":\"strategy-node-plugin\",\"estimatedDays\":5,"
        + "\"reusableLevel\":\"SEED\",\"status\":\"IN_PROGRESS\","
        + "\"ownerUserId\":810}";
    mvc.perform(post("/api/v1/projects/{id}/delivery-tracking", projectId)
            .with(auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.itemCode").value(projectId + "-001"))
        .andExpect(jsonPath("$.ownerName").value("项目经理"));

    mvc.perform(get("/api/v1/projects/{id}/delivery-tracking", projectId).with(auth()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].estimatedDays").value(5));

    long itemId = jdbc.queryForObject("select id from delivery_tracking_item", Long.class);
    mvc.perform(put("/api/v1/projects/{id}/delivery-tracking/{itemId}", projectId, itemId)
            .with(auth()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"estimatedDays\":5", "\"estimatedDays\":5,\"actualDays\":7")
                .replace("\"ownerUserId\":810", "\"ownerUserId\":810,\"version\":0")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.deviationPercent").value(40));
  }

  private RequestPostProcessor auth() {
    return authentication(new UsernamePasswordAuthenticationToken(manager, null,
        Arrays.asList(new SimpleGrantedAuthority("project:read"),
            new SimpleGrantedAuthority("project:write"))));
  }
}
