package com.zhilu.delivery.operation;

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
    "spring.datasource.url=jdbc:h2:mem:customer-operation-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class CustomerOperationApiIT {
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
    jdbc.update("delete from project_activity");
    jdbc.update("delete from stage_instance");
    jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from customer");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (800,'智鹿科技','OPS-A')");
    jdbc.update("insert into organization(id,name,code) values (801,'其他组织','OPS-B')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (800,800,'operation-owner','运营负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (802,800,'disabled-owner','停用负责人','INACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (801,801,'other-owner','其他负责人','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (800,800,'华东银行','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (802,800,'停用客户','INACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (801,801,'其他客户','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (800,800,'CRM','智鹿 CRM','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (801,801,'OTHER','其他产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (800,800,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (801,801,'V1','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,customer_id,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (800,800,'PRJ-800','华东银行项目','华东银行',800,800,800,800,800)");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,customer_id,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (801,801,'PRJ-801','其他项目','其他客户',801,801,801,801,801)");
    jdbc.update("insert into sales_opportunity(id,organization_id,customer_id,customer_name_snapshot,"
        + "title,amount,stage,status,project_id,operation_owner_user_id,created_by) "
        + "values (800,800,800,'华东银行','财务中台升级',100,'CONTRACT','WON',800,800,800)");
    jdbc.update("insert into sales_opportunity(id,organization_id,customer_id,customer_name_snapshot,"
        + "title,amount,stage,status,project_id,created_by) "
        + "values (801,801,801,'其他客户','其他商机',100,'CONTRACT','WON',801,801)");
  }

  @Test
  void createsListsAndGetsAnOperationWithItsSourceChain() throws Exception {
    String body = mvc.perform(post("/api/v1/operations").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"华东银行持续运营\","
                + "\"ownerUserId\":800,\"projectId\":800,\"opportunityId\":800}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stage").value("MAINTENANCE"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.customerName").value("华东银行"))
        .andExpect(jsonPath("$.ownerName").value("运营负责人"))
        .andExpect(jsonPath("$.project.name").value("华东银行项目"))
        .andExpect(jsonPath("$.opportunity.title").value("财务中台升级"))
        .andReturn().getResponse().getContentAsString();
    long id = json.readTree(body).get("id").asLong();

    mvc.perform(get("/api/v1/operations").param("keyword", "持续")
            .param("customerId", "800").param("ownerUserId", "800")
            .param("stage", "MAINTENANCE").param("status", "OPEN").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/api/v1/operations/{id}", id).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where resource_type='CUSTOMER_OPERATION' and action='CREATE'",
        Integer.class));
  }

  @Test
  void updatesWithOptimisticLockAndAdvancesLinearlyUntilClosed() throws Exception {
    long id = createBasicOperation();
    mvc.perform(put("/api/v1/operations/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"更新后的运营\",\"ownerUserId\":800,\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("更新后的运营"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(put("/api/v1/operations/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"过期更新\",\"version\":0}"))
        .andExpect(status().isConflict());

    long version = 1;
    String[] stages = {"OPERATING", "REPURCHASE", "CLOSED"};
    for (String stage : stages) {
      String response = mvc.perform(post("/api/v1/operations/{id}/advance", id)
              .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
              .content("{\"version\":" + version + "}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.stage").value(stage))
          .andReturn().getResponse().getContentAsString();
      version = json.readTree(response).get("version").asLong();
    }
    mvc.perform(post("/api/v1/operations/{id}/advance", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
        .andExpect(status().isConflict());
    mvc.perform(put("/api/v1/operations/{id}", id).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"关闭后更新\",\"version\":" + version + "}"))
        .andExpect(status().isConflict());
  }

  @Test
  void validatesReferencesOrganizationCustomerAndPermissions() throws Exception {
    mvc.perform(post("/api/v1/operations").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":802,\"title\":\"停用客户运营\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/operations").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"跨组织项目\",\"projectId\":801}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/operations").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"跨客户商机\",\"opportunityId\":801}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/operations").with(actor("customer:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/operations").with(reader()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"只读不可写\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/operations").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private long createBasicOperation() throws Exception {
    String body = mvc.perform(post("/api/v1/operations").with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":800,\"title\":\"基础运营\",\"ownerUserId\":800}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private RequestPostProcessor reader() { return actor("crm:read"); }
  private RequestPostProcessor writer() { return actor("crm:write"); }

  private RequestPostProcessor actor(String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(800L, 800L, "operation-owner", "运营负责人",
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
