package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-migration;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06",
    "delivery.outline.max-attempts=1"
})
class DocumentMigrationServiceTest {
  private static final String COLLECTION_ID = "a4296a54-2044-4529-ba86-d598a5322e06";

  @Autowired private DocumentMigrationService migrations;
  @Autowired private DocumentJobService jobs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.zhilu.delivery.knowledge.KnowledgeService knowledge;
  @MockBean private OutlineClient outline;
  private final AtomicInteger sequence = new AtomicInteger();
  private final Map<String, OutlineDocument> remote =
      new ConcurrentHashMap<String, OutlineDocument>();

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
    jdbc.update("insert into organization(id,name,code) values (6100,'智鹿','MIGRATION')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (6100,6100,'admin','管理员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (6100,6100,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (6100,6100,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (6100,6100,'客户','ACTIVE')");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
            + "visibility,status,owner_user_id) values "
            + "(6100,6100,'CASE','成功知识','摘要','# 旧正文','ORGANIZATION','PUBLISHED',6100),"
            + "(6101,6100,'CASE','失败知识','摘要','失败正文','ORGANIZATION','PUBLISHED',6100)");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
            + "customer_id,product_id,product_version_id,manager_user_id,status,current_stage,"
            + "risk_level,gate_mode,created_by) values "
            + "(6100,6100,'PRJ-6100','项目一','客户',6100,6100,6100,6100,'ACTIVE','START',"
            + "'GREEN','BLOCK',6100),"
            + "(6101,6100,'PRJ-6101','项目二','客户',6100,6100,6100,6100,'ACTIVE','START',"
            + "'GREEN','BLOCK',6100)");
    remote.clear();
    when(outline.collectionInfo(any(OutlineConnection.class), eq(COLLECTION_ID)))
        .thenReturn(new OutlineCollection(COLLECTION_ID, "文档中心", "delivery"));
    when(outline.info(any(OutlineConnection.class), anyString())).thenAnswer(invocation -> {
      OutlineDocument document = remote.get(invocation.getArgument(1));
      if (document == null) {
        throw new OutlineException(OutlineException.Type.NOT_FOUND, "document not found");
      }
      return document;
    });
    doAnswer(invocation -> {
      String title = invocation.getArgument(2);
      if ("失败知识".equals(title)) {
        throw new OutlineException(OutlineException.Type.UNAVAILABLE, "模拟单条失败");
      }
      OutlineDocument document = document(
          invocation.getArgument(1), title, invocation.getArgument(3), 1);
      remote.put(document.getId(), document);
      return document;
    }).when(outline).create(
        any(OutlineConnection.class), anyString(), anyString(), anyString(), anyString(),
        nullable(String.class), anyBoolean());
  }

  @Test
  void migratesLegacyKnowledgeIdempotentlyAndKeepsProcessingAfterOneFailure() {
    assertEquals(2, migrations.startKnowledgeMigration(6100).get("enqueued"));
    assertEquals(0, migrations.startKnowledgeMigration(6100).get("enqueued"));

    jobs.runDueJobs();

    ArgumentCaptor<OutlineConnection> connection =
        ArgumentCaptor.forClass(OutlineConnection.class);
    verify(outline, atLeastOnce()).info(connection.capture(), anyString());
    assertEquals(6100L, connection.getValue().getOrganizationId());
    assertEquals("ol_api_test", connection.getValue().getApiToken());

    assertEquals(1, jdbc.queryForObject(
        "select count(*) from document_job where job_type='KNOWLEDGE_MIGRATION' "
            + "and status='DONE'",
        Integer.class));
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from document_job where job_type='KNOWLEDGE_MIGRATION' "
            + "and status='FAILED'",
        Integer.class));
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from knowledge_item where id=6100 and outline_link_id is not null",
        Integer.class));

    Map<String, Object> detail = knowledge.get(6100, admin());
    assertNull(detail.get("content"));
    assertEquals("READY", detail.get("documentStatus"));
    assertEquals(0, migrations.startKnowledgeMigration(6100).get("enqueued"));

    @SuppressWarnings("unchecked")
    Map<String, Object> counts = (Map<String, Object>) migrations.status(6100).get("jobs");
    assertEquals(1L, ((Number) counts.get("success")).longValue());
    assertEquals(1L, ((Number) counts.get("failed")).longValue());
  }

  @Test
  void enqueuesExistingProjectsOnceForSharedInitializer() {
    assertEquals(2, migrations.startProjectMigration(6100).get("enqueued"));
    assertEquals(0, migrations.startProjectMigration(6100).get("enqueued"));
    assertEquals(2, jdbc.queryForObject(
        "select count(*) from document_job where job_type='PROJECT_MIGRATION'",
        Integer.class));
  }

  @Test
  void retriesKnowledgeThatAlreadyHasAFailedOutlineLink() {
    String documentId = DocumentCenterService.deterministicDocumentId(6100, "KNOWLEDGE:6100");
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,revision,sync_status,last_error) "
            + "values (6100,'KNOWLEDGE:6100','KNOWLEDGE_DOCUMENT',?,?,?,1,'FAILED','temporary')",
        COLLECTION_ID, documentId, "成功知识");
    Long linkId = jdbc.queryForObject(
        "select id from outline_document_link where business_key='KNOWLEDGE:6100'",
        Long.class);
    jdbc.update("update knowledge_item set outline_link_id=? where id=6100", linkId);
    remote.put(documentId, document(documentId, "成功知识", "# 已恢复", 2));

    assertEquals(2, migrations.startKnowledgeMigration(6100).get("enqueued"));
    jobs.runDueJobs();

    assertEquals("READY", jdbc.queryForObject(
        "select sync_status from outline_document_link where id=?", String.class, linkId));
    assertNull(jdbc.queryForObject(
        "select last_error from outline_document_link where id=?", String.class, linkId));
  }

  private CurrentUser admin() {
    return new CurrentUser(
        6100L, 6100L, "admin", "管理员",
        Arrays.asList("ADMIN"), Collections.singletonList("system:manage"));
  }

  private OutlineDocument document(
      String id, String title, String text, long revision) {
    return new OutlineDocument(
        id, COLLECTION_ID, null, title, text, "/doc/" + id, "url-" + id, revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }
}
