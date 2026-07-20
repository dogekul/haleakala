package com.zhilu.delivery.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.iam.service.CurrentUser;
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
    "spring.datasource.url=jdbc:h2:mem:opportunity-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class OpportunityApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void seed() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from customer_operation");
    jdbc.update("delete from opportunity_artifact");
    jdbc.update("delete from opportunity_activity");
    jdbc.update("delete from sales_opportunity");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from customer");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (400,'智鹿科技','CRM-A')");
    jdbc.update("insert into organization(id,name,code) values (401,'其他组织','CRM-B')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (400,400,'crm-admin','商务负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (402,400,'solution-owner','方案负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (403,400,'disabled-owner','停用用户','INACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (401,401,'other-user','其他用户','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (400,400,'华东银行','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (402,400,'停用客户','INACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (404,400,'华南制造','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (401,401,'其他客户','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (400,400,400,'CRM','智鹿 CRM','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (402,400,400,'ERP','智鹿 ERP','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,owner_user_id,code,name,status) "
        + "values (401,401,401,'OTHER','其他产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (400,400,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (402,402,'V2','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (401,401,'OTHER-V1','RELEASED')");
  }

  @Test
  void createsGetsAndFiltersAnOpportunityWithEnrichedNames() throws Exception {
    String body = mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":400,\"title\":\"财务中台升级\","
                + "\"note\":\"年度重点项目\",\"amount\":800000,"
                + "\"productId\":400,\"productVersionId\":400,"
                + "\"commercialOwnerUserId\":400,\"solutionOwnerUserId\":402,"
                + "\"projectManagerUserId\":400,\"operationOwnerUserId\":402}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stage").value("LEAD"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.customerName").value("华东银行"))
        .andExpect(jsonPath("$.productName").value("智鹿 CRM"))
        .andExpect(jsonPath("$.commercialOwnerName").value("商务负责人"))
        .andReturn().getResponse().getContentAsString();
    long id = json.readTree(body).get("id").asLong();

    mvc.perform(get("/api/v1/opportunities/{id}", id).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("财务中台升级"));
    mvc.perform(get("/api/v1/opportunities")
            .param("keyword", "财务")
            .param("customerId", "400")
            .param("productId", "400")
            .param("commercialOwnerUserId", "400")
            .param("stage", "LEAD")
            .param("status", "OPEN")
            .with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where resource_type='OPPORTUNITY' and action='CREATE'",
        Integer.class));
  }

  @Test
  void updatesOnlyBasicFieldsWithOptimisticLocking() throws Exception {
    long id = insertOpportunity(400, 400, "待更新商机");

    mvc.perform(put("/api/v1/opportunities/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":400,\"title\":\"已更新商机\",\"amount\":1200000,"
                + "\"stage\":\"CONTRACT\",\"status\":\"WON\",\"projectId\":999,"
                + "\"commercialOwnerUserId\":402,\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("已更新商机"))
        .andExpect(jsonPath("$.stage").value("LEAD"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.projectId").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(put("/api/v1/opportunities/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":400,\"title\":\"过期更新\",\"amount\":1,\"version\":0}"))
        .andExpect(status().isConflict());
  }

  @Test
  void neverChangesTheOpportunityCustomerThroughTheGeneralUpdateApi() throws Exception {
    long id = insertOpportunity(400, 400, "客户关联不可变");

    mvc.perform(put("/api/v1/opportunities/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":404,\"title\":\"试图换客户\",\"amount\":1,\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("商机客户不能修改"));
    assertEquals(Long.valueOf(400), jdbc.queryForObject(
        "select customer_id from sales_opportunity where id=?", Long.class, id));
  }

  @Test
  void rejectsInactiveAndCrossOrganizationReferences() throws Exception {
    String template = "{\"customerId\":%d,\"title\":\"无效商机\",\"amount\":1%s}";
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format(template, 402, "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("客户已停用，不能创建商机"));
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format(template, 401, "")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format(template, 400, ",\"commercialOwnerUserId\":401")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format(template, 400, ",\"commercialOwnerUserId\":403")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("负责人已停用"));
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format(template, 400, ",\"productId\":401,\"productVersionId\":401")))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsMismatchedProductVersionAndHidesOtherOrganizations() throws Exception {
    insertOpportunity(401, 401, "其他组织商机");
    mvc.perform(post("/api/v1/opportunities").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":400,\"title\":\"版本错配\",\"amount\":1,"
                + "\"productId\":400,\"productVersionId\":402}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("产品版本不属于所选产品"));
    mvc.perform(get("/api/v1/opportunities").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void returnsOnlyActiveOwnerOptionsAndSeparatesReadWritePermissions() throws Exception {
    mvc.perform(get("/api/v1/crm/owner-options").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(400))
        .andExpect(jsonPath("$[1].id").value(402));
    mvc.perform(get("/api/v1/opportunities").with(actor(400, 400, "customer:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/opportunities").with(reader()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":400,\"title\":\"不可写\",\"amount\":1}"))
        .andExpect(status().isForbidden());
  }

  private long insertOpportunity(long organizationId, long customerId, String title) {
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,amount,created_by) values (?,?,?,?,0,?)",
        organizationId, customerId, title.startsWith("其他") ? "其他客户" : "华东银行", title,
        organizationId);
    return jdbc.queryForObject("select id from sales_opportunity where organization_id=? and title=?",
        Long.class, organizationId, title);
  }

  private RequestPostProcessor reader() { return actor(400, 400, "crm:read"); }
  private RequestPostProcessor writer() { return actor(400, 400, "crm:write"); }

  private RequestPostProcessor actor(long id, long organizationId, String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(id, organizationId, "actor-" + id, "Actor " + id,
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
