package com.zhilu.delivery.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    "spring.datasource.url=jdbc:h2:mem:customer-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class CustomerApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedOrganizations() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from customer");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (300,'智鹿科技','CUSTOMER-A')");
    jdbc.update("insert into organization(id,name,code) values (301,'其他组织','CUSTOMER-B')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (300,300,'customer-admin','客户管理员','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (301,301,'other-admin','其他管理员','ACTIVE')");
  }

  @Test
  void createsGeneratedIdAndFiltersCustomersWithoutCodes() throws Exception {
    mvc.perform(post("/api/v1/customers").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华东银行\",\"shortName\":\"华东行\","
                + "\"contactName\":\"王经理\",\"phone\":\"13800000000\","
                + "\"email\":\"wang@example.com\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.projectCount").value(0));

    mvc.perform(get("/api/v1/customers?keyword=王经理&status=ACTIVE")
            .with(actor(300, 300, "customer:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("华东银行"))
        .andExpect(jsonPath("$[0].shortName").value("华东行"));
  }

  @Test
  void updatesWithOptimisticLockAndRejectsDuplicateNames() throws Exception {
    jdbc.update("insert into customer(id,organization_id,name,status) values (300,300,'华东银行','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (301,300,'华南银行','ACTIVE')");

    mvc.perform(put("/api/v1/customers/300").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华东银行\",\"contactName\":\"李经理\","
                + "\"status\":\"INACTIVE\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contactName").value("李经理"))
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(put("/api/v1/customers/300").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华东银行\",\"status\":\"ACTIVE\",\"version\":0}"))
        .andExpect(status().isConflict());

    mvc.perform(post("/api/v1/customers").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华南银行\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateRequiresExplicitStatusAndVersion() throws Exception {
    jdbc.update("insert into customer(id,organization_id,name,status) values (305,300,'华北银行','INACTIVE')");

    mvc.perform(put("/api/v1/customers/305").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华北银行\",\"version\":0}"))
        .andExpect(status().isBadRequest());

    mvc.perform(put("/api/v1/customers/305").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"华北银行\",\"status\":\"INACTIVE\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void hidesCustomersFromOtherOrganizations() throws Exception {
    jdbc.update("insert into customer(id,organization_id,name,status) values (310,301,'其他客户','ACTIVE')");

    mvc.perform(get("/api/v1/customers").with(actor(300, 300, "customer:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mvc.perform(put("/api/v1/customers/310").with(actor(300, 300, "customer:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"越权修改\",\"status\":\"ACTIVE\",\"version\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void separatesCustomerReadAndWritePermissions() throws Exception {
    mvc.perform(get("/api/v1/customers").with(actor(300, 300, "project:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/customers").with(actor(300, 300, "customer:read")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"不可写客户\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor actor(long id, long organizationId, String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(id, organizationId, "actor-" + id, "Actor " + id,
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
