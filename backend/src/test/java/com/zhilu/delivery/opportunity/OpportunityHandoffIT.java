package com.zhilu.delivery.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:opportunity-handoff;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class OpportunityHandoffIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;

  @BeforeEach
  void seed() {
    jdbc.execute("set referential_integrity false");
    for (String table : Arrays.asList(
        "audit_log", "customer_operation", "opportunity_artifact", "opportunity_activity",
        "sales_opportunity", "project_activity", "project_artifact", "template_instance",
        "milestone", "project_risk", "project_member", "stage_instance", "delivery_project",
        "file_object", "customer", "product_version", "product", "app_user", "organization")) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("set referential_integrity true");
    jdbc.update("insert into organization(id,name,code) values (600,'智鹿科技','HANDOFF-A')");
    jdbc.update("insert into organization(id,name,code) values (601,'其他组织','HANDOFF-B')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (600,600,'delivery-owner','交付负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (601,601,'other-owner','其他负责人','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (600,600,'华东银行','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (602,600,'华南制造','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (601,601,'其他客户','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (600,600,600,'CRM','智鹿 CRM','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (601,601,601,'OTHER','其他产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (600,600,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (601,601,'OTHER-V1','RELEASED')");
    jdbc.update("insert into file_object(id,organization_id,object_key,original_name,mime_type,"
        + "size_bytes,checksum_sha256,created_by) values "
        + "(600,600,'handoff/contract.pdf','合同材料.pdf','application/pdf',100,'"
        + "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',600)");
  }

  @Test
  void atomicallyCreatesAProjectForAContractWin() throws Exception {
    long id = contractOpportunity("新建项目转交", 600);

    mvc.perform(post("/api/v1/opportunities/{id}/handoff", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(createRequest("PRJ-CRM-001", 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WON"))
        .andExpect(jsonPath("$.stage").value("CONTRACT"))
        .andExpect(jsonPath("$.projectId").isNumber())
        .andExpect(jsonPath("$.projectName").value("财务中台实施"));

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from delivery_project where organization_id=600 and code='PRJ-CRM-001' "
            + "and customer_id=600 and current_stage='START'", Integer.class));
    assertEquals(Integer.valueOf(7), jdbc.queryForObject(
        "select count(*) from stage_instance s join delivery_project p on p.id=s.project_id "
            + "where p.code='PRJ-CRM-001'", Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where resource_type='OPPORTUNITY' and action='HANDOFF'",
        Integer.class));
  }

  @Test
  void linksOnlyAnUnclaimedProjectForTheSameCustomerAndOrganization() throws Exception {
    ProjectView project = project("PRJ-EXISTING", 600, 600, 600);
    long id = contractOpportunity("关联已有项目", 600);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"mode\":\"LINK\",\"version\":0,\"projectId\":" + project.getId() + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(project.getId()))
        .andExpect(jsonPath("$.status").value("WON"));

    long second = contractOpportunity("重复关联项目", 600);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", second)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"mode\":\"LINK\",\"version\":0,\"projectId\":" + project.getId() + "}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("项目已关联其他商机"));
  }

  @Test
  void rejectsMismatchedAndCrossOrganizationProjects() throws Exception {
    ProjectView differentCustomer = project("PRJ-DIFFERENT", 600, 602, 600);
    long id = contractOpportunity("客户不一致", 600);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(linkRequest(differentCustomer.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("项目客户与商机客户不一致"));

    ProjectView other = project("PRJ-OTHER", 601, 601, 601);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(linkRequest(other.getId())))
        .andExpect(status().isNotFound());
  }

  @Test
  void rollsBackFailedCreationAndPreventsRepeatedHandoff() throws Exception {
    project("PRJ-DUPLICATE", 600, 600, 600);
    long duplicate = contractOpportunity("项目创建失败", 600);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", duplicate)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(createRequest("PRJ-DUPLICATE", 0)))
        .andExpect(status().isConflict());
    assertEquals("OPEN", jdbc.queryForObject(
        "select status from sales_opportunity where id=?", String.class, duplicate));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from delivery_project where organization_id=600", Integer.class));

    long success = contractOpportunity("防止重复转交", 600);
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", success)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(createRequest("PRJ-ONCE", 0)))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", success)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(createRequest("PRJ-TWICE", 1)))
        .andExpect(status().isConflict());
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from delivery_project where code='PRJ-TWICE'", Integer.class));
  }

  @Test
  void refusesHandoffWhenContractArtifactsAreMissing() throws Exception {
    long id = opportunity("合同材料缺失", 600, "CONTRACT");
    mvc.perform(post("/api/v1/opportunities/{id}/handoff", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(createRequest("PRJ-MISSING", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("缺少必需产出物")));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from delivery_project where code='PRJ-MISSING'", Integer.class));
  }

  private long contractOpportunity(String title, long customerId) {
    long id = opportunity(title, customerId, "CONTRACT");
    for (String type : Arrays.asList("AWARD_NOTICE", "CONTRACT", "REVIEW_MINUTES",
        "EMAIL_ARCHIVE", "SEALED_CONTRACT")) {
      boolean report = "REVIEW_MINUTES".equals(type);
      jdbc.update("insert into opportunity_artifact(organization_id,opportunity_id,stage_from,"
              + "artifact_type,title,content_markdown,file_id,created_by) "
              + "values (600,?,'CONTRACT',?,?,?,?,600)",
          id, type, type, report ? "评审通过" : null, report ? null : 600L);
    }
    return id;
  }

  private long opportunity(String title, long customerId, String stage) {
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,stage,amount,project_manager_user_id,created_by) "
            + "values (600,?,'华东银行',?,?,0,600,600)", customerId, title, stage);
    return jdbc.queryForObject("select id from sales_opportunity where title=?", Long.class, title);
  }

  private ProjectView project(String code, long organizationId, long customerId, long productId) {
    return projects.create(new CreateProjectCommand(organizationId, code, code, customerId,
        productId, productId, organizationId, organizationId, LocalDate.of(2026, 7, 16),
        LocalDate.of(2026, 10, 31), "BLOCK"));
  }

  private String createRequest(String code, long version) {
    return "{\"mode\":\"CREATE\",\"version\":" + version + ",\"project\":{"
        + "\"code\":\"" + code + "\",\"name\":\"财务中台实施\","
        + "\"productId\":600,\"productVersionId\":600,\"managerUserId\":600,"
        + "\"startDate\":\"2026-07-16\",\"plannedEndDate\":\"2026-10-31\","
        + "\"gateMode\":\"BLOCK\"}}";
  }

  private String linkRequest(long projectId) {
    return "{\"mode\":\"LINK\",\"version\":0,\"projectId\":" + projectId + "}";
  }

  private RequestPostProcessor writer() {
    List<String> permissions = Collections.singletonList("crm:write");
    CurrentUser user = new CurrentUser(600L, 600L, "delivery-owner", "交付负责人",
        Collections.singletonList("ADMIN"), permissions);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
