package com.zhilu.delivery.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin-iam;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class AdminIamControllerTest {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedOrganization() {
    jdbc.update("delete from user_role");
    jdbc.update("delete from app_user");
    jdbc.update("delete from team");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (300,'智鹿科技','ZHILU-ADMIN')");
    jdbc.update("insert into organization(id,name,code) values (301,'其他组织','OTHER-ADMIN')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (300,300,'admin','系统管理员','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (301,301,'outsider','其他用户','ACTIVE')");
    jdbc.update("insert into user_role(user_id,role_id) values (300,1)");
  }

  @Test
  void adminCanCreateTeamAndLocalUserWithRole() throws Exception {
    mvc.perform(post("/api/v1/admin/teams")
            .with(admin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"交付一组\",\"code\":\"DELIVERY-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("交付一组"));

    Long teamId = jdbc.queryForObject("select id from team where code='DELIVERY-1'", Long.class);
    mvc.perform(post("/api/v1/admin/users")
            .with(admin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"primaryTeamId\":" + teamId
                + ",\"username\":\"wang\",\"password\":\"secret123\","
                + "\"displayName\":\"小王\",\"email\":\"wang@example.com\","
                + "\"roleCodes\":[\"DELIVERY_ENGINEER\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("wang"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void adminOnlyReadsAndEditsUsersAndTeamsInOwnOrganization() throws Exception {
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (310,300,'交付一组','DELIVERY-1')");
    jdbc.update("insert into app_user(id,organization_id,primary_team_id,username,display_name,status) "
        + "values (310,300,310,'wang','小王','ACTIVE')");
    jdbc.update("insert into user_role(user_id,role_id) values (310,4)");

    mvc.perform(get("/api/v1/admin/users").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[1].username").value("wang"))
        .andExpect(jsonPath("$[1].primaryTeamName").value("交付一组"))
        .andExpect(jsonPath("$[1].roles[0]").value("DELIVERY_ENGINEER"));

    mvc.perform(put("/api/v1/admin/users/310").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"王工\",\"email\":\"wang@zhilu.local\","
                + "\"primaryTeamId\":310,\"roleCodes\":[\"TECH_MANAGER\"],"
                + "\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("王工"))
        .andExpect(jsonPath("$.roles[0]").value("TECH_MANAGER"));

    mvc.perform(put("/api/v1/admin/teams/310").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"交付先锋组\",\"code\":\"DELIVERY-1\",\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("交付先锋组"));
  }

  @Test
  void adminCanReadRolesAndReplaceRolePermissions() throws Exception {
    mvc.perform(get("/api/v1/admin/roles")
            .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6));

    mvc.perform(put("/api/v1/admin/roles/6/permissions")
            .with(admin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionCodes\":[\"dashboard:read\",\"standardization:read\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));
  }

  @Test
  void adminCanReadPermissionCatalogButCannotRemoveOwnManagementPermission() throws Exception {
    mvc.perform(get("/api/v1/admin/permissions").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").isNotEmpty())
        .andExpect(jsonPath("$[0].name").isNotEmpty())
        .andExpect(jsonPath("$[0].module").isNotEmpty());

    mvc.perform(put("/api/v1/admin/roles/1/permissions")
            .with(admin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionCodes\":[\"dashboard:read\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  private RequestPostProcessor admin() {
    CurrentUser principal = new CurrentUser(300L, 300L, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority("system:manage"))));
  }
}
