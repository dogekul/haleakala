package com.zhilu.delivery.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.standardization.StandardizationService;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class RequirementApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @SpyBean private JdbcTemplate jdbc;
  @Autowired private RequirementService requirements;
  @Autowired private RequirementFeatureService features;
  @Autowired private StandardizationService standardization;
  @SpyBean private RequirementDocumentService requirementDocuments;
  @MockBean private DocumentCenterService documents;
  @MockBean private RequirementClassificationAiService classifications;
  private CurrentUser user;
  private long requirementId;
  private long uncoveredRequirementId;
  private long firstFeature;
  private long secondFeature;
  private long otherProductFeature;
  private long otherOrganizationFeature;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"audit_log", "standardization_debt_requirement",
        "requirement_product_feature", "product_feature", "product_module",
        "standardization_debt", "classification_decision", "classification_suggestion",
        "requirement_item", "outline_document_link", "project_member", "delivery_project", "product_version",
        "product", "app_user", "organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (910,'智鹿','ZHILU-REQ-API')");
    jdbc.update("insert into organization(id,name,code) values (911,'友商','OTHER-REQ-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (910,910,'engineer','工程师','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (914,910,'nonmember','非项目成员','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (911,911,'other','友商工程师','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (910,910,'CRM','CRM','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (911,910,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (912,911,'OTHER','友商产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (910,910,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (911,911,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (912,912,'V1','RELEASED')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (910,910,'CUSTOMER','客户管理','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (911,911,'ORDER','订单管理','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (912,912,'OTHER','友商模块','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (910,910,910,'VALIDATION','客户校验','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (911,910,910,'DUPLICATE','客户查重','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (912,911,911,'ORDER_CREATE','订单创建','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (913,912,912,'OTHER_FEATURE','友商功能','ACTIVE')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (910,910,'PRJ-910','CRM 交付','客户',910,910,910,910)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (910,910,'ENGINEER')");
    requirementId = ((Number) requirements.create(910,"客户主数据校验规则配置","支持按客户类型配置不同校验规则","访谈","P1",910).get("id")).longValue();
    uncoveredRequirementId = ((Number) requirements.create(910,"客户数据导入","批量导入客户并校验字段","访谈","P2",910).get("id")).longValue();
    jdbc.update("insert into standardization_debt(id,product_version_id,pattern_key,title,occurrence_count,distinct_projects) values (910,910,'CUSTOMER_IMPORT','客户数据导入',1,1)");
    jdbc.update("insert into standardization_debt_requirement(standardization_debt_id,requirement_id) values (910,?)", uncoveredRequirementId);
    firstFeature = 910L;
    secondFeature = 911L;
    otherProductFeature = 912L;
    otherOrganizationFeature = 913L;
    user = new CurrentUser(910L,910L,"engineer","工程师",Collections.singletonList("DELIVERY_ENGINEER"),Arrays.asList("requirement:read","requirement:write"));
  }

  @Test void listsVisibleRequirementsAndFunnel() throws Exception {
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        user, null, Collections.singletonList(new SimpleGrantedAuthority("requirement:read")));
    mvc.perform(get("/api/v1/requirements").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productId").value(910));
    mvc.perform(get("/api/v1/requirements/{id}", requirementId)
            .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value(910));
    mvc.perform(get("/api/v1/requirements/funnel").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$.L0").value(0));
  }

  @Test void classifyReturnsCompleteConstructionAndProductionTables() throws Exception {
    when(classifications.analyze(requirementId)).thenReturn(completeClassification());

    mvc.perform(post("/api/v1/requirements/{id}/classify", requirementId)
            .with(actor(user, "requirement:classify")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classificationEvidence[0]").value("客户校验 Spec/当前能力"))
        .andExpect(jsonPath("$.classificationWarnings[0]").value("生产窗口待确认"))
        .andExpect(jsonPath("$.constructionContents[0].changeType").value("ENHANCEMENT"))
        .andExpect(jsonPath("$.productionPlan[0].phase").value("开发与验证"));
  }

  @Test void updatesStructuredFieldsWithOptionalAiReportRegeneration() throws Exception {
    mvc.perform(put("/api/v1/requirements/{id}", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"projectId\":910,\"title\":\"客户主数据规则编辑\","
                + "\"description\":\"补充业务规则和完整验收条件\",\"source\":\"评审会议\","
                + "\"priority\":\"P0\",\"version\":0,\"regenerateReport\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("客户主数据规则编辑"))
        .andExpect(jsonPath("$.version").value(1));
    verify(requirementDocuments, never()).regenerate(requirementId, 910L);

    doNothing().when(requirementDocuments).regenerate(requirementId, 910L);
    mvc.perform(put("/api/v1/requirements/{id}", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"projectId\":910,\"title\":\"客户主数据规则终稿\","
                + "\"description\":\"补充流程、边界和可验证的验收条件\",\"source\":\"评审会议\","
                + "\"priority\":\"P0\",\"version\":1,\"regenerateReport\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("客户主数据规则终稿"));
    verify(requirementDocuments).regenerate(requirementId, 910L);
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from audit_log where action='REQUIREMENT_UPDATED' and resource_id=?",
        Integer.class, String.valueOf(requirementId)));
  }

  @Test void abandonsRequirementWithoutDeletingItsDocumentAndBlocksFurtherActions()
      throws Exception {
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
        + "outline_collection_id,title_cache) values (919,910,'REQ:ABANDON','REQUIREMENT_RESEARCH',"
        + "'collection','保留的需求文档')");
    jdbc.update("update requirement_item set outline_link_id=919 where id=?", requirementId);
    org.mockito.Mockito.when(documents.readLink(919L, 910L)).thenReturn(new DocumentView(
        919L, "保留的需求文档", "# 正文", 3L, Instant.now(), "READY", null,
        "http://outline/doc/919"));

    mvc.perform(post("/api/v1/requirements/{id}/abandon", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABANDONED"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.outlineLinkId").value(919));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='REQUIREMENT_ABANDONED' and resource_id=?",
        Integer.class, String.valueOf(requirementId)));

    mvc.perform(get("/api/v1/requirements/{id}/document", requirementId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outlineUrl").value("http://outline/doc/919"));
    mvc.perform(put("/api/v1/requirements/{id}", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"projectId\":910,\"title\":\"不可编辑\",\"description\":\"不可编辑描述\","
                + "\"priority\":\"P2\",\"version\":1}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/requirements/{id}/classify", requirementId)
            .with(actor(user, "requirement:classify")).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/requirements/{id}/confirm", requirementId)
            .with(actor(user, "requirement:classify")).with(csrf())
            .contentType("application/json").content("{\"level\":\"L0\"}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/requirements/{id}/duplicates", requirementId)
            .with(writer()).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/requirements/{sourceId}/merge/{targetId}",
            requirementId, uncoveredRequirementId).with(writer()).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[]}"))
        .andExpect(status().isConflict());
  }

  @Test void replacesAndReadsMultipleFeatureCoverageEntries() throws Exception {
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"PARTIAL\"},{\"featureId\":" + secondFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementId").value(requirementId))
        .andExpect(jsonPath("$.fullyCovered").value(true))
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].featureCode").value("VALIDATION"))
        .andExpect(jsonPath("$.entries[0].featureName").value("客户校验"))
        .andExpect(jsonPath("$.entries[0].moduleName").value("客户管理"))
        .andExpect(jsonPath("$.entries[0].coverageType").value("PARTIAL"))
        .andExpect(jsonPath("$.entries[1].coverageType").value("FULL"));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=910 and actor_user_id=910 "
            + "and action='REPLACE_COVERAGE' and resource_type='REQUIREMENT' and resource_id=?",
        Integer.class, String.valueOf(requirementId)));

    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullyCovered").value(true))
        .andExpect(jsonPath("$.entries.length()").value(2));
  }

  @Test void invalidCoverageReplacementsPreserveExistingCoverage() throws Exception {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)",
        requirementId, firstFeature);

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[null]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    assertOriginalCoverageRemains();
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_COVERAGE'", Integer.class));

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"FULL\"},{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"PARTIAL\"}]}"))
        .andExpect(status().isBadRequest());
    assertOriginalCoverageRemains();

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + otherProductFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isNotFound());
    assertOriginalCoverageRemains();

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + otherOrganizationFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isNotFound());
    assertOriginalCoverageRemains();
  }

  @Test void concurrentCoverageReplacementsSerializeAndLastWholeReplacementWins()
      throws Exception {
    CountDownLatch firstReachedDelete = new CountDownLatch(1);
    CountDownLatch allowFirstDelete = new CountDownLatch(1);
    doAnswer(invocation -> {
      firstReachedDelete.countDown();
      if (!allowFirstDelete.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("first replacement did not resume");
      }
      return invocation.callRealMethod();
    }).when(jdbc).update(argThat(sql ->
        sql.startsWith("delete from requirement_product_feature")), (Object) any());
    List<RequirementFeatureService.CoverageEntry> first = Collections.singletonList(
        new RequirementFeatureService.CoverageEntry(firstFeature, "PARTIAL"));
    List<RequirementFeatureService.CoverageEntry> second = Collections.singletonList(
        new RequirementFeatureService.CoverageEntry(secondFeature, "PARTIAL"));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Map<String, Object>> firstSave = executor.submit(
          () -> features.replaceCoverage(requirementId, user, first));
      assertTrue(firstReachedDelete.await(5, TimeUnit.SECONDS));
      Future<Map<String, Object>> secondSave = executor.submit(
          () -> features.replaceCoverage(requirementId, user, second));
      assertThrows(TimeoutException.class, () -> secondSave.get(250, TimeUnit.MILLISECONDS));
      allowFirstDelete.countDown();
      firstSave.get(5, TimeUnit.SECONDS);
      secondSave.get(5, TimeUnit.SECONDS);
    } finally {
      allowFirstDelete.countDown();
      executor.shutdownNow();
    }
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from requirement_product_feature where requirement_id=?",
        Integer.class, requirementId));
    assertEquals(Long.valueOf(secondFeature), jdbc.queryForObject(
        "select product_feature_id from requirement_product_feature where requirement_id=?",
        Long.class, requirementId));
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_COVERAGE' and resource_id=?",
        Integer.class, String.valueOf(requirementId)));
  }

  @Test void fullCoverageFirstSerializesThenCandidateIsRejectedWithoutAudit() throws Exception {
    CountDownLatch coverageReachedDelete = new CountDownLatch(1);
    CountDownLatch allowCoverageCommit = new CountDownLatch(1);
    doAnswer(invocation -> {
      coverageReachedDelete.countDown();
      if (!allowCoverageCommit.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("coverage replacement did not resume");
      }
      return invocation.callRealMethod();
    }).when(jdbc).update(argThat(sql ->
        sql.startsWith("delete from requirement_product_feature")), (Object) any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> coverage = executor.submit(() -> features.replaceCoverage(requirementId, user,
          Collections.singletonList(
              new RequirementFeatureService.CoverageEntry(firstFeature, "FULL"))));
      assertTrue(coverageReachedDelete.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> candidate = executor.submit(() -> {
        try {
          standardization.createCandidateFromRequirement(requirementId, user);
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> candidate.get(250, TimeUnit.MILLISECONDS));
      allowCoverageCommit.countDown();
      coverage.get(5, TimeUnit.SECONDS);
      assertTrue(candidate.get(5, TimeUnit.SECONDS) instanceof ConflictException);
    } finally {
      allowCoverageCommit.countDown();
      executor.shutdownNow();
    }
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=?",
        Integer.class, requirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='CREATE_CANDIDATE' and details_text=?",
        Integer.class, "requirementId=" + requirementId));
  }

  @Test void candidateFirstSerializesThenFullCoverageIsRejectedWithoutCoverageAudit()
      throws Exception {
    CountDownLatch candidateReachedInsert = new CountDownLatch(1);
    CountDownLatch allowCandidateCommit = new CountDownLatch(1);
    doAnswer(invocation -> {
      candidateReachedInsert.countDown();
      if (!allowCandidateCommit.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("candidate creation did not resume");
      }
      return invocation.callRealMethod();
    }).when(jdbc).update(argThat(sql -> sql.startsWith("insert into standardization_debt(")),
        any(), any(), any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> candidate = executor.submit(
          () -> standardization.createCandidateFromRequirement(requirementId, user));
      assertTrue(candidateReachedInsert.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> coverage = executor.submit(() -> {
        try {
          features.replaceCoverage(requirementId, user, Collections.singletonList(
              new RequirementFeatureService.CoverageEntry(firstFeature, "FULL")));
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> coverage.get(250, TimeUnit.MILLISECONDS));
      allowCandidateCommit.countDown();
      candidate.get(5, TimeUnit.SECONDS);
      assertTrue(coverage.get(5, TimeUnit.SECONDS) instanceof ConflictException);
    } finally {
      allowCandidateCommit.countDown();
      executor.shutdownNow();
    }
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=?",
        Integer.class, requirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_product_feature where requirement_id=? "
            + "and coverage_type='FULL'", Integer.class, requirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_COVERAGE' and resource_id=?",
        Integer.class, String.valueOf(requirementId)));
  }

  @Test void preservesRequirementProjectDataScopeForCoverage() throws Exception {
    CurrentUser outsider = new CurrentUser(999L, 910L, "outsider", "外部人员",
        Collections.singletonList("DELIVERY_ENGINEER"),
        Arrays.asList("requirement:read", "requirement:write"));
    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(actor(outsider, "requirement:read")))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(actor(outsider, "requirement:write")).with(csrf())
            .contentType("application/json").content("{\"entries\":[]}"))
        .andExpect(status().isNotFound());
  }

  @Test void groupsProductCoverageAndReturnsDebtLinkedUncoveredRequirements() throws Exception {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)", requirementId, firstFeature);
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,coverage_type,source,created_by) values (?,?,'FULL','MANUAL',910)", requirementId, secondFeature);

    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(user, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value(910))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[0].featureId").value(firstFeature))
        .andExpect(jsonPath("$.features[0].fullCount").value(0))
        .andExpect(jsonPath("$.features[0].partialCount").value(1))
        .andExpect(jsonPath("$.features[1].fullCount").value(1))
        .andExpect(jsonPath("$.features[1].partialCount").value(0))
        .andExpect(jsonPath("$.uncoveredRequirements.length()").value(1))
        .andExpect(jsonPath("$.uncoveredRequirements[0].requirementId").value(uncoveredRequirementId))
        .andExpect(jsonPath("$.uncoveredRequirements[0].requirementCode").isNotEmpty())
        .andExpect(jsonPath("$.uncoveredRequirements[0].projectCode").value("PRJ-910"))
        .andExpect(jsonPath("$.uncoveredRequirements[0].debtLinked").value(true));
  }

  @Test void productCoverageRejectsAnotherOrganizationsProduct() throws Exception {
    mvc.perform(get("/api/v1/products/{productId}/coverage", 912)
            .with(actor(user, "product:read")))
        .andExpect(status().isNotFound());
  }

  @Test void productCoverageScopesRequirementsToMembersButAllowsAdmin() throws Exception {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)",
        requirementId, firstFeature);
    CurrentUser nonmember = new CurrentUser(914L, 910L, "nonmember", "非项目成员",
        Collections.singletonList("DELIVERY_ENGINEER"),
        Collections.singletonList("product:read"));

    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(nonmember, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[0].partialCount").value(0))
        .andExpect(jsonPath("$.uncoveredRequirements.length()").value(0));

    CurrentUser admin = new CurrentUser(914L, 910L, "admin", "管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("product:read"));
    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(admin, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[0].partialCount").value(1))
        .andExpect(jsonPath("$.uncoveredRequirements.length()").value(2));
  }

  @Test void candidateCreationRequiresProjectMembershipUnlessCrossScope() throws Exception {
    CurrentUser nonmember = new CurrentUser(914L, 910L, "nonmember", "非项目成员",
        Collections.singletonList("DELIVERY_ENGINEER"),
        Collections.singletonList("requirement:write"));

    mvc.perform(post("/api/v1/standardization/debts/from-requirement")
            .with(actor(nonmember, "requirement:write")).with(csrf())
            .contentType("application/json")
            .content("{\"requirementId\":" + requirementId + "}"))
        .andExpect(status().isNotFound());
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=?",
        Integer.class, requirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='CREATE_CANDIDATE'", Integer.class));

    mvc.perform(post("/api/v1/standardization/debts/from-requirement")
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"requirementId\":" + requirementId + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CANDIDATE"));
  }

  @Test void rejectsHistoricalRequirementBoundToAnotherOrganizationsProduct()
      throws Exception {
    long contaminatedRequirementId = 920L;
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (920,910,'PRJ-CONTAMINATED','历史脏项目','客户',912,910,910,910)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) "
        + "values (920,910,'ENGINEER')");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,status,created_by) "
        + "values (?,910,920,'REQ-CONTAMINATED','历史脏需求','跨组织产品绑定','CONFIRMED',910)",
        contaminatedRequirementId);

    mvc.perform(get("/api/v1/requirements/{id}/product-features", contaminatedRequirementId)
            .with(reader()))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/requirements/{id}/product-features", contaminatedRequirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + otherOrganizationFeature
                + ",\"coverageType\":\"PARTIAL\"}]}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/standardization/debts/from-requirement")
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"requirementId\":" + contaminatedRequirementId + "}"))
        .andExpect(status().isNotFound());

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_product_feature where requirement_id=?",
        Integer.class, contaminatedRequirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=?",
        Integer.class, contaminatedRequirementId));
  }

  @Test void rejectsHistoricalRequirementBoundToAnotherProductsVersion()
      throws Exception {
    long contaminatedRequirementId = 922L;
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (922,910,'PRJ-CONTAMINATED-VERSION','历史版本脏项目','客户',910,912,910,910)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) "
        + "values (922,910,'ENGINEER')");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,status,created_by) "
        + "values (?,910,922,'REQ-CONTAMINATED-VERSION','历史版本脏需求',"
        + "'跨产品版本绑定','CONFIRMED',910)", contaminatedRequirementId);
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)",
        contaminatedRequirementId, firstFeature);

    mvc.perform(get("/api/v1/requirements/{id}/product-features", contaminatedRequirementId)
            .with(reader()))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/requirements/{id}/product-features", contaminatedRequirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/standardization/debts/from-requirement")
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"requirementId\":" + contaminatedRequirementId + "}"))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(user, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[0].partialCount").value(0))
        .andExpect(jsonPath("$.uncoveredRequirements.length()").value(2))
        .andExpect(jsonPath("$.uncoveredRequirements[0].requirementId").value(requirementId))
        .andExpect(jsonPath("$.uncoveredRequirements[1].requirementId")
            .value(uncoveredRequirementId));

    assertEquals("PARTIAL", jdbc.queryForObject(
        "select coverage_type from requirement_product_feature where requirement_id=? "
            + "and product_feature_id=?", String.class, contaminatedRequirementId, firstFeature));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=?",
        Integer.class, contaminatedRequirementId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where resource_id=?",
        Integer.class, String.valueOf(contaminatedRequirementId)));
  }

  @Test void productCoverageExcludesRequirementsOwnedByAnotherOrganization() throws Exception {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)",
        requirementId, firstFeature);
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (921,911,'PRJ-FOREIGN-REQ','友商历史项目','客户',910,910,911,911)");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,status,created_by) "
        + "values (921,911,921,'REQ-FOREIGN','友商需求','不应计入本组织聚合','CONFIRMED',911)");
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (921,?,'PARTIAL','MANUAL',911)", firstFeature);

    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(user, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[0].featureId").value(firstFeature))
        .andExpect(jsonPath("$.features[0].partialCount").value(1));
  }

  @Test void retainedStandardizationCoveragePreservesTraceWhenCoverageIsAdjusted()
      throws Exception {
    Map<String, Object> candidate = standardization.createCandidateFromRequirement(
        requirementId, user);
    long debtId = ((Number) candidate.get("id")).longValue();
    Map<String, Object> converted = standardization.convertToFeature(debtId, user,
        new StandardizationService.ConvertFeatureCommand(910, 910, null,
            "AUTO-TRACE", "自动沉淀功能", "追踪来源", null, 0));
    long convertedFeatureId = ((Number) converted.get("convertedFeatureId")).longValue();
    Map<String, Object> originalTrace = jdbc.queryForMap(
        "select source,created_by,created_at from requirement_product_feature "
            + "where requirement_id=? and product_feature_id=?",
        requirementId, convertedFeatureId);

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"PARTIAL\"},{\"featureId\":" + convertedFeatureId
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].source").value("MANUAL"))
        .andExpect(jsonPath("$.entries[0].createdBy").value(910))
        .andExpect(jsonPath("$.entries[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.entries[1].featureId").value(convertedFeatureId))
        .andExpect(jsonPath("$.entries[1].coverageType").value("FULL"))
        .andExpect(jsonPath("$.entries[1].source").value("STANDARDIZATION"))
        .andExpect(jsonPath("$.entries[1].createdBy").value(910))
        .andExpect(jsonPath("$.entries[1].createdAt").isNotEmpty());

    Map<String, Object> retainedTrace = jdbc.queryForMap(
        "select source,created_by,created_at from requirement_product_feature "
            + "where requirement_id=? and product_feature_id=?",
        requirementId, convertedFeatureId);
    assertEquals(originalTrace.get("source"), retainedTrace.get("source"));
    assertEquals(originalTrace.get("created_by"), retainedTrace.get("created_by"));
    assertEquals(originalTrace.get("created_at"), retainedTrace.get("created_at"));
    assertEquals("MANUAL", jdbc.queryForObject(
        "select source from requirement_product_feature where requirement_id=? "
            + "and product_feature_id=?", String.class, requirementId, firstFeature));

    features.replaceCoverage(requirementId, user, Collections.singletonList(
        new RequirementFeatureService.CoverageEntry(firstFeature, "PARTIAL")));
    features.replaceCoverage(requirementId, user, Arrays.asList(
        new RequirementFeatureService.CoverageEntry(firstFeature, "PARTIAL"),
        new RequirementFeatureService.CoverageEntry(convertedFeatureId, "PARTIAL")));
    assertEquals("MANUAL", jdbc.queryForObject(
        "select source from requirement_product_feature where requirement_id=? "
            + "and product_feature_id=?", String.class, requirementId, convertedFeatureId));
  }

  @Test void failedCoverageReplacementRollsBackTraceAndCoverageChanges() {
    Timestamp originalCreatedAt = Timestamp.valueOf("2025-01-02 03:04:05");
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
            + "coverage_type,source,created_by,created_at) "
            + "values (?,?,'PARTIAL','STANDARDIZATION',910,?)",
        requirementId, firstFeature, originalCreatedAt);
    doAnswer(invocation -> {
      throw new IllegalStateException("forced audit failure");
    }).when(jdbc).update(argThat(sql -> sql.startsWith("insert into audit_log")),
        any(), any(), any(), any(), any(), any(), any());

    assertThrows(IllegalStateException.class, () -> features.replaceCoverage(
        requirementId, user, Arrays.asList(
            new RequirementFeatureService.CoverageEntry(firstFeature, "FULL"),
            new RequirementFeatureService.CoverageEntry(secondFeature, "PARTIAL"))));

    Map<String, Object> retained = jdbc.queryForMap(
        "select product_feature_id,coverage_type,source,created_by,created_at "
            + "from requirement_product_feature where requirement_id=?", requirementId);
    assertEquals(firstFeature, ((Number) retained.get("product_feature_id")).longValue());
    assertEquals("PARTIAL", retained.get("coverage_type"));
    assertEquals("STANDARDIZATION", retained.get("source"));
    assertEquals(910L, ((Number) retained.get("created_by")).longValue());
    assertEquals(originalCreatedAt, retained.get("created_at"));
  }

  private ObjectNode completeClassification() {
    ObjectNode result = json.createObjectNode();
    result.put("level", "L1"); result.put("confidence", 0.9);
    result.put("reason", "需要增强开发");
    result.putArray("evidence").add("客户校验 Spec/当前能力");
    result.putArray("warnings").add("生产窗口待确认");
    ObjectNode row = result.putArray("constructionContents").addObject();
    row.put("moduleName", "客户管理"); row.put("featureCode", "VALIDATION");
    row.put("featureName", "客户校验"); row.put("versionAvailability", "INCLUDED");
    row.put("currentCapability", "校验证件格式"); row.put("gap", "缺少有效期校验");
    row.put("changeType", "ENHANCEMENT"); row.put("constructionContent", "增强开发");
    row.put("acceptanceCriteria", "无效证件被拦截"); row.put("priority", "P1");
    row.put("evidence", "客户校验 Spec/当前能力");
    ObjectNode phase = result.putArray("productionPlan").addObject();
    phase.put("phase", "开发与验证"); phase.put("workItem", "完成增强和回归测试");
    phase.put("ownerRole", "研发、测试"); phase.put("plannedStart", "待确认");
    phase.put("plannedEnd", "待确认"); phase.put("deliverable", "发布包和测试报告");
    phase.put("entryCriteria", "方案评审通过"); phase.put("exitCriteria", "验收通过");
    phase.put("riskAndRollback", "灰度发布并验证回退");
    return result;
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
    return actor(user, "requirement:read");
  }

  private void assertOriginalCoverageRemains() throws Exception {
    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullyCovered").value(false))
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].featureId").value(firstFeature))
        .andExpect(jsonPath("$.entries[0].coverageType").value("PARTIAL"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
    return actor(user, "requirement:write");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor actor(
      CurrentUser currentUser, String authority) {
    return authentication(new UsernamePasswordAuthenticationToken(currentUser, null,
        Collections.singletonList(new SimpleGrantedAuthority(authority))));
  }
}
