package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-export-api;MODE=MySQL;"
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
class DocumentExportApiIT {
  private static final String COLLECTION_ID = "a4296a54-2044-4529-ba86-d598a5322e06";
  private static final String DOCUMENT_ID = "015f5a38-a8f4-4ab1-b3c2-98cf41ad5d2a";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "audit_log", "document_job", "project_document", "document_template_config", "training_material",
        "code_snippet", "knowledge_item", "outline_document_link", "project_activity",
        "project_artifact", "template_instance", "milestone", "project_risk", "stage_instance",
        "project_member", "delivery_project", "customer", "product_version", "product",
        "app_user", "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(5100,'智鹿','EXPORT'),(5200,'其他组织','OTHER-EXPORT')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(5100,5100,'owner','负责人','ACTIVE'),"
        + "(5101,5100,'member','项目成员','ACTIVE'),"
        + "(5200,5200,'other','其他用户','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (5100,5100,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (5100,5100,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (5100,5100,'客户','ACTIVE')");
    long knowledgeLink = link("KNOWLEDGE:5100", "中文 验收报告");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
            + "visibility,status,owner_user_id,outline_link_id) values "
            + "(5100,5100,'CASE','中文 验收报告','摘要','不能回退的旧正文','ORGANIZATION',"
            + "'PUBLISHED',5100,?)",
        knowledgeLink);
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
            + "customer_id,product_id,product_version_id,manager_user_id,status,current_stage,"
            + "risk_level,gate_mode,created_by) values "
            + "(5100,5100,'PRJ-5100','项目','客户',5100,5100,5100,5100,'ACTIVE','START',"
            + "'GREEN','BLOCK',5100)");
    jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
        + "values (5100,5100,'DELIVERY_MANAGER',100),(5100,5101,'DELIVERY_ENGINEER',100)");
    jdbc.update("insert into project_document(id,project_id,stage_code,source_template_id,"
            + "source_template_revision,outline_link_id,requirement,status) "
            + "values (5100,5100,'START',5100,1,?,'REQUIRED','PENDING_CONFIRMATION')",
        knowledgeLink);
  }

  @Test
  void exportsKnowledgeAndProjectDocumentsWithSafeHeaders() throws Exception {
    when(outline.info(DOCUMENT_ID)).thenReturn(document());

    mvc.perform(get("/api/v1/knowledge/5100/document/export?format=md")
            .with(actor(5100, 5100, "knowledge:read")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
        .andExpect(header().string("Content-Disposition",
            org.hamcrest.Matchers.containsString("filename*=UTF-8''")));

    mvc.perform(get("/api/v1/projects/5100/documents/5100/export?format=pdf")
            .with(actor(5101, 5100, "project:read")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));

    assertEquals(2, jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=5100 and action='EXPORT'",
        Integer.class));
  }

  @Test
  void hidesCrossOrganizationExports() throws Exception {
    mvc.perform(get("/api/v1/knowledge/5100/document/export?format=html")
            .with(actor(5200, 5200, "knowledge:read")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/projects/5100/documents/5100/export?format=docx")
            .with(actor(5200, 5200, "project:read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void outlineFailureDoesNotFallBackToLegacyBody() throws Exception {
    when(outline.info(DOCUMENT_ID)).thenThrow(
        new OutlineException(OutlineException.Type.UNAVAILABLE, "Outline 暂不可用"));

    mvc.perform(get("/api/v1/knowledge/5100/document/export?format=md")
            .with(actor(5100, 5100, "knowledge:read")))
        .andExpect(status().isServiceUnavailable());
  }

  private long link(String businessKey, String title) {
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,revision,sync_status) "
            + "values (5100,?,'KNOWLEDGE_DOCUMENT',?,?,?,2,'READY')",
        businessKey, COLLECTION_ID, DOCUMENT_ID, title);
    return jdbc.queryForObject(
        "select id from outline_document_link where organization_id=5100 and business_key=?",
        Long.class, businessKey);
  }

  private OutlineDocument document() {
    return new OutlineDocument(
        DOCUMENT_ID, COLLECTION_ID, null, "中文 验收报告",
        "# 验收报告\n\n| 检查项 | 结果 |\n| --- | --- |\n| 登录 | 通过 |",
        "/doc/test-" + DOCUMENT_ID, "test-url-id", 3,
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
