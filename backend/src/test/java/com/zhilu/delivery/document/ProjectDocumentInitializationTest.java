package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.iam.service.CurrentUser;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
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
  private CurrentUser manager;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "document_job", "project_document", "document_template_config",
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
    stubOutline();
  }

  @Test
  void initializesSevenStageDirectoriesAndCopiesPublishedEnabledTemplatesIdempotently() {
    seedTemplate(7101, "项目启动检查单", "START", "REQUIRED", true, "PUBLISHED", 7);
    seedTemplate(7102, "停用模板", "REQUIREMENT", "OPTIONAL", false, "PUBLISHED", 3);
    seedTemplate(7103, "草稿模板", "GO_LIVE", "REQUIRED", true, "DRAFT", 4);
    ProjectView project = projects.create(command("PRJ-701"));

    assertEquals("PENDING", project.getDocumentSpaceStatus());
    assertEquals(0, outlineIds.get());

    jobs.runDueJobs();

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
  void retriesUnavailableOutlineThenAllowsManualRecovery() throws Exception {
    seedTemplate(7201, "需求调研纪要", "REQUIREMENT", "REQUIRED", true, "PUBLISHED", 2);
    outlineAvailable.set(false);
    ProjectView project = projects.create(command("PRJ-702"));

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
            + "enabled,published_revision) values (?,?,?,?,?)",
        knowledgeId, stage, requirement, enabled, revision);
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

  private CreateProjectCommand command(String code) {
    return new CreateProjectCommand(
        701, code, "华东银行核心系统交付", 701, 701, 701, 701, 701,
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
    when(outline.create(anyString(), anyString(), anyString(),
        nullable(String.class), anyBoolean())).thenAnswer(invocation -> {
          if (!outlineAvailable.get()) {
            throw new OutlineException(
                OutlineException.Type.UNAVAILABLE, "Outline is unavailable");
          }
          String title = invocation.getArgument(0);
          String text = invocation.getArgument(1);
          String parent = invocation.getArgument(3);
          String id = UUID.nameUUIDFromBytes(
              (title + "-" + outlineIds.incrementAndGet()).getBytes(StandardCharsets.UTF_8))
              .toString();
          OutlineDocument document = document(id, parent, title, text, 1);
          outlineDocuments.put(id, document);
          return document;
        });
    when(outline.info(anyString())).thenAnswer(invocation -> {
      if (!outlineAvailable.get()) {
        throw new OutlineException(
            OutlineException.Type.UNAVAILABLE, "Outline is unavailable");
      }
      return outlineDocuments.get(invocation.getArgument(0));
    });
    when(outline.isConfigured()).thenReturn(true);
  }

  private OutlineDocument document(
      String id, String parent, String title, String text, long revision) {
    return new OutlineDocument(
        id, COLLECTION, parent, title, text, "/doc/" + id, id, revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }
}
