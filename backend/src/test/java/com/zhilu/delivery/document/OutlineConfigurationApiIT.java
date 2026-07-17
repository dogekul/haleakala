package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:outline-configuration-api;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.settings.encryption-key=test-settings-master-key-2026"
})
@AutoConfigureMockMvc
class OutlineConfigurationApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;

  @BeforeEach
  void seedOrganizations() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : Arrays.asList(
        "audit_log", "outline_document_link", "system_setting", "app_user", "organization")) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(9100,'组织一','OUTLINE-API-ONE'),(9200,'组织二','OUTLINE-API-TWO')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(9100,9100,'outline-admin-one','组织一管理员','ACTIVE'),"
        + "(9200,9200,'outline-admin-two','组织二管理员','ACTIVE')");
  }

  @Test
  void testsAndSavesACollectionLinkWithoutReturningTheToken() throws Exception {
    when(outline.testConnection(
        any(OutlineConnection.class), eq("delivery-D4rIACBrmU")))
        .thenReturn(new OutlineCollection(
            "11111111-1111-4111-8111-111111111111",
            "智鹿交付", "D4rIACBrmU"));

    String request = "{"
        + "\"baseUrl\":\"http://outline.internal:3000\","
        + "\"publicBaseUrl\":\"http://localhost:3000\","
        + "\"collectionId\":\"http://localhost:3000/collection/delivery-D4rIACBrmU/\","
        + "\"apiToken\":\"ol_api_admin_secret\"}";

    mvc.perform(post("/api/v1/admin/document-center/config/test")
            .with(admin(9100)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.collectionId")
            .value("11111111-1111-4111-8111-111111111111"))
        .andExpect(jsonPath("$.collectionName").value("智鹿交付"))
        .andExpect(jsonPath("$.apiToken").doesNotExist());
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from system_setting where organization_id=9100", Integer.class));
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=9100 "
            + "and action='TEST' and resource_type='OUTLINE_CONFIGURATION'",
        Integer.class));

    mvc.perform(put("/api/v1/admin/document-center/config")
            .with(admin(9100)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiTokenConfigured").value(true))
        .andExpect(jsonPath("$.apiToken").doesNotExist());
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=9100 "
            + "and action='UPDATE' and resource_type='OUTLINE_CONFIGURATION'",
        Integer.class));

    mvc.perform(get("/api/v1/admin/document-center/config").with(admin(9100)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionName").value("智鹿交付"))
        .andExpect(jsonPath("$.apiToken").doesNotExist());

    assertEquals(0, jdbc.queryForObject(
        "select count(*) from audit_log where details_text like '%ol_api_admin_secret%'",
        Integer.class));
    mvc.perform(get("/api/v1/admin/document-center/config").with(admin(9200)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseUrl").value("http://localhost:3000"))
        .andExpect(jsonPath("$.collectionName").isEmpty());
  }

  @Test
  void rejectsUnauthorizedUnknownAndFailedConfigurationRequests() throws Exception {
    mvc.perform(get("/api/v1/admin/document-center/config").with(nonManager(9100)))
        .andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/admin/document-center/config")
            .with(admin(9100)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"baseUrl\":\"http://outline.internal:3000\","
                + "\"publicBaseUrl\":\"http://localhost:3000\","
                + "\"collectionId\":\"D4rIACBrmU\","
                + "\"apiToken\":\"ol_api_admin_secret\","
                + "\"organizationId\":9200}"))
        .andExpect(status().isBadRequest());

    when(outline.testConnection(
        any(OutlineConnection.class), eq("D4rIACBrmU")))
        .thenThrow(new OutlineException(
            OutlineException.Type.AUTHENTICATION, "Outline authentication failed"));
    mvc.perform(put("/api/v1/admin/document-center/config")
            .with(admin(9100)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"baseUrl\":\"http://outline.internal:3000\","
                + "\"publicBaseUrl\":\"http://localhost:3000\","
                + "\"collectionId\":\"D4rIACBrmU\","
                + "\"apiToken\":\"wrong\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("OUTLINE_AUTHENTICATION"));
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from system_setting where organization_id=9100",
        Integer.class));
  }

  @Test
  void blankTokenPreservesSecretAndExistingDocumentsBlockCollectionSwitch() throws Exception {
    when(outline.testConnection(any(OutlineConnection.class), anyString()))
        .thenReturn(new OutlineCollection(
            "11111111-1111-4111-8111-111111111111", "智鹿交付", "D4rIACBrmU"));
    putConfiguration(9100, "ol_api_admin_secret", "D4rIACBrmU")
        .andExpect(status().isOk());
    String firstCiphertext = jdbc.queryForObject(
        "select setting_value from system_setting where organization_id=9100 "
            + "and setting_key='outline.apiToken'", String.class);

    putConfiguration(9100, "", "11111111-1111-4111-8111-111111111111")
        .andExpect(status().isOk());
    assertEquals(firstCiphertext, jdbc.queryForObject(
        "select setting_value from system_setting where organization_id=9100 "
            + "and setting_key='outline.apiToken'", String.class));

    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,sync_status) "
            + "values (9100,'KNOWLEDGE_ROOT','INDEX',"
            + "'11111111-1111-4111-8111-111111111111','doc-1','知识库','READY')");
    when(outline.testConnection(any(OutlineConnection.class), eq("NewUrlId")))
        .thenReturn(new OutlineCollection(
            "22222222-2222-4222-8222-222222222222", "新集合", "NewUrlId"));
    putConfiguration(9100, "", "NewUrlId")
        .andExpect(status().isConflict());
  }

  private ResultActions putConfiguration(
      long organizationId, String token, String collection) throws Exception {
    return mvc.perform(put("/api/v1/admin/document-center/config")
        .with(admin(organizationId)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"baseUrl\":\"http://outline.internal:3000\","
            + "\"publicBaseUrl\":\"http://localhost:3000\","
            + "\"collectionId\":\"" + collection + "\","
            + "\"apiToken\":\"" + token + "\"}"));
  }

  private RequestPostProcessor admin(long organizationId) {
    return actor(organizationId, "system:manage");
  }

  private RequestPostProcessor nonManager(long organizationId) {
    return actor(organizationId, "project:read");
  }

  private RequestPostProcessor actor(long organizationId, String permission) {
    CurrentUser principal = new CurrentUser(
        organizationId, organizationId, "actor-" + organizationId,
        "Actor " + organizationId, Collections.<String>emptyList(),
        Arrays.asList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(
        principal, null,
        Arrays.asList(new SimpleGrantedAuthority(permission))));
  }
}
