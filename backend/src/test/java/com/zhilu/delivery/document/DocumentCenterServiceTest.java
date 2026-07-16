package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:document-center;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
class DocumentCenterServiceTest {
  private static final String COLLECTION_ID = "a4296a54-2044-4529-ba86-d598a5322e06";
  private static final String DOCUMENT_ID = "015f5a38-a8f4-4ab1-b3c2-98cf41ad5d2a";
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DocumentCenterService documents;
  @MockBean private OutlineClient outline;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "document_job", "project_document", "document_template_config", "knowledge_item",
        "outline_document_link", "delivery_project", "customer", "product_version", "product",
        "app_user", "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(3100,'智鹿','DOC-ORG'),(3200,'其他组织','OTHER-DOC-ORG')");
  }

  @Test
  void createsEachBusinessIndexExactlyOnce() {
    OutlineDocument created = document("知识库", "# 知识库", 1);
    when(outline.create("知识库", "", COLLECTION_ID, null, true)).thenReturn(created);
    when(outline.info(DOCUMENT_ID)).thenReturn(created);

    long first = documents.ensureIndex(3100, "KNOWLEDGE_ROOT", "知识库", null);
    long second = documents.ensureIndex(3100, "KNOWLEDGE_ROOT", "知识库", null);

    assertEquals(first, second);
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from outline_document_link where organization_id=3100 "
            + "and business_key='KNOWLEDGE_ROOT'", Integer.class));
    verify(outline).create("知识库", "", COLLECTION_ID, null, true);
  }

  @Test
  void rejectsStaleRevisionBeforeUpdatingOutline() {
    long linkId = link(3100, "KNOWLEDGE:1", "知识", DOCUMENT_ID, 3);
    when(outline.info(DOCUMENT_ID)).thenReturn(document("知识", "最新正文", 3));

    assertThrows(ConflictException.class,
        () -> documents.updateLink(linkId, 3100, "知识", "旧编辑", 2));

    verify(outline, never()).update(anyString(), anyString(), anyString());
  }

  @Test
  void updatesOutlineAndRefreshesTheLocalRevisionCache() {
    long linkId = link(3100, "KNOWLEDGE:2", "原标题", DOCUMENT_ID, 2);
    when(outline.info(DOCUMENT_ID)).thenReturn(document("原标题", "原正文", 2));
    when(outline.update(DOCUMENT_ID, "新标题", "# 新正文"))
        .thenReturn(document("新标题", "# 新正文", 3));

    DocumentView updated = documents.updateLink(
        linkId, 3100, "新标题", "# 新正文", 2);

    assertEquals(3L, updated.getRevision());
    assertEquals("新标题", jdbc.queryForObject(
        "select title_cache from outline_document_link where id=?", String.class, linkId));
    assertEquals(Long.valueOf(3), jdbc.queryForObject(
        "select revision from outline_document_link where id=?", Long.class, linkId));
  }

  @Test
  void storesFailureForRetryAndKeepsTenantIsolation() {
    when(outline.create(anyString(), anyString(), anyString(), isNull(), anyBoolean()))
        .thenThrow(new OutlineException(
            OutlineException.Type.UNAVAILABLE, "Outline is unavailable"));

    assertThrows(OutlineException.class,
        () -> documents.ensureIndex(3100, "PROJECT_ROOT", "项目文档", null));
    assertEquals("FAILED", jdbc.queryForObject(
        "select sync_status from outline_document_link where organization_id=3100 "
            + "and business_key='PROJECT_ROOT'", String.class));
    long linkId = jdbc.queryForObject(
        "select id from outline_document_link where organization_id=3100 "
            + "and business_key='PROJECT_ROOT'", Long.class);
    assertThrows(NotFoundException.class, () -> documents.readLink(linkId, 3200));
  }

  private long link(
      long organizationId, String businessKey, String title, String documentId, long revision) {
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,revision,sync_status) "
            + "values (?,?,?,?,?,?,?,'READY')",
        organizationId, businessKey, "KNOWLEDGE_DOCUMENT", COLLECTION_ID, documentId, title,
        revision);
    return jdbc.queryForObject(
        "select id from outline_document_link where organization_id=? and business_key=?",
        Long.class, organizationId, businessKey);
  }

  private OutlineDocument document(String title, String text, long revision) {
    return new OutlineDocument(
        DOCUMENT_ID, COLLECTION_ID, null, title, text,
        "/doc/test-" + DOCUMENT_ID, "test-url-id", revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }
}
