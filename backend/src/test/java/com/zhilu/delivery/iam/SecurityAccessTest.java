package com.zhilu.delivery.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpSession;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.storage.ObjectStorage;
import java.util.Collections;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

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
  @MockBean private ObjectStorage objects;

  @BeforeEach
  void seedLoginUser() {
    jdbc.update("delete from file_version");
    jdbc.update("delete from file_object");
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
  void existingSessionImmediatelyLosesRevokedPermission() throws Exception {
    MvcResult login = login();
    org.springframework.mock.web.MockHttpSession session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
    jdbc.update("delete from role_permission");

    mvc.perform(get("/api/v1/admin/users").session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void existingSessionImmediatelyReflectsRevokedRole() throws Exception {
    MvcResult login = login();
    org.springframework.mock.web.MockHttpSession session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
    jdbc.update("delete from user_role where user_id=200");

    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles.length()").value(0))
        .andExpect(jsonPath("$.permissions.length()").value(0));
  }

  @Test
  void disabledUserCannotKeepUsingExistingSession() throws Exception {
    MvcResult login = login();
    org.springframework.mock.web.MockHttpSession session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
    jdbc.update("update app_user set status='DISABLED' where id=200");

    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotReadUserAdministration() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(user("engineer").authorities(() -> "project:read")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void authenticatedUserCanReadOrganizationBrandingSettings() throws Exception {
    CurrentUser engineer = new CurrentUser(200L, 200L, "admin", "交付工程师",
        Collections.singletonList("DELIVERY_ENGINEER"),
        Collections.singletonList("project:read"));
    mvc.perform(get("/api/v1/runtime-settings").with(authentication(
            new UsernamePasswordAuthenticationToken(engineer, null,
                Collections.singletonList(new SimpleGrantedAuthority("project:read"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformName").value("智鹿交付"));
  }

  @Test
  void adminCanReadUserAdministration() throws Exception {
    CurrentUser admin = new CurrentUser(200L, 200L, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));
    mvc.perform(get("/api/v1/admin/users").with(authentication(
            new UsernamePasswordAuthenticationToken(admin, null,
                Collections.singletonList(new SimpleGrantedAuthority("system:manage"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("admin"));
  }

  @Test
  void businessUserCanReadProductsButCannotWriteCatalog() throws Exception {
    mvc.perform(get("/api/v1/products")
            .with(user("engineer").authorities(() -> "project:read")))
        .andExpect(status().isOk());

    mvc.perform(post("/api/v1/products")
            .with(user("engineer").authorities(() -> "project:read"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"NO-WRITE\",\"name\":\"不可写产品\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void classificationActionsUseClassifyPermissionInsteadOfRequirementWrite() throws Exception {
    mvc.perform(post("/api/v1/requirements/999/classify")
            .with(actor("requirement:write")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/requirements/999/confirm")
            .with(actor("requirement:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"level\":\"L1\",\"overrideReason\":\"reviewed\"}"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/requirements/999/classify")
            .with(actor("requirement:classify")).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/requirements/999/confirm")
            .with(actor("requirement:classify")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"level\":\"L1\",\"overrideReason\":\"reviewed\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void agentPostAndCancelUseExecutePermissionWhileGetKeepsProjectRead() throws Exception {
    mvc.perform(post("/api/v1/projects/999/agent-jobs")
            .with(actor("project:write")).with(csrf())
            .header("Idempotency-Key", "security-test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"skill\":\"requirement-classify\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/agent-jobs/999/cancel")
            .with(actor("project:write")).with(csrf()))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/projects/999/agent-jobs")
            .with(actor("agent:execute")).with(csrf())
            .header("Idempotency-Key", "security-test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"skill\":\"requirement-classify\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/agent-jobs/999/cancel")
            .with(actor("agent:execute")).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/agent-jobs/999").with(actor("project:read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void auditReadDoesNotGrantOtherAdministrationAccess() throws Exception {
    mvc.perform(get("/api/v1/admin/audit-logs").with(actor("audit:read")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/settings").with(actor("audit:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void fileWritesRequireFileWritePermission() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "security.txt", "text/plain", "security".getBytes("UTF-8"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .multipart("/api/v1/files").file(file)
            .with(actor("project:write")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .multipart("/api/v1/files/999/versions").file(file)
            .with(actor("project:write")).with(csrf()))
        .andExpect(status().isForbidden());
  }

  private Long ensureRole() {
    Integer count = jdbc.queryForObject("select count(*) from role where code='ADMIN'", Integer.class);
    if (count != null && count == 0) {
      jdbc.update("insert into role(code,name,built_in) values ('ADMIN','系统管理员',true)");
    }
    return jdbc.queryForObject("select id from role where code='ADMIN'", Long.class);
  }

  private MvcResult login() throws Exception {
    return mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"secret123\"}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor actor(
      String permission) {
    CurrentUser principal = new CurrentUser(200L, 200L, "admin", "系统管理员",
        Arrays.asList("DELIVERY_ENGINEER"), Arrays.asList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission))));
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
