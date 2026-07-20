package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
  void generatesOutlineCompatibleDeterministicUuidV4() {
    String first = DocumentCenterService.deterministicDocumentId(3100, "KNOWLEDGE_ROOT");

    assertEquals(4, UUID.fromString(first).version());
    assertEquals(first,
        DocumentCenterService.deterministicDocumentId(3100, "KNOWLEDGE_ROOT"));
  }

  @Test
  void createsEachBusinessIndexExactlyOnce() {
    OutlineDocument created = document("知识库", "# 知识库", 1);
    String desiredId = DocumentCenterService.deterministicDocumentId(3100, "KNOWLEDGE_ROOT");
    when(outline.create(any(OutlineConnection.class), eq(desiredId), eq("知识库"),
        eq(""), eq(COLLECTION_ID), nullable(String.class), eq(true)))
        .thenReturn(created);
    when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID))).thenReturn(created);

    long first = documents.ensureIndex(3100, "KNOWLEDGE_ROOT", "知识库", null);
    long second = documents.ensureIndex(3100, "KNOWLEDGE_ROOT", "知识库", null);

    assertEquals(first, second);
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from outline_document_link where organization_id=3100 "
            + "and business_key='KNOWLEDGE_ROOT'", Integer.class));
    verify(outline).create(any(OutlineConnection.class), eq(desiredId), eq("知识库"),
        eq(""), eq(COLLECTION_ID), nullable(String.class), eq(true));
  }

  @Test
  void rejectsStaleRevisionBeforeUpdatingOutline() {
    long linkId = link(3100, "KNOWLEDGE:1", "知识", DOCUMENT_ID, 3);
    when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID)))
        .thenReturn(document("知识", "最新正文", 3));

    assertThrows(ConflictException.class,
        () -> documents.updateLink(linkId, 3100, "知识", "旧编辑", 2));

    verify(outline, never()).update(
        any(OutlineConnection.class), anyString(), anyString(), anyString());
  }

  @Test
  void acceptsStaleRevisionWhenOutlineAlreadyContainsTheRequestedContent() {
    long linkId = link(3100, "OPPORTUNITY:1:RESEARCH_REPORT", "需求调研报告", DOCUMENT_ID, 2);
    when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID)))
        .thenReturn(document("需求调研报告", "# 已提交正文", 3));

    DocumentView current = documents.updateLink(
        linkId, 3100, "需求调研报告", "# 已提交正文", 2);

    assertEquals(3L, current.getRevision());
    assertEquals("# 已提交正文", current.getMarkdown());
    verify(outline, never()).update(
        any(OutlineConnection.class), anyString(), anyString(), anyString());
  }

  @Test
  void updatesOutlineAndRefreshesTheLocalRevisionCache() {
    long linkId = link(3100, "KNOWLEDGE:2", "原标题", DOCUMENT_ID, 2);
    when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID)))
        .thenReturn(document("原标题", "原正文", 2));
    when(outline.update(
        any(OutlineConnection.class), eq(DOCUMENT_ID), eq("新标题"), eq("# 新正文")))
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
  void serializesConcurrentSavesUsingTheSameExpectedRevision() throws Exception {
    long linkId = link(3100, "KNOWLEDGE:3", "原标题", DOCUMENT_ID, 1);
    AtomicReference<OutlineDocument> remote =
        new AtomicReference<OutlineDocument>(document("原标题", "原正文", 1));
    AtomicInteger infoCalls = new AtomicInteger();
    CountDownLatch firstInfo = new CountDownLatch(1);
    CountDownLatch secondInfo = new CountDownLatch(1);
    when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID))).thenAnswer(invocation -> {
      int call = infoCalls.incrementAndGet();
      if (call == 1) {
        firstInfo.countDown();
        secondInfo.await(500, TimeUnit.MILLISECONDS);
      } else {
        secondInfo.countDown();
      }
      return remote.get();
    });
    when(outline.update(
        any(OutlineConnection.class), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> {
      OutlineDocument current = remote.get();
      OutlineDocument updated = document(
          invocation.getArgument(2), invocation.getArgument(3), current.getRevision() + 1);
      remote.set(updated);
      return updated;
    });
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Object>> results = new ArrayList<Future<Object>>();
      results.add(pool.submit(() -> documents.updateLink(
          linkId, 3100, "编辑一", "正文一", 1)));
      firstInfo.await(2, TimeUnit.SECONDS);
      results.add(pool.submit(() -> {
        try {
          return documents.updateLink(linkId, 3100, "编辑二", "正文二", 1);
        } catch (ConflictException conflict) {
          return conflict;
        }
      }));
      int successes = 0;
      int conflicts = 0;
      for (Future<Object> result : results) {
        Object value = result.get(5, TimeUnit.SECONDS);
        if (value instanceof DocumentView) successes++;
        if (value instanceof ConflictException) conflicts++;
      }
      assertEquals(1, successes);
      assertEquals(1, conflicts);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void storesFailureForRetryAndKeepsTenantIsolation() {
    when(outline.create(
        any(OutlineConnection.class), anyString(), anyString(), anyString(), anyString(),
        nullable(String.class), anyBoolean()))
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

  @Test
  void recoversACompletedRemoteCreateAfterTheResponseWasLost() {
    String businessKey = "PROJECT_ROOT";
    String desiredId = DocumentCenterService.deterministicDocumentId(3100, businessKey);
    OutlineDocument recovered = document(
        desiredId, "项目文档", "", 1);
    when(outline.info(any(OutlineConnection.class), eq(desiredId)))
        .thenThrow(new OutlineException(
            OutlineException.Type.NOT_FOUND, "document not found"))
        .thenReturn(recovered);
    when(outline.create(any(OutlineConnection.class), eq(desiredId), eq("项目文档"),
        eq(""), eq(COLLECTION_ID), nullable(String.class), eq(true)))
        .thenThrow(new OutlineException(
            OutlineException.Type.TIMEOUT, "Outline request timed out"));

    assertThrows(OutlineException.class,
        () -> documents.ensureIndex(3100, businessKey, "项目文档", null));
    long linkId = documents.ensureIndex(3100, businessKey, "项目文档", null);

    assertEquals("READY", jdbc.queryForObject(
        "select sync_status from outline_document_link where id=?",
        String.class, linkId));
    assertEquals(desiredId, jdbc.queryForObject(
        "select outline_document_id from outline_document_link where id=?",
        String.class, linkId));
    verify(outline, times(1))
        .create(any(OutlineConnection.class), eq(desiredId), eq("项目文档"),
            eq(""), eq(COLLECTION_ID), nullable(String.class), eq(true));
  }

  @Test
  void reclaimsAStaleCreatingLink() {
    String businessKey = "KNOWLEDGE_ROOT";
    String desiredId = DocumentCenterService.deterministicDocumentId(3100, businessKey);
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,title_cache,sync_status,updated_at) "
            + "values (3100,?,'INDEX',?,'知识库','CREATING',?)",
        businessKey, COLLECTION_ID,
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)));
    when(outline.info(any(OutlineConnection.class), eq(desiredId))).thenThrow(new OutlineException(
        OutlineException.Type.NOT_FOUND, "document not found"));
    when(outline.create(any(OutlineConnection.class), eq(desiredId), eq("知识库"),
        eq(""), eq(COLLECTION_ID), nullable(String.class), eq(true)))
        .thenReturn(document(desiredId, "知识库", "", 1));

    long linkId = documents.ensureIndex(3100, businessKey, "知识库", null);

    assertEquals("READY", jdbc.queryForObject(
        "select sync_status from outline_document_link where id=?",
        String.class, linkId));
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
    return document(DOCUMENT_ID, title, text, revision);
  }

  private OutlineDocument document(
      String documentId, String title, String text, long revision) {
    return new OutlineDocument(
        documentId, COLLECTION_ID, null, title, text,
        "/doc/test-" + documentId, "test-url-id", revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }
}
