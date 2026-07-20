package com.zhilu.delivery.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.audit.AuditService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
  @SpyBean private AuditService audit;

  @BeforeEach
  void seedOrganization() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from sso_identity");
    jdbc.update("delete from user_team");
    jdbc.update("delete from user_role");
    jdbc.update("delete from role_permission where role_id>=100");
    jdbc.update("delete from role where id>=100");
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

    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=300 and action in "
            + "('TEAM_CREATED','USER_CREATED')", Integer.class));
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
  void teamHierarchyCannotFormAnIndirectCycle() throws Exception {
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (310,300,'交付一组','DELIVERY-1')");
    jdbc.update("insert into team(id,organization_id,parent_id,name,code) "
        + "values (311,300,310,'交付二组','DELIVERY-2')");

    mvc.perform(put("/api/v1/admin/teams/310").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parentId\":311,\"name\":\"交付一组\","
                + "\"code\":\"DELIVERY-1\",\"enabled\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
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

  @Test
  void managementWriteRollsBackWhenAuditRecordingFails() {
    doThrow(new IllegalStateException("audit unavailable")).when(audit).record(
        anyLong(), any(), anyString(), anyString(), anyString(), anyString());

    assertThrows(Exception.class, () -> mvc.perform(post("/api/v1/admin/teams")
        .with(admin())
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"交付二组\",\"code\":\"DELIVERY-2\"}")));

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from team where organization_id=300 and code='DELIVERY-2'",
        Integer.class));
  }

  @Test
  void adminCanDeleteUnreferencedUserTeamAndCustomRole() throws Exception {
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (310,300,'临时团队','TEMP-TEAM')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (310,300,'temporary','临时用户','ACTIVE')");
    jdbc.update("insert into user_role(user_id,role_id) values (310,4)");
    jdbc.update("insert into user_team(user_id,team_id) values (310,310)");
    jdbc.update("insert into sso_identity(user_id,provider,subject,email) "
        + "values (310,'oidc','temporary-subject','temporary@example.com')");
    jdbc.update("insert into role(id,code,name,description,built_in) "
        + "values (100,'CUSTOM_TEMP','临时角色','用于删除测试',false)");
    jdbc.update("insert into role_permission(role_id,permission_id) values (100,2)");

    mvc.perform(delete("/api/v1/admin/users/310").with(admin()).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/admin/teams/310").with(admin()).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/admin/roles/100").with(admin()).with(csrf()))
        .andExpect(status().isNoContent());

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from app_user where id=310", Integer.class));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from team where id=310", Integer.class));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from role where id=100", Integer.class));
    assertEquals(Integer.valueOf(3), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=300 and action in "
            + "('USER_DELETED','TEAM_DELETED','ROLE_DELETED')", Integer.class));
  }

  @Test
  void protectedUsersTeamsAndRolesReturnConflict() throws Exception {
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (310,300,'有成员团队','TEAM-WITH-USER')");
    jdbc.update("insert into app_user(id,organization_id,primary_team_id,username,display_name,status) "
        + "values (310,300,310,'member','团队成员','ACTIVE')");
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (311,300,'父团队','PARENT-TEAM')");
    jdbc.update("insert into team(id,organization_id,parent_id,name,code) "
        + "values (312,300,311,'子团队','CHILD-TEAM')");
    jdbc.update("insert into role(id,code,name,description,built_in) "
        + "values (100,'CUSTOM_ASSIGNED','已分配角色','用于保护测试',false)");
    jdbc.update("insert into user_role(user_id,role_id) values (310,100)");

    mvc.perform(delete("/api/v1/admin/users/300").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("不能删除当前登录用户"));
    mvc.perform(delete("/api/v1/admin/teams/310").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("团队仍有用户，不能删除"));
    mvc.perform(delete("/api/v1/admin/teams/311").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("团队仍有下级团队，不能删除"));
    mvc.perform(delete("/api/v1/admin/roles/1").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("内置角色不能删除"));
    mvc.perform(delete("/api/v1/admin/roles/100").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("角色已分配给用户，不能删除"));
  }

  @Test
  void userWithBusinessHistoryCannotBeDeletedAndIdentityRelationsRollBack() throws Exception {
    jdbc.update("insert into team(id,organization_id,name,code) "
        + "values (310,300,'临时团队','TEMP-TEAM')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (310,300,'historical','历史用户','ACTIVE')");
    jdbc.update("insert into user_role(user_id,role_id) values (310,4)");
    jdbc.update("insert into user_team(user_id,team_id) values (310,310)");
    jdbc.update("insert into sso_identity(user_id,provider,subject,email) "
        + "values (310,'oidc','historical-subject','historical@example.com')");
    jdbc.update("insert into audit_log(organization_id,actor_user_id,action,resource_type,"
        + "resource_id,trace_id,details_text) values (300,310,'TEST_ACTION','USER','310',"
        + "'trace-history','历史业务记录')");

    mvc.perform(delete("/api/v1/admin/users/310").with(admin()).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("用户已有业务记录，不能删除；请停用用户"));

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from app_user where id=310", Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from user_role where user_id=310", Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from user_team where user_id=310", Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from sso_identity where user_id=310", Integer.class));
  }

  private RequestPostProcessor admin() {
    CurrentUser principal = new CurrentUser(300L, 300L, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority("system:manage"))));
  }
}
