package com.zhilu.delivery.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
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
    "spring.datasource.url=jdbc:h2:mem:admin-system;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.agent.timeout-minutes=30"
})
@AutoConfigureMockMvc
class AdminSystemControllerTest {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from system_setting");
    jdbc.update("delete from user_role");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (400,'智鹿科技','ZHILU-SYSTEM')");
    jdbc.update("insert into organization(id,name,code) values (401,'其他组织','OTHER-SYSTEM')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (400,400,'admin','系统管理员','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (401,401,'other','其他管理员','ACTIVE')");
    jdbc.update("insert into audit_log(organization_id,actor_user_id,action,resource_type,"
        + "resource_id,trace_id,details_text) values "
        + "(400,400,'UPDATE','USER','410','TRACE-400','更新用户'),"
        + "(401,401,'UPDATE','USER','411','TRACE-401','其他组织记录')");
  }

  @Test
  void auditSearchIsFilteredAndPagedInsideCurrentOrganization() throws Exception {
    mvc.perform(get("/api/v1/admin/audit-logs")
            .param("keyword", "TRACE-400")
            .param("page", "1")
            .param("pageSize", "20")
            .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.items[0].traceId").value("TRACE-400"))
        .andExpect(jsonPath("$.items[0].actorName").value("系统管理员"));

    mvc.perform(get("/api/v1/admin/audit-log-facets").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions[0]").value("UPDATE"))
        .andExpect(jsonPath("$.resourceTypes[0]").value("USER"));
  }

  @Test
  void settingsUseDefaultsAndPersistOnlyValidatedValues() throws Exception {
    mvc.perform(get("/api/v1/admin/settings").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformName").value("智鹿交付"))
        .andExpect(jsonPath("$.agentTimeoutMinutes").value(30));

    mvc.perform(put("/api/v1/admin/settings").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"platformName\":\"智鹿交付中心\","
                + "\"environmentLabel\":\"演示环境\","
                + "\"timezone\":\"Asia/Shanghai\","
                + "\"supportEmail\":\"support@zhilu.local\","
                + "\"agentTimeoutMinutes\":45}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformName").value("智鹿交付中心"))
        .andExpect(jsonPath("$.agentTimeoutMinutes").value(45));

    mvc.perform(put("/api/v1/admin/settings").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"platformName\":\"智鹿交付\","
                + "\"environmentLabel\":\"生产环境\","
                + "\"timezone\":\"Mars/Base\","
                + "\"supportEmail\":\"\",\"agentTimeoutMinutes\":30}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    mvc.perform(put("/api/v1/admin/settings").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"platformName\":\"智鹿交付\","
                + "\"environmentLabel\":\"生产环境\","
                + "\"timezone\":\"Asia/Shanghai\",\"supportEmail\":\"\","
                + "\"agentTimeoutMinutes\":30,\"databaseUrl\":\"should-not-be-accepted\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  private RequestPostProcessor admin() {
    CurrentUser principal = new CurrentUser(400L, 400L, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Arrays.asList("system:manage", "audit:read"));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Arrays.asList(new SimpleGrantedAuthority("system:manage"),
            new SimpleGrantedAuthority("audit:read"))));
  }
}
