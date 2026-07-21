package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.project.CreateProjectCommand;
import com.zhilu.delivery.project.DeliveryStage;
import com.zhilu.delivery.project.ProjectService;
import com.zhilu.delivery.project.ProjectView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:project-document-init;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06",
    "delivery.outline.max-attempts=2",
    "delivery.outline.initial-backoff=0s",
    "delivery.outline.stale-after=300ms",
    "delivery.outline.job-scan-ms=3600000"
})
@AutoConfigureMockMvc
class ProjectDocumentInitializationTest {
  private static final String COLLECTION = "a4296a54-2044-4529-ba86-d598a5322e06";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  @Autowired private DocumentJobService jobs;
  @Autowired private ProjectDocumentService projectDocuments;
  @Autowired private MockMvc mvc;
  @MockBean private OutlineClient outline;

  private final Map<String, OutlineDocument> outlineDocuments =
      new ConcurrentHashMap<String, OutlineDocument>();
  private final AtomicLong outlineIds = new AtomicLong();
  private final AtomicBoolean outlineAvailable = new AtomicBoolean(true);
  private final AtomicBoolean blockNextCreate = new AtomicBoolean(false);
  private CountDownLatch blockedCreateStarted;
  private CountDownLatch releaseBlockedCreate;
  private CurrentUser manager;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "audit_log", "document_job", "project_document", "document_template_config",
        "outline_document_link", "knowledge_item", "project_activity", "project_artifact",
        "template_instance", "milestone", "project_risk", "stage_instance", "project_member",
        "delivery_project", "customer", "product_version", "product", "app_user", "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (701,'智鹿','ZHILU-DOC-INIT')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (701,701,'manager','交付经理','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (701,701,'CORE','核心系统','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (701,701,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (701,701,'华东银行','ACTIVE')");
    manager = new CurrentUser(701L, 701L, "manager", "交付经理",
        Collections.singletonList("DELIVERY_MANAGER"),
        Collections.singletonList("project:write"));
    outlineAvailable.set(true);
    blockNextCreate.set(false);
    blockedCreateStarted = new CountDownLatch(1);
    releaseBlockedCreate = new CountDownLatch(1);
    stubOutline();
  }

  @Test
  void initializesSevenStageDirectoriesAndCopiesPublishedEnabledTemplatesIdempotently() {
    seedTemplate(7101, "项目启动检查单", "START", "REQUIRED", true, "PUBLISHED", 7);
    seedTemplate(7102, "停用模板", "REQUIREMENT", "OPTIONAL", false, "PUBLISHED", 3);
    seedTemplate(7103, "草稿模板", "GO_LIVE", "REQUIRED", true, "DRAFT", 4);
    seedTemplate(7105, "需求调研报告", "OPPORTUNITY_RESEARCH", "REQUIRED", true,
        "PUBLISHED", 2);
    ProjectView project = projects.create(command());

    assertEquals("PENDING", project.getDocumentSpaceStatus());
    assertEquals(0, outlineIds.get());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_document where project_id=?",
        Integer.class, project.getId()));
    assertThrows(ConflictException.class,
        () -> projects.advanceStage(project.getId(), DeliveryStage.REQUIREMENT, manager));

    jdbc.update("update document_template_config set stage_code='REQUIREMENT',"
        + "requirement='OPTIONAL',enabled=false where knowledge_item_id=7802");
    seedTemplate(7104, "创建后发布的模板", "START", "REQUIRED", true, "PUBLISHED", 5);
    outlineDocuments.put("template-7101", document(
        "template-7101", null, "项目启动检查单（新版）", "# 新版正文", 8));

    jobs.runDueJobs();

    ArgumentCaptor<OutlineConnection> connection =
        ArgumentCaptor.forClass(OutlineConnection.class);
    verify(outline, atLeastOnce()).info(connection.capture(), anyString());
    assertEquals(701L, connection.getValue().getOrganizationId());
    assertEquals("ol_api_test", connection.getValue().getApiToken());

    ProjectView initialized = projects.get(project.getId());
    assertEquals("READY", initialized.getDocumentSpaceStatus());
    assertEquals(null, initialized.getDocumentSpaceError());
    assertEquals(10, outlineIds.get());
    assertEquals(Integer.valueOf(7), jdbc.queryForObject(
        "select count(*) from outline_document_link where organization_id=701 "
            + "and business_key like ?",
        Integer.class, "PROJECT:" + project.getId() + ":STAGE:%"));
    assertEquals(Arrays.asList(
        "01 项目启动", "02 需求采集", "03 二开实施", "04 上线切换",
        "05 试运行与移交", "06 标准化评估", "07 项目收尾"),
        stageTitles(project.getId()));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_document where project_id=?",
        Integer.class, project.getId()));
    Map<String, Object> copied = jdbc.queryForMap(
        "select stage_code,source_template_revision,requirement,outline_link_id "
            + "from project_document where project_id=?",
        project.getId());
    assertEquals("START", copied.get("stage_code"));
    assertEquals(7L, ((Number) copied.get("source_template_revision")).longValue());
    assertEquals("REQUIRED", copied.get("requirement"));
    assertNotNull(copied.get("outline_link_id"));
    OutlineDocument copy = outlineDocuments.values().stream()
        .filter(document -> "项目启动检查单".equals(document.getTitle())
            && !"template-7101".equals(document.getId()))
        .findFirst().orElseThrow(AssertionError::new);
    assertEquals("# 项目启动检查单\n\n请补充项目目标", copy.getText());

    projectDocuments.initialize(project.getId());

    assertEquals(10, outlineIds.get());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_document where project_id=?",
        Integer.class, project.getId()));
  }

  @Test
  void hydratesLegacyPublishedTemplateSnapshotBeforeCreatingProject() {
    seedTemplate(7151, "旧发布模板", "START", "REQUIRED", true, "PUBLISHED", 7);
    jdbc.update("update document_template_config set published_title_snapshot=null,"
        + "published_markdown_snapshot=null where knowledge_item_id=7852");

    ProjectView project = projects.create(command());

    Map<String, Object> published = jdbc.queryForMap(
        "select published_title_snapshot,published_markdown_snapshot "
            + "from document_template_config where knowledge_item_id=7852");
    assertEquals("旧发布模板", published.get("published_title_snapshot"));
    assertEquals("# 旧发布模板\n\n请补充项目目标",
        published.get("published_markdown_snapshot"));
    Map<String, Object> snapshot = jdbc.queryForMap(
        "select source_title_snapshot,source_markdown_snapshot "
            + "from project_document where project_id=?",
        project.getId());
    assertEquals("旧发布模板", snapshot.get("source_title_snapshot"));
    assertEquals("# 旧发布模板\n\n请补充项目目标",
        snapshot.get("source_markdown_snapshot"));
  }

  @Test
  void rejectsLegacyPublishedTemplateWhenOutlineRevisionHasChanged() {
    seedTemplate(7152, "无法恢复的旧模板", "START", "REQUIRED", true, "PUBLISHED", 7);
    jdbc.update("update document_template_config set published_title_snapshot=null,"
        + "published_markdown_snapshot=null where knowledge_item_id=7853");
    outlineDocuments.put("template-7152", document(
        "template-7152", null, "无法恢复的旧模板（已修改）", "# 新正文", 8));

    ConflictException failure = assertThrows(
        ConflictException.class, () -> projects.create(command()));

    assertTrue(failure.getMessage().contains("重新发布"));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from delivery_project where code='PRJ-LEGACY-CONFLICT'",
        Integer.class));
  }

  @Test
  void hydratesLegacyPendingProjectSnapshotBeforeInitialization() {
    ProjectView project = projects.create(command());
    seedTemplate(7153, "旧项目待初始化模板", "START", "REQUIRED", true, "PUBLISHED", 9);
    jdbc.update("insert into project_document(project_id,stage_code,source_template_id,"
            + "source_template_revision,requirement,status) values (?,?,?,?,?,'PENDING')",
        project.getId(), "START", 7854, 9, "REQUIRED");

    projectDocuments.initialize(project.getId());

    Map<String, Object> snapshot = jdbc.queryForMap(
        "select source_title_snapshot,source_markdown_snapshot,outline_link_id "
            + "from project_document where project_id=?",
        project.getId());
    assertEquals("旧项目待初始化模板", snapshot.get("source_title_snapshot"));
    assertEquals("# 旧项目待初始化模板\n\n请补充项目目标",
        snapshot.get("source_markdown_snapshot"));
    assertNotNull(snapshot.get("outline_link_id"));
    assertTrue(outlineDocuments.values().stream().anyMatch(document ->
        "旧项目待初始化模板".equals(document.getTitle())
            && "# 旧项目待初始化模板\n\n请补充项目目标".equals(document.getText())
            && !"template-7153".equals(document.getId())));
  }

  @Test
  void rejectsLegacyPendingProjectWhenSourceRevisionCannotBeRecovered() {
    ProjectView project = projects.create(command());
    seedTemplate(7154, "旧项目冲突模板", "START", "REQUIRED", true, "PUBLISHED", 9);
    jdbc.update("insert into project_document(project_id,stage_code,source_template_id,"
            + "source_template_revision,requirement,status) values (?,?,?,?,?,'PENDING')",
        project.getId(), "START", 7855, 9, "REQUIRED");
    outlineDocuments.put("template-7154", document(
        "template-7154", null, "旧项目冲突模板（已更新）", "# 新正文", 10));

    ConflictException failure = assertThrows(
        ConflictException.class, () -> projectDocuments.initialize(project.getId()));

    assertTrue(failure.getMessage().contains("重新创建项目"));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from project_document where project_id=? and outline_link_id is not null",
        Integer.class, project.getId()));
  }

  @Test
  void retriesUnavailableOutlineThenAllowsManualRecovery() throws Exception {
    seedTemplate(7201, "需求调研纪要", "REQUIREMENT", "REQUIRED", true, "PUBLISHED", 2);
    outlineAvailable.set(false);
    ProjectView project = projects.create(command());

    jobs.runDueJobs();
    assertEquals("RETRY", jobStatus(project.getId()));
    assertEquals("FAILED", projects.get(project.getId()).getDocumentSpaceStatus());

    jobs.runDueJobs();
    assertEquals("FAILED", jobStatus(project.getId()));
    assertEquals(2, jobAttempts(project.getId()));

    outlineAvailable.set(true);
    mvc.perform(post("/api/v1/projects/{id}/documents/retry", project.getId())
            .with(authentication(authToken(manager))).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentSpaceStatus").value("PENDING"));

    jobs.runDueJobs();

    assertEquals("DONE", jobStatus(project.getId()));
    assertEquals("READY", projects.get(project.getId()).getDocumentSpaceStatus());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_document where project_id=?",
        Integer.class, project.getId()));
  }

  @Test
  void recoversAJobLeftRunningByAStoppedProcess() {
    ProjectView project = projects.create(command());
    jdbc.update("update document_job set status='RUNNING',lease_token='stale-worker',"
            + "lease_expires_at=?,started_at=?,updated_at=? "
            + "where job_type='PROJECT_INIT' and business_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)), project.getId());

    jobs.runDueJobs();

    assertEquals("DONE", jobStatus(project.getId()));
    assertEquals("READY", projects.get(project.getId()).getDocumentSpaceStatus());
  }

  @Test
  void doesNotReclaimAJobWhoseLeaseIsStillActive() {
    ProjectView project = projects.create(command());
    jdbc.update("update document_job set status='RUNNING',lease_token='active-worker',"
            + "lease_expires_at=?,started_at=?,updated_at=? "
            + "where job_type='PROJECT_INIT' and business_id=?",
        java.sql.Timestamp.from(Instant.now().plusSeconds(600)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)),
        java.sql.Timestamp.from(Instant.now().minusSeconds(600)), project.getId());

    jobs.runDueJobs();

    assertEquals("RUNNING", jobStatus(project.getId()));
    assertEquals("PENDING", projects.get(project.getId()).getDocumentSpaceStatus());
  }

  @Test
  void heartbeatsPreventReclaimWhileAProjectInitializationIsStillRunning() throws Exception {
    ProjectView project = projects.create(command());
    blockNextCreate.set(true);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> running = pool.submit(() -> jobs.runDueJobs());
      assertTrue(blockedCreateStarted.await(2, TimeUnit.SECONDS));
      Thread.sleep(500);

      jobs.runDueJobs();

      assertEquals("RUNNING", jobStatus(project.getId()));
      releaseBlockedCreate.countDown();
      running.get(5, TimeUnit.SECONDS);
      assertEquals("DONE", jobStatus(project.getId()));
      assertEquals("READY", projects.get(project.getId()).getDocumentSpaceStatus());
    } finally {
      releaseBlockedCreate.countDown();
      pool.shutdownNow();
    }
  }

  private void seedTemplate(
      long id, String title, String stage, String requirement, boolean enabled,
      String status, long revision) {
    String documentId = "template-" + id;
    OutlineDocument source = document(
        documentId, null, title, "# " + title + "\n\n请补充项目目标", revision);
    outlineDocuments.put(documentId, source);
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,outline_url_id,title_cache,revision,"
            + "sync_status) values (?,?,?,?,?,?,?,?,?,'READY')",
        id, 701, "KNOWLEDGE:" + id, "TEMPLATE", COLLECTION, documentId,
        documentId, title, revision);
    long knowledgeId = 701 + id;
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
            + "tags_text,status,visibility,owner_user_id,outline_link_id) "
            + "values (?,701,'TEMPLATE',?,?,'legacy','模板',?,'ORGANIZATION',701,?)",
        knowledgeId, title, title + "摘要", status, id);
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
            + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
            + "values (?,?,?,?,?,?,?)",
        knowledgeId, stage, requirement, enabled, revision, title,
        "# " + title + "\n\n请补充项目目标");
  }

  private List<String> stageTitles(long projectId) {
    String prefix = "PROJECT:" + projectId + ":STAGE:";
    Map<String, String> titles = new LinkedHashMap<String, String>();
    for (Map<String, Object> row : jdbc.queryForList(
        "select business_key,title_cache from outline_document_link "
            + "where organization_id=701 and business_key like ?",
        prefix + "%")) {
      String businessKey = String.valueOf(row.get("business_key"));
      titles.put(businessKey.substring(prefix.length()), String.valueOf(row.get("title_cache")));
    }
    return Arrays.stream(DeliveryStage.values())
        .map(stage -> titles.get(stage.name()))
        .collect(Collectors.toList());
  }

  private CreateProjectCommand command() {
    return new CreateProjectCommand(
        701, "华东银行核心系统交付", 701, 701, 701, 701, 701,
        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK");
  }

  private String jobStatus(long projectId) {
    return jdbc.queryForObject(
        "select status from document_job where job_type='PROJECT_INIT' and business_id=?",
        String.class, projectId);
  }

  private int jobAttempts(long projectId) {
    return jdbc.queryForObject(
        "select attempt_count from document_job where job_type='PROJECT_INIT' and business_id=?",
        Integer.class, projectId);
  }

  private UsernamePasswordAuthenticationToken authToken(CurrentUser user) {
    return new UsernamePasswordAuthenticationToken(
        user, "n/a", Collections.singletonList(new SimpleGrantedAuthority("project:write")));
  }

  private void stubOutline() {
    outlineDocuments.clear();
    outlineIds.set(0);
    when(outline.create(any(OutlineConnection.class), anyString(), anyString(), anyString(),
        anyString(), nullable(String.class), anyBoolean())).thenAnswer(invocation -> {
          if (!outlineAvailable.get()) {
            throw new OutlineException(
                OutlineException.Type.UNAVAILABLE, "Outline is unavailable");
          }
          String id = invocation.getArgument(1);
          String title = invocation.getArgument(2);
          String text = invocation.getArgument(3);
          String parent = invocation.getArgument(5);
          if (blockNextCreate.compareAndSet(true, false)) {
            blockedCreateStarted.countDown();
            if (!releaseBlockedCreate.await(5, TimeUnit.SECONDS)) {
              throw new AssertionError("timed out waiting to release blocked Outline create");
            }
          }
          outlineIds.incrementAndGet();
          OutlineDocument document = document(id, parent, title, text, 1);
          outlineDocuments.put(id, document);
          return document;
        });
    when(outline.info(any(OutlineConnection.class), anyString())).thenAnswer(invocation -> {
      if (!outlineAvailable.get()) {
        throw new OutlineException(
            OutlineException.Type.UNAVAILABLE, "Outline is unavailable");
      }
      return outlineDocuments.get(invocation.getArgument(1));
    });
  }

  private OutlineDocument document(
      String id, String parent, String title, String text, long revision) {
    return new OutlineDocument(
        id, COLLECTION, parent, title, text, "/doc/" + id, id, revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }
}
