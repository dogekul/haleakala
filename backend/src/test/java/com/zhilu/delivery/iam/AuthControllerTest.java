package com.zhilu.delivery.iam;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.fail;

import com.zhilu.delivery.iam.service.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:iam;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedLocalAdmin() {
    jdbc.update("delete from user_role");
    jdbc.update("delete from role_permission");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");

    Long roleId = ensureRole("ADMIN", "系统管理员");
    Long permissionId = ensurePermission("system:manage", "系统管理", "system");
    jdbc.update("insert into role_permission(role_id, permission_id) values (?, ?)", roleId, permissionId);
    jdbc.update("insert into organization(id,name,code) values (100,'智鹿科技','ZHILU')");
    jdbc.update("insert into app_user(id,organization_id,username,password_hash,display_name,email,status) "
            + "values (100,100,'admin',?,'系统管理员','admin@example.com','ACTIVE')",
        new BCryptPasswordEncoder().encode("secret123"));
    jdbc.update("insert into user_role(user_id,role_id) values (100,?)", roleId);
  }

  @Test
  void localLoginReturnsCurrentUserRolesAndPermissions() throws Exception {
    MvcResult result = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"secret123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.displayName").value("系统管理员"))
        .andExpect(jsonPath("$.roles", hasItem("ADMIN")))
        .andExpect(jsonPath("$.permissions", hasItem("system:manage")))
        .andReturn();

    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    SecurityContext context = (SecurityContext) session.getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertNotNull(context, "login must establish the security context in the same session");
    CurrentUser principal = (CurrentUser) context.getAuthentication().getPrincipal();
    assertEquals("admin", principal.getUsername());
  }

  @Test
  void invalidPasswordReturnsUnauthorizedError() throws Exception {
    try {
      mvc.perform(post("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
          .andExpect(jsonPath("$.traceId").isNotEmpty());
    } catch (org.springframework.web.util.NestedServletException error) {
      fail("Bad credentials must be translated to the stable API error contract");
    }
  }

  private Long ensureRole(String code, String name) {
    Integer count = jdbc.queryForObject("select count(*) from role where code=?", Integer.class, code);
    if (count != null && count == 0) {
      jdbc.update("insert into role(code,name,built_in) values (?,?,true)", code, name);
    }
    return jdbc.queryForObject("select id from role where code=?", Long.class, code);
  }

  private Long ensurePermission(String code, String name, String module) {
    Integer count = jdbc.queryForObject(
        "select count(*) from permission where code=?", Integer.class, code);
    if (count != null && count == 0) {
      jdbc.update("insert into permission(code,name,module) values (?,?,?)", code, name, module);
    }
    return jdbc.queryForObject("select id from permission where code=?", Long.class, code);
  }
}
