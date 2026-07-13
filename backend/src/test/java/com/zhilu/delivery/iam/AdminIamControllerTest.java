package com.zhilu.delivery.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
  }

  @Test
  void adminCanCreateTeamAndLocalUserWithRole() throws Exception {
    mvc.perform(post("/api/v1/admin/teams")
            .with(user("admin").authorities(() -> "system:manage"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"organizationId\":300,\"name\":\"交付一组\",\"code\":\"DELIVERY-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("交付一组"));

    Long teamId = jdbc.queryForObject("select id from team where code='DELIVERY-1'", Long.class);
    mvc.perform(post("/api/v1/admin/users")
            .with(user("admin").authorities(() -> "system:manage"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"organizationId\":300,\"primaryTeamId\":" + teamId
                + ",\"username\":\"wang\",\"password\":\"secret123\","
                + "\"displayName\":\"小王\",\"email\":\"wang@example.com\","
                + "\"roleCodes\":[\"DELIVERY_ENGINEER\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("wang"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void adminCanReadRolesAndReplaceRolePermissions() throws Exception {
    mvc.perform(get("/api/v1/admin/roles")
            .with(user("admin").authorities(() -> "system:manage")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6));

    mvc.perform(put("/api/v1/admin/roles/6/permissions")
            .with(user("admin").authorities(() -> "system:manage"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionCodes\":[\"dashboard:read\",\"standardization:read\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));
  }
}
