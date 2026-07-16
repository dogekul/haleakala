package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.ProjectService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hamcrest.Matchers;
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
    "spring.datasource.url=jdbc:h2:mem:project-document-lifecycle;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
@AutoConfigureMockMvc
class ProjectDocumentLifecycleIT {
  private static final String COLLECTION = "a4296a54-2044-4529-ba86-d598a5322e06";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private ProjectDocumentService projectDocuments;
  @Autowired private ProjectService projects;
  @MockBean private OutlineClient outline;

  private final Map<String, OutlineDocument> outlineDocuments =
      new LinkedHashMap<String, OutlineDocument>();
  private CurrentUser manager;
  private CurrentUser member;
  private long projectId;
  private long todoDocumentId;
  private long requiredDocumentId;
  private long optionalDocumentId;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "audit_log", "document_job", "project_document", "document_template_config", "knowledge_item",
        "outline_document_link", "project_activity", "stage_instance", "project_member",
        "delivery_project", "customer", "product_version", "product", "app_user", "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (7300,'智鹿','DOC-LIFECYCLE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(7300,7300,'manager','项目经理','ACTIVE'),"
        + "(7301,7300,'member','实施顾问','ACTIVE'),"
        + "(7302,7300,'admin','系统管理员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (7300,7300,'CORE','核心系统','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (7300,7300,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (7300,7300,'华东银行','ACTIVE')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
            + "customer_id,product_id,product_version_id,manager_user_id,status,current_stage,"
            + "risk_level,gate_mode,document_space_status,created_by) values "
            + "(7300,7300,'PRJ-7300','华东银行核心系统交付','华东银行',7300,7300,7300,7300,"
            + "'ACTIVE','START','GREEN','BLOCK','READY',7300)");
    projectId = 7300;
    jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
        + "values (7300,7300,'DELIVERY_MANAGER',100),(7300,7301,'DELIVERY_ENGINEER',100)");
    String[] names = {
        "启动", "需求采集", "二开实施", "上线切换", "试运行与移交", "标准化评估", "项目收尾"
    };
    String[] codes = {
        "START", "REQUIREMENT", "CUSTOM_DEV", "GO_LIVE",
        "TRIAL_HANDOVER", "STANDARDIZATION", "CLOSE"
    };
    for (int index = 0; index < codes.length; index++) {
      jdbc.update("insert into stage_instance(project_id,stage_code,stage_name,stage_order,status,"
              + "gate_status,started_at) values (7300,?,?,?,?,'READY',?)",
          codes[index], names[index], index + 1, index == 0 ? "ACTIVE" : "PENDING",
          index == 0 ? java.sql.Timestamp.from(Instant.now()) : null);
    }
    manager = user(7300, "DELIVERY_MANAGER");
    member = user(7301, "DELIVERY_ENGINEER");
    outlineDocuments.clear();
    todoDocumentId = projectDocument(
        7310, "项目启动检查单", "# 项目启动检查单\n\n请补充项目目标", 1, "REQUIRED");
    requiredDocumentId = projectDocument(
        7320, "项目启动方案", "# 项目启动方案\n\n项目目标为完成核心系统升级。", 2, "REQUIRED");
    optionalDocumentId = projectDocument(
        7330, "参考资料", "# 参考资料\n\n请补充相关链接", 1, "OPTIONAL");
    when(outline.info(anyString())).thenAnswer(
        invocation -> outlineDocuments.get(invocation.getArgument(0)));
  }

  @Test
  void refreshesStatusConfirmsCurrentRevisionAndInvalidatesAfterExternalEdit() throws Exception {
    mvc.perform(get("/api/v1/projects/{id}/documents", projectId)
            .with(actor(member, "project:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id==" + todoDocumentId + ")].status")
            .value("TODO"))
        .andExpect(jsonPath("$[?(@.id==" + requiredDocumentId + ")].status")
            .value("PENDING_CONFIRMATION"));

    mvc.perform(post("/api/v1/projects/{id}/documents/{documentId}/confirm",
            projectId, requiredDocumentId)
            .with(actor(member, "project:write")).with(csrf()))
        .andExpect(status().isNotFound());

    mvc.perform(post("/api/v1/projects/{id}/documents/{documentId}/confirm",
            projectId, requiredDocumentId)
            .with(actor(manager, "project:write")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.confirmedRevision").value(2));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='CONFIRM' "
            + "and resource_type='PROJECT_DOCUMENT' and resource_id=?",
        Integer.class, String.valueOf(requiredDocumentId)));

    String outlineId = "project-document-7320";
    outlineDocuments.put(outlineId, document(
        outlineId, "项目启动方案", "# 项目启动方案\n\n外部修改后的项目目标。", 3));

    mvc.perform(get("/api/v1/projects/{id}/documents", projectId)
            .with(actor(member, "project:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id==" + requiredDocumentId + ")].status")
            .value("PENDING_CONFIRMATION"))
        .andExpect(jsonPath("$[?(@.id==" + requiredDocumentId + ")].confirmedRevision")
            .value(Matchers.contains(Matchers.nullValue())));
    assertNull(jdbc.queryForObject(
        "select confirmed_revision from project_document where id=?",
        Long.class, requiredDocumentId));
  }

  @Test
  void sectionHeadingsWithoutAnswersRemainTodo() throws Exception {
    String outlineId = "project-document-7320";
    outlineDocuments.put(outlineId, document(
        outlineId, "项目启动方案",
        "# 项目启动方案\n\n## 项目目标\n\n### 项目范围\n\n## 关键干系人", 3));

    mvc.perform(get("/api/v1/projects/{id}/documents", projectId)
            .with(actor(member, "project:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id==" + requiredDocumentId + ")].status")
            .value("TODO"));

    mvc.perform(post("/api/v1/projects/{id}/documents/{documentId}/confirm",
            projectId, requiredDocumentId)
            .with(actor(manager, "project:write")).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void staleRefreshCannotClearANewerConfirmation() throws Exception {
    CountDownLatch staleRefreshRead = new CountDownLatch(1);
    CountDownLatch continueStaleRefresh = new CountDownLatch(1);
    AtomicBoolean delayRequiredDocument = new AtomicBoolean(true);
    when(outline.info(anyString())).thenAnswer(invocation -> {
      String documentId = invocation.getArgument(0);
      if ("project-document-7320".equals(documentId)
          && delayRequiredDocument.compareAndSet(true, false)) {
        staleRefreshRead.countDown();
        assertTrue(continueStaleRefresh.await(5, TimeUnit.SECONDS));
      }
      return outlineDocuments.get(documentId);
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<List<Map<String, Object>>> stale =
          executor.submit(() -> projectDocuments.list(projectId, member));
      assertTrue(staleRefreshRead.await(5, TimeUnit.SECONDS));

      Map<String, Object> confirmed =
          projectDocuments.confirm(projectId, requiredDocumentId, manager);
      assertEquals("COMPLETED", confirmed.get("status"));
      assertEquals(2L, ((Number) confirmed.get("confirmedRevision")).longValue());

      continueStaleRefresh.countDown();
      stale.get(5, TimeUnit.SECONDS);

      assertEquals(Long.valueOf(2L), jdbc.queryForObject(
          "select confirmed_revision from project_document where id=?",
          Long.class, requiredDocumentId));
      assertEquals("COMPLETED", jdbc.queryForObject(
          "select status from project_document where id=?",
          String.class, requiredDocumentId));
    } finally {
      continueStaleRefresh.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void blockModeReturnsAllIncompleteRequiredDocumentsButNotOptionalOnes() throws Exception {
    mvc.perform(post("/api/v1/projects/{id}/advance", projectId)
            .with(actor(manager, "project:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStage\":\"REQUIREMENT\",\"mode\":\"WARNING\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", Matchers.containsString("项目启动检查单")))
        .andExpect(jsonPath("$.message", Matchers.containsString("项目启动方案")))
        .andExpect(jsonPath("$.message", Matchers.not(Matchers.containsString("参考资料"))));
  }

  @Test
  void warningModeAllowsAdvanceAndAuditsTheIncompleteDocumentList() throws Exception {
    jdbc.update("update delivery_project set gate_mode='WARNING' where id=?", projectId);

    mvc.perform(post("/api/v1/projects/{id}/advance", projectId)
            .with(actor(manager, "project:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStage\":\"REQUIREMENT\",\"mode\":\"BLOCK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStage").value("REQUIREMENT"));

    Map<String, Object> activity = jdbc.queryForMap(
        "select action,details_text from project_activity where project_id=? "
            + "order by id desc limit 1",
        projectId);
    assertEquals("STAGE_ADVANCED_WITH_WARNING", activity.get("action"));
    assertEquals(true, String.valueOf(activity.get("details_text")).contains("项目启动检查单"));
    assertEquals(true, String.valueOf(activity.get("details_text")).contains("项目启动方案"));
    assertEquals(false, String.valueOf(activity.get("details_text")).contains("参考资料"));
  }

  @Test
  void optionalDocumentDoesNotBlockWhenEveryRequiredDocumentIsConfirmed() {
    projectDocuments.list(projectId, member);
    String todoOutlineId = "project-document-7310";
    outlineDocuments.put(todoOutlineId, document(
        todoOutlineId, "项目启动检查单", "# 项目启动检查单\n\n启动目标已确认。", 2));
    projectDocuments.confirm(projectId, todoDocumentId, manager);
    projectDocuments.confirm(projectId, requiredDocumentId, manager);

    projects.advanceStage(
        projectId, com.zhilu.delivery.project.DeliveryStage.REQUIREMENT, manager);

    assertEquals("REQUIREMENT", projects.get(projectId).getCurrentStage());
    assertEquals("TODO", jdbc.queryForObject(
        "select status from project_document where id=?", String.class, optionalDocumentId));
  }

  @Test
  void systemManagerCanConfirmWithoutProjectMembership() {
    CurrentUser systemManager = new CurrentUser(
        7302L, 7300L, "admin", "系统管理员",
        Collections.<String>emptyList(), Collections.singletonList("system:manage"));

    Map<String, Object> confirmed =
        projectDocuments.confirm(projectId, requiredDocumentId, systemManager);

    assertEquals("COMPLETED", confirmed.get("status"));
    assertEquals(7302L, ((Number) confirmed.get("confirmedBy")).longValue());
  }

  private long projectDocument(
      long seed, String title, String markdown, long revision, String requirement) {
    String outlineId = "project-document-" + seed;
    outlineDocuments.put(outlineId, document(outlineId, title, markdown, revision));
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,outline_url_id,title_cache,revision,"
            + "sync_status) values (?,?,?,'PROJECT_DOCUMENT',?,?,?,?,?,'READY')",
        seed, 7300, "PROJECT:7300:DOC:" + seed, COLLECTION, outlineId, outlineId, title, revision);
    long knowledgeId = seed + 10000;
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
            + "visibility,status,owner_user_id) values "
            + "(?,7300,'TEMPLATE',?,?,'legacy','ORGANIZATION','PUBLISHED',7300)",
        knowledgeId, title, title + "摘要");
    jdbc.update("insert into project_document(project_id,stage_code,source_template_id,"
            + "source_template_revision,outline_link_id,requirement,status) "
            + "values (7300,'START',?,1,?,?,'PENDING')",
        knowledgeId, seed, requirement);
    return jdbc.queryForObject(
        "select id from project_document where project_id=7300 and source_template_id=?",
        Long.class, knowledgeId);
  }

  private OutlineDocument document(
      String id, String title, String markdown, long revision) {
    return new OutlineDocument(
        id, COLLECTION, null, title, markdown, "/doc/" + id, id, revision,
        Instant.parse("2026-07-16T08:00:00Z"));
  }

  private CurrentUser user(long id, String role) {
    return new CurrentUser(
        id, 7300L, "user-" + id, "User " + id,
        Collections.singletonList(role), Arrays.asList("project:read", "project:write"));
  }

  private RequestPostProcessor actor(CurrentUser user, String permission) {
    return authentication(new UsernamePasswordAuthenticationToken(
        user, null, Collections.singletonList(new SimpleGrantedAuthority(permission))));
  }
}
