package com.zhilu.delivery.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class SecurityAccessTest {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedLoginUser() {
    jdbc.update("delete from user_role");
    jdbc.update("delete from role_permission");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    Long roleId = ensureRole();
    Long permissionId = ensurePermission();
    jdbc.update("insert into role_permission(role_id,permission_id) values (?,?)", roleId, permissionId);
    jdbc.update("insert into organization(id,name,code) values (200,'智鹿科技','ZHILU-SEC')");
    jdbc.update("insert into app_user(id,organization_id,username,password_hash,display_name,status) "
            + "values (200,200,'admin',?,'系统管理员','ACTIVE')",
        new BCryptPasswordEncoder().encode("secret123"));
    jdbc.update("insert into user_role(user_id,role_id) values (200,?)", roleId);
  }

  @Test
  void unauthenticatedApiReturnsStableUnauthorizedError() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void loginCreatesSessionThatCanReadCurrentUser() throws Exception {
    MvcResult login = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"secret123\"}"))
        .andExpect(status().isOk())
        .andReturn();
    HttpSession session = login.getRequest().getSession(false);

    mvc.perform(get("/api/v1/auth/me").session((org.springframework.mock.web.MockHttpSession) session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"));
  }

  @Test
  void nonAdminCannotReadUserAdministration() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(user("engineer").authorities(() -> "project:read")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void adminCanReadUserAdministration() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(user("admin").authorities(() -> "system:manage")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("admin"));
  }

  private Long ensureRole() {
    Integer count = jdbc.queryForObject("select count(*) from role where code='ADMIN'", Integer.class);
    if (count != null && count == 0) {
      jdbc.update("insert into role(code,name,built_in) values ('ADMIN','系统管理员',true)");
    }
    return jdbc.queryForObject("select id from role where code='ADMIN'", Long.class);
  }

  private Long ensurePermission() {
    Integer count = jdbc.queryForObject(
        "select count(*) from permission where code='system:manage'", Integer.class);
    if (count != null && count == 0) {
      jdbc.update("insert into permission(code,name,module) values ('system:manage','系统管理','system')");
    }
    return jdbc.queryForObject(
        "select id from permission where code='system:manage'", Long.class);
  }
}
