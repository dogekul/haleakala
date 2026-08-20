package com.zhilu.delivery.document;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.audit.AuditService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-admin;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class DocumentAdminControllerTest {
  @Autowired private MockMvc mvc;
  @MockBean private DocumentMigrationService migrations;
  @MockBean private DocumentJobService documentJobs;
  @MockBean private OutlineConfigurationService configurations;
  @MockBean private OutlineClient outline;
  @MockBean private AuditService audit;

  @Test
  void savingValidOutlineConfigurationRequeuesFailedProjectDocuments() throws Exception {
    OutlineConnection connection = new OutlineConnection(
        7100, "http://outline", "http://outline", "ol_api_test",
        "collection-ref", "", "ORGANIZATION");
    OutlineConfigurationDraft draft = new OutlineConfigurationDraft(
        connection, "collection-ref", true);
    OutlineCollection collection = new OutlineCollection(
        "collection-id", "交付文档", "delivery");
    when(configurations.draft(
        7100, "http://outline", "http://outline", "ol_api_test", "collection-ref"))
        .thenReturn(draft);
    when(outline.testConnection(connection, "collection-ref")).thenReturn(collection);
    when(configurations.saveValidated(7100, draft, collection))
        .thenReturn(Collections.singletonMap("collectionId", "collection-id"));

    mvc.perform(put("/api/v1/admin/document-center/config")
            .with(actor(true)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"baseUrl\":\"http://outline\","
                + "\"publicBaseUrl\":\"http://outline\","
                + "\"apiToken\":\"ol_api_test\","
                + "\"collectionId\":\"collection-ref\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionId").value("collection-id"));

    verify(documentJobs).requeueFailedProjectJobs(7100);
  }

  @Test
  void exposesStatusAndOperationsOnlyToSystemManagers() throws Exception {
    Map<String, Object> status = new LinkedHashMap<String, Object>();
    status.put("integrationStatus", "NOT_CONFIGURED");
    status.put("collectionId", "");
    status.put("jobs", Collections.singletonMap("failed", 0));
    when(migrations.status(7100)).thenReturn(status);
    when(migrations.initialize(7100)).thenReturn(Collections.singletonMap("status", "READY"));
    when(migrations.startKnowledgeMigration(7100))
        .thenReturn(Collections.singletonMap("enqueued", 2));
    when(migrations.startProjectMigration(7100))
        .thenReturn(Collections.singletonMap("enqueued", 3));
    when(migrations.retry(7100, 99))
        .thenReturn(Collections.singletonMap("status", "PENDING"));
    when(migrations.jobs(7100, "FAILED"))
        .thenReturn(Collections.singletonList(Collections.singletonMap("id", 99)));
    when(configurations.view(7100)).thenReturn(Collections.singletonMap(
        "apiTokenConfigured", false));

    mvc.perform(get("/api/v1/admin/document-center/status").with(actor(false)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/document-center/config").with(actor(false)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/document-center/status").with(actor(true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.integrationStatus").value("NOT_CONFIGURED"))
        .andExpect(jsonPath("$.apiToken").doesNotExist());
    mvc.perform(get("/api/v1/admin/document-center/config").with(actor(true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiTokenConfigured").value(false))
        .andExpect(jsonPath("$.apiToken").doesNotExist());
    mvc.perform(post("/api/v1/admin/document-center/initialize")
            .with(actor(true)).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/admin/document-center/migrate-knowledge")
            .with(actor(true)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enqueued").value(2));
    mvc.perform(post("/api/v1/admin/document-center/migrate-projects")
            .with(actor(true)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enqueued").value(3));
    mvc.perform(post("/api/v1/admin/document-center/jobs/99/retry")
            .with(actor(true)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
    mvc.perform(get("/api/v1/admin/document-center/jobs?status=FAILED").with(actor(true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(99));

    verify(migrations).initialize(7100);
    verify(migrations).retry(7100, 99);
  }

  private RequestPostProcessor actor(boolean manager) {
    String permission = manager ? "system:manage" : "project:read";
    CurrentUser principal = new CurrentUser(
        7100L, 7100L, "actor", "Actor",
        Collections.<String>emptyList(), Arrays.asList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(
        principal, null, Arrays.asList(new SimpleGrantedAuthority(permission))));
  }
}
