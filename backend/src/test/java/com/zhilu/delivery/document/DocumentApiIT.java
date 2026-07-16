package com.zhilu.delivery.document;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-api;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
@AutoConfigureMockMvc
class DocumentApiIT {
  private static final String COLLECTION_ID = "a4296a54-2044-4529-ba86-d598a5322e06";
  private static final String DOCUMENT_ID = "015f5a38-a8f4-4ab1-b3c2-98cf41ad5d2a";
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;
  private long knowledgeId;
  private long projectId;
  private long projectDocumentId;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "document_job", "project_document", "document_template_config", "training_material",
        "code_snippet", "knowledge_item", "outline_document_link", "project_activity",
        "project_artifact", "template_instance", "milestone", "project_risk", "stage_instance",
        "project_member", "delivery_project", "customer", "product_version", "product",
        "app_user", "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(4100,'智鹿','DOC-API'),(4200,'其他组织','OTHER-DOC-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(4100,4100,'owner','负责人','ACTIVE'),"
        + "(4101,4100,'member','项目成员','ACTIVE'),"
        + "(4200,4200,'other','其他用户','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (4100,4100,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (4100,4100,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (4100,4100,'客户','ACTIVE')");
    long knowledgeLink = link("KNOWLEDGE:4100", "知识正文");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
            + "visibility,status,owner_user_id,outline_link_id) "
            + "values (4100,4100,'CASE','知识正文','摘要','旧正文','ORGANIZATION',"
            + "'PUBLISHED',4100,?)", knowledgeLink);
    knowledgeId = 4100;
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
            + "customer_id,product_id,product_version_id,manager_user_id,status,current_stage,"
            + "risk_level,gate_mode,created_by) values "
            + "(4100,4100,'PRJ-4100','项目','客户',4100,4100,4100,4100,'ACTIVE','START',"
            + "'GREEN','BLOCK',4100)");
    jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
        + "values (4100,4100,'DELIVERY_MANAGER',100),(4100,4101,'DELIVERY_ENGINEER',100)");
    projectId = 4100;
    jdbc.update("insert into project_document(id,project_id,stage_code,source_template_id,"
            + "source_template_revision,outline_link_id,requirement,status) "
            + "values (4100,4100,'START',4100,1,?,'REQUIRED','PENDING_CONFIRMATION')",
        knowledgeLink);
    projectDocumentId = 4100;
  }

  @Test
  void readsKnowledgeAndProjectDocumentsThroughBusinessIds() throws Exception {
    when(outline.info(DOCUMENT_ID)).thenReturn(document("知识正文", "# 最新正文", 3));

    mvc.perform(get("/api/v1/knowledge/{id}/document", knowledgeId)
            .with(actor(4100, 4100, "knowledge:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("知识正文"))
        .andExpect(jsonPath("$.markdown").value("# 最新正文"))
        .andExpect(jsonPath("$.renderedHtml").value(
            org.hamcrest.Matchers.containsString("<h1>最新正文</h1>")))
        .andExpect(jsonPath("$.revision").value(3))
        .andExpect(jsonPath("$.outlineUrl").value(
            "http://outline.test/doc/test-" + DOCUMENT_ID));

    mvc.perform(get("/api/v1/projects/{projectId}/documents/{documentId}",
            projectId, projectDocumentId)
            .with(actor(4101, 4100, "project:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(3));
  }

  @Test
  void staleKnowledgeSaveReturnsConflictWithoutUpdatingOutline() throws Exception {
    when(outline.info(DOCUMENT_ID)).thenReturn(document("知识正文", "# 最新正文", 3));

    mvc.perform(put("/api/v1/knowledge/{id}/document", knowledgeId)
            .with(actor(4100, 4100, "knowledge:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"知识正文\",\"markdown\":\"# 我的编辑\",\"revision\":2}"))
        .andExpect(status().isConflict());

    verify(outline, never()).update(anyString(), anyString(), anyString());
  }

  @Test
  void crossOrganizationDocumentAccessIsHidden() throws Exception {
    mvc.perform(get("/api/v1/knowledge/{id}/document", knowledgeId)
            .with(actor(4200, 4200, "knowledge:read")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/projects/{projectId}/documents/{documentId}",
            projectId, projectDocumentId)
            .with(actor(4200, 4200, "project:read")))
        .andExpect(status().isNotFound());
  }

  private long link(String businessKey, String title) {
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,revision,sync_status) "
            + "values (4100,?,'KNOWLEDGE_DOCUMENT',?,?,?,2,'READY')",
        businessKey, COLLECTION_ID, DOCUMENT_ID, title);
    return jdbc.queryForObject(
        "select id from outline_document_link where organization_id=4100 and business_key=?",
        Long.class, businessKey);
  }

  private OutlineDocument document(String title, String text, long revision) {
    return new OutlineDocument(
        DOCUMENT_ID, COLLECTION_ID, null, title, text,
        "/doc/test-" + DOCUMENT_ID, "test-url-id", revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }

  private RequestPostProcessor actor(long id, long organizationId, String permission) {
    CurrentUser principal = new CurrentUser(
        id, organizationId, "actor-" + id, "Actor " + id,
        Collections.<String>emptyList(), Arrays.asList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(
        principal, null, Arrays.asList(new SimpleGrantedAuthority(permission))));
  }
}
