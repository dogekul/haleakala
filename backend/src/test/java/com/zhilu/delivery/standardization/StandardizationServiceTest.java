package com.zhilu.delivery.standardization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.zhilu.delivery.catalog.ProductStructureService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:standardization;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class StandardizationServiceTest {
  @SpyBean private JdbcTemplate jdbc;
  @Autowired private StandardizationService standardization;
  @SpyBean private ProductStructureService structures;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"audit_log","product_version_feature","requirement_product_feature",
        "standardization_debt_requirement","flywheel_metric","cost_attribution",
        "standardization_debt","maturity_assessment","product_baseline","custom_dev_task",
        "classification_decision","requirement_item","delivery_project","product_feature",
        "product_module","product_version","product","app_user","organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (1000,'智鹿','ZHILU-STD')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (1000,1000,'product','产品经理','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (1000,1000,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1000,1000,'V5','RELEASED')");
    for (int i=0;i<5;i++) {
      long id=1000+i;
      jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (?,1000,?,?,?,1000,1000,1000,1000)",id,"P-"+id,"项目"+id,"客户");
      jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,title,description,status,created_by) values (?,1000,?,?,?,?,'CONFIRMED',1000)",id,id,"R-"+id,"批量对账重跑","批量对账重跑与差异定位");
      jdbc.update("insert into classification_decision(requirement_id,confirmed_level,confirmed_by) values (?,'L1',1000)",id);
      jdbc.update("insert into custom_dev_task(requirement_id,project_id,title,extension_point,status,actual_person_days,actual_cost) values (?,?,?,'reconciliation.retry','DONE',10,20000)",id,id,"对账重跑");
    }
  }

  @Test void fiveProjectsCreateOneIdempotentCandidateAndCloseNeedsVerification() {
    List<Map<String,Object>> first=standardization.evaluateDebts(1000,1000);
    List<Map<String,Object>> second=standardization.evaluateDebts(1000,1000);
    assertEquals(1,first.size()); assertEquals(1,second.size());
    assertEquals(1,jdbc.queryForObject("select count(*) from standardization_debt",Integer.class));
    long debt=((Number)first.get(0).get("id")).longValue();
    assertThrows(ConflictException.class,()->standardization.transitionDebt(debt,"CLOSED","直接关闭",1000));
    standardization.transitionDebt(debt,"PENDING",null,1000);
    standardization.transitionDebt(debt,"INCLUDED",null,1000);
    standardization.transitionDebt(debt,"VERIFYING","进入验证",1000);
    assertEquals("CLOSED",standardization.transitionDebt(debt,"CLOSED","五项目回归通过",1000).get("status"));
  }

  @Test void maturityAndCostAreDeterministic() {
    Map<String,Object> assessment=standardization.assess(1000,1000);
    assertEquals(0,((Number)assessment.get("standardCoverage")).intValue());
    assertEquals(50,((Number)assessment.get("maturityScore")).intValue());
    assertEquals(100000.0,((Number)standardization.costs(1000).get("actualCost")).doubleValue(),0.01);
  }

  @Test void flywheelUsesAuthenticatedActorBeforeFirstDeliveryProject() {
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (1001,1000,'V6','RELEASED')");

    Map<String, Object> flywheel = standardization.flywheel(1001, 1000);

    assertEquals(0, ((Number) flywheel.get("confirmedRequirements")).intValue());
    assertEquals(0, ((Number) flywheel.get("reuseRate")).intValue());
    assertEquals(Long.valueOf(1000), jdbc.queryForObject(
        "select assessed_by from maturity_assessment where product_version_id=1001",
        Long.class));
  }

  @Test void concurrentFirstAssessmentsRecoverFromDuplicateInsert() throws Exception {
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (1001,1000,'V6','RELEASED')");
    CountDownLatch bothAtInsert = new CountDownLatch(2);
    doAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.startsWith("insert into maturity_assessment")) {
        bothAtInsert.countDown();
        if (!bothAtInsert.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("assessments did not race at first insert");
        }
      }
      return invocation.callRealMethod();
    }).when(jdbc).update(argThat(sql -> sql.startsWith("insert into maturity_assessment")),
        any(), any(), any(), any(), any(), any(), any(), any(), any());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Map<String, Object>> first = executor.submit(
          () -> standardization.assess(1001, 1000));
      Future<Map<String, Object>> second = executor.submit(
          () -> standardization.assess(1001, 1000));

      Map<String, Object> firstAssessment = first.get(10, TimeUnit.SECONDS);
      Map<String, Object> secondAssessment = second.get(10, TimeUnit.SECONDS);
      assertEquals(firstAssessment, secondAssessment);
      assertEquals(Integer.valueOf(((Number) firstAssessment.get("maturityScore")).intValue()),
          jdbc.queryForObject("select maturity_score from maturity_assessment "
              + "where product_version_id=1001", Integer.class));
    } finally {
      executor.shutdownNow();
    }

    assertEquals(0L, bothAtInsert.getCount());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from maturity_assessment where product_version_id=1001",
        Integer.class));
    assertEquals(Long.valueOf(1000), jdbc.queryForObject(
        "select assessed_by from maturity_assessment where product_version_id=1001",
        Long.class));
  }

  @Test void uncoveredRequirementCreatesOneCandidateWhileFullCoverageIsRejected() {
    jdbc.update("insert into product_module(id,product_id,code,name,status) "
        + "values (1000,1000,'FIN','财务','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) "
        + "values (1000,1000,1000,'RECON','对账','ACTIVE')");
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (1001,1000,'FULL','MANUAL',1000)");

    Map<String, Object> candidate = standardization.createCandidateFromRequirement(1000, actor());

    assertEquals("CANDIDATE", candidate.get("status"));
    assertEquals(0L, ((Number) candidate.get("version")).longValue());
    assertEquals("REQUIREMENT:1000", candidate.get("patternKey"));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from standardization_debt_requirement where requirement_id=1000",
        Integer.class));
    assertThrows(ConflictException.class,
        () -> standardization.createCandidateFromRequirement(1000, actor()));
    assertThrows(ConflictException.class,
        () -> standardization.createCandidateFromRequirement(1001, actor()));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from standardization_debt", Integer.class));
  }

  @Test void candidateConversionCreatesFeatureCoverageManifestDebtTraceAndAudit() {
    seedConversionCatalog();
    long debtId = debtLinkedTo(1000, 1001);

    Map<String, Object> converted = standardization.convertToFeature(debtId, actor(),
        command(1000, 1000, 1001L, "RECON-RETRY", 0));

    Number featureId = (Number) converted.get("convertedFeatureId");
    assertNotNull(featureId);
    assertEquals("INCLUDED", converted.get("status"));
    assertEquals("V6", converted.get("targetVersion"));
    assertEquals(1L, ((Number) converted.get("version")).longValue());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_feature where id=? and code='RECON-RETRY'",
        Integer.class, featureId.longValue()));
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from requirement_product_feature where product_feature_id=? "
            + "and coverage_type='PARTIAL'",
        Integer.class, featureId.longValue()));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=1001 "
            + "and product_feature_id=? and availability='PLANNED'",
        Integer.class, featureId.longValue()));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='CONVERT_TO_FEATURE' "
            + "and resource_type='STANDARDIZATION_DEBT' and resource_id=?",
        Integer.class, String.valueOf(debtId)));
  }

  @Test void crossProductModuleRollsBackConversion() {
    seedConversionCatalog();
    seedSecondProduct();
    long debtId = debtLinkedTo(1000, 1001);

    assertThrows(IllegalArgumentException.class, () -> standardization.convertToFeature(
        debtId, actor(), command(1000, 2000, null, "BROKEN-CONVERT", 0)));

    assertConversionRolledBack(debtId, "BROKEN-CONVERT");
  }

  @Test void linkedRequirementFromAnotherProductRollsBackConversion() {
    seedConversionCatalog();
    seedSecondProduct();
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (2000,1000,'P-2000','其他项目','客户',2000,2000,1000,1000)");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,status,created_by) "
        + "values (2000,1000,2000,'R-2000','其他产品需求','需求','CONFIRMED',1000)");
    long debtId = debtLinkedTo(1000, 2000);

    assertThrows(IllegalArgumentException.class, () -> standardization.convertToFeature(
        debtId, actor(), command(1000, 1000, null, "WRONG-PRODUCT", 0)));

    assertConversionRolledBack(debtId, "WRONG-PRODUCT");
  }

  @Test void staleDebtVersionRollsBackFeatureAndCoverage() {
    seedConversionCatalog();
    long debtId = debtLinkedTo(1000, 1001);
    jdbc.update("update standardization_debt set version=1 where id=?", debtId);

    assertThrows(ConflictException.class, () -> standardization.convertToFeature(
        debtId, actor(), command(1000, 1000, null, "STALE-CONVERT", 0)));

    assertConversionRolledBack(debtId, "STALE-CONVERT");
  }

  @Test void failureAfterFeatureAndCoverageWritesRollsBackEverySideEffect() {
    seedConversionCatalog();
    long debtId = debtLinkedTo(1000, 1001);

    assertThrows(ConflictException.class, () -> standardization.convertToFeature(
        debtId, actor(), command(1000, 1000, 1000L, "RELEASED-ROLLBACK", 0)));

    assertDeepRollback(debtId, "RELEASED-ROLLBACK", 1000, 0, 0);
  }

  @Test void concurrentConversionsReachFinalCasAndLoserRollsBackAllWrites() throws Exception {
    seedConversionCatalog();
    long debtId = debtLinkedTo(1000, 1001);
    CountDownLatch bothPassedEntryValidation = new CountDownLatch(2);
    doAnswer(invocation -> {
      bothPassedEntryValidation.countDown();
      if (!bothPassedEntryValidation.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrent conversions did not reach feature creation");
      }
      return invocation.callRealMethod();
    }).when(structures).saveFeature(anyLong(), anyLong(), anyLong(), any(), anyLong(), any(),
        anyString(), anyString(), anyString(), anyString(), anyLong());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Map<String, Object>> first = executor.submit(() -> standardization.convertToFeature(
          debtId, actor(), command(1000, 1000, 1001L, "CAS-WINNER-A", 0)));
      Future<Map<String, Object>> second = executor.submit(() -> standardization.convertToFeature(
          debtId, actor(), command(1000, 1000, 1001L, "CAS-WINNER-B", 0)));
      List<Future<Map<String, Object>>> conversions = Arrays.asList(first, second);
      int succeeded = 0;
      int finalCasConflicts = 0;
      for (Future<Map<String, Object>> conversion : conversions) {
        try {
          Map<String, Object> value = conversion.get(10, TimeUnit.SECONDS);
          assertEquals("INCLUDED", value.get("status"));
          succeeded++;
        } catch (ExecutionException failed) {
          assertTrue(failed.getCause() instanceof ConflictException);
          assertEquals("标准化债务已被更新，请刷新后重试",
              failed.getCause().getMessage());
          finalCasConflicts++;
        }
      }
      assertEquals(0L, bothPassedEntryValidation.getCount());
      assertEquals(1, succeeded);
      assertEquals(1, finalCasConflicts);
    } finally {
      executor.shutdownNow();
    }

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_feature where code in ('CAS-WINNER-A','CAS-WINNER-B')",
        Integer.class));
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from requirement_product_feature", Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=1001",
        Integer.class));
    assertEquals(Long.valueOf(1), jdbc.queryForObject(
        "select version from product_version where id=1001", Long.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='CREATE' and resource_type='PRODUCT_FEATURE'",
        Integer.class));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='CONVERT_TO_FEATURE'", Integer.class));
    assertEquals("INCLUDED", jdbc.queryForObject(
        "select status from standardization_debt where id=?", String.class, debtId));
    assertEquals(Long.valueOf(1), jdbc.queryForObject(
        "select version from standardization_debt where id=?", Long.class, debtId));
    assertNotNull(jdbc.queryForObject(
        "select converted_feature_id from standardization_debt where id=?", Long.class, debtId));
  }

  @Test void candidateCreationAndConversionRejectAnotherOrganization() {
    seedConversionCatalog();
    long debtId = debtLinkedTo(1000, 1001);
    CurrentUser outsider = new CurrentUser(2000L, 2000L, "outsider", "外部用户",
        Collections.singletonList("PRODUCT_MANAGER"),
        Collections.singletonList("standardization:write"));

    assertThrows(NotFoundException.class,
        () -> standardization.createCandidateFromRequirement(1002, outsider));
    assertThrows(NotFoundException.class, () -> standardization.convertToFeature(
        debtId, outsider, command(1000, 1000, null, "CROSS-ORG", 0)));

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from standardization_debt where pattern_key='REQUIREMENT:1002'",
        Integer.class));
    assertDeepRollback(debtId, "CROSS-ORG", 1001, 0, 0);
  }

  private CurrentUser actor() {
    return new CurrentUser(1000L, 1000L, "product", "产品经理",
        Collections.singletonList("PRODUCT_MANAGER"),
        Collections.singletonList("standardization:write"));
  }

  private void seedConversionCatalog() {
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (1001,1000,'V6','PLANNING')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) "
        + "values (1000,1000,'FIN','财务','ACTIVE')");
  }

  private void seedSecondProduct() {
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (2000,1000,'CRM','CRM','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (2000,2000,'V1','RELEASED')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) "
        + "values (2000,2000,'CUSTOMER','客户','ACTIVE')");
  }

  private long debtLinkedTo(long... requirementIds) {
    jdbc.update("insert into standardization_debt(product_version_id,pattern_key,title,"
        + "occurrence_count,distinct_projects,status) values "
        + "(1000,?, '对账重跑',2,2,'CANDIDATE')", "TEST:" + requirementIds[0]);
    long debtId = jdbc.queryForObject(
        "select id from standardization_debt where pattern_key=?", Long.class,
        "TEST:" + requirementIds[0]);
    for (long requirementId : requirementIds) {
      jdbc.update("insert into standardization_debt_requirement(standardization_debt_id,"
          + "requirement_id) values (?,?)", debtId, requirementId);
    }
    return debtId;
  }

  private StandardizationService.ConvertFeatureCommand command(long productId, long moduleId,
      Long productVersionId, String code, long version) {
    return new StandardizationService.ConvertFeatureCommand(productId, moduleId, productVersionId,
        code, "对账重跑", "沉淀交付项目的对账重跑能力", 1000L, version);
  }

  private void assertConversionRolledBack(long debtId, String code) {
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_feature where code=?", Integer.class, code));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_product_feature", Integer.class));
    assertEquals("CANDIDATE", jdbc.queryForObject(
        "select status from standardization_debt where id=?", String.class, debtId));
    assertEquals(null, jdbc.queryForObject(
        "select converted_feature_id from standardization_debt where id=?", Long.class, debtId));
  }

  private void assertDeepRollback(long debtId, String code, long productVersionId,
      long expectedProductVersion, long expectedDebtVersion) {
    assertConversionRolledBack(debtId, code);
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_version_feature", Integer.class));
    assertEquals(Long.valueOf(expectedProductVersion), jdbc.queryForObject(
        "select version from product_version where id=?", Long.class, productVersionId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where "
            + "(action='CREATE' and resource_type='PRODUCT_FEATURE') "
            + "or action='CONVERT_TO_FEATURE'",
        Integer.class));
    assertEquals(Long.valueOf(expectedDebtVersion), jdbc.queryForObject(
        "select version from standardization_debt where id=?", Long.class, debtId));
  }
}
