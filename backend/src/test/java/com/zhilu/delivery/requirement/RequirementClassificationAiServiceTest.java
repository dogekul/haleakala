package com.zhilu.delivery.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-classification-ai;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class RequirementClassificationAiServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private RequirementClassificationAiService classifications;
  @MockBean private AiClient ai;
  @MockBean private DocumentCenterService documents;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"requirement_product_feature", "product_document_node",
        "product_version_feature", "product_feature", "product_module", "requirement_item",
        "outline_document_link", "delivery_project", "product_version", "product", "app_user",
        "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (4300,'智鹿','AI-DETAIL')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (4300,4300,'engineer','交付工程师','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (4300,4300,'CRM','客户管理平台','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (4300,4300,'V2.0','RELEASED')");
    jdbc.update("insert into product_module(id,product_id,code,name,status,sort_order) "
        + "values (4300,4300,'CUSTOMER','客户管理','ACTIVE',1)");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,description,status) "
        + "values (4301,4300,4300,'VALIDATION','客户校验','校验客户证件和基础字段','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,description,status) "
        + "values (4302,4300,4300,'DUPLICATE','客户查重','识别重复客户','ACTIVE')");
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
        + "availability) values (4300,4301,'INCLUDED'),(4300,4302,'PLANNED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,start_date,planned_end_date,created_by) "
        + "values (4300,4300,'PRJ-4300','客户平台交付','示例银行',4300,4300,4300,"
        + "'2026-08-01','2026-10-31',4300)");
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
        + "outline_collection_id,title_cache) values "
        + "(4305,4300,'REQUIREMENT:4304:RESEARCH_REPORT','REQUIREMENT_RESEARCH','collection',"
        + "'需求调研报告'),"
        + "(4306,4300,'PRODUCT:4300:FEATURE:4301:SPEC','PRODUCT_FEATURE_SPEC','collection',"
        + "'客户校验 Spec')");
    jdbc.update("insert into product_document_node(id,product_id,node_type,code,title,sort_order,"
        + "outline_link_id,linked_feature_id) values "
        + "(4306,4300,'DOCUMENT','VALIDATION-SPEC','客户校验 Spec',1,4306,4301)");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,source,priority,status,created_by,outline_link_id) values "
        + "(4304,4300,4300,'REQ-4304','增强客户证件校验',"
        + "'增加证件有效期和黑名单联动校验','客户访谈','P1','DRAFT',4300,4305)");
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,"
        + "coverage_type,source,created_by) values (4304,4301,'PARTIAL','MANUAL',4300)");

    when(documents.readLink(4305L, 4300L)).thenReturn(document(
        4305L, "需求调研报告", "# 需求调研报告\n\n## 详细需求\n增加证件有效期和黑名单联动校验。"));
    when(documents.readLink(4306L, 4300L)).thenReturn(document(
        4306L, "客户校验 Spec", "# 客户校验 Spec\n\n当前仅校验证件格式，不校验有效期。"));
  }

  @Test
  void analyzesReportFullCatalogCoverageAndRelevantSpecsInTwoStages() {
    ObjectNode candidates = json.createObjectNode();
    candidates.putArray("featureIds").add(4301);
    ObjectNode result = completeResult();
    when(ai.completeJson(eq(4300L), anyString(), anyString(), any()))
        .thenReturn(candidates, result);

    JsonNode analyzed = classifications.analyze(4304L);

    assertEquals(result, analyzed);
    ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
    verify(ai, times(2)).completeJson(eq(4300L), anyString(), prompts.capture(), any());
    List<String> values = prompts.getAllValues();
    assertTrue(values.get(0).contains("客户校验"));
    assertTrue(values.get(0).contains("客户查重"));
    assertTrue(values.get(0).contains("# 需求调研报告"));
    assertTrue(values.get(1).contains("# 需求调研报告"));
    assertTrue(values.get(1).contains("# 客户校验 Spec"));
    assertTrue(values.get(1).contains("PARTIAL"));
    assertTrue(values.get(1).contains("项目计划开始：2026-08-01"));
    assertTrue(values.get(1).contains("项目计划结束：2026-10-31"));
  }

  @Test
  void rejectsIncompleteDeliveryTables() {
    ObjectNode candidates = json.createObjectNode();
    candidates.putArray("featureIds").add(4301);
    ObjectNode invalid = completeResult();
    invalid.putArray("productionPlan");
    when(ai.completeJson(eq(4300L), anyString(), anyString(), any()))
        .thenReturn(candidates, invalid);

    AiServiceException failure = assertThrows(
        AiServiceException.class, () -> classifications.analyze(4304L));

    assertEquals(AiServiceException.Type.INCOMPATIBLE_RESPONSE, failure.getType());
  }

  @Test
  void requiresAFormalRequirementReport() {
    jdbc.update("update requirement_item set outline_link_id=null where id=4304");

    ConflictException failure = assertThrows(
        ConflictException.class, () -> classifications.analyze(4304L));

    assertEquals("请先生成完整需求调研报告后再进行 AI 分类", failure.getMessage());
  }

  private ObjectNode completeResult() {
    ObjectNode result = json.createObjectNode();
    result.put("level", "L1"); result.put("confidence", 0.93);
    result.put("reason", "现有功能仅部分覆盖，需要增强开发");
    result.putArray("evidence").add("需求调研报告/详细需求").add("客户校验 Spec/当前能力");
    result.putArray("warnings").add("生产窗口待确认");
    ObjectNode row = result.putArray("constructionContents").addObject();
    row.put("moduleName", "客户管理"); row.put("featureCode", "VALIDATION");
    row.put("featureName", "客户校验"); row.put("versionAvailability", "INCLUDED");
    row.put("currentCapability", "校验证件格式"); row.put("gap", "缺少有效期和黑名单联动");
    row.put("changeType", "ENHANCEMENT"); row.put("constructionContent", "增强开发");
    row.put("acceptanceCriteria", "无效证件和黑名单客户被准确拦截并留痕");
    row.put("priority", "P1"); row.put("evidence", "客户校验 Spec/当前能力");
    ObjectNode phase = result.putArray("productionPlan").addObject();
    phase.put("phase", "开发与验证"); phase.put("workItem", "完成校验增强和回归测试");
    phase.put("ownerRole", "研发、测试、实施"); phase.put("plannedStart", "2026-08-01");
    phase.put("plannedEnd", "2026-10-31"); phase.put("deliverable", "发布包和测试报告");
    phase.put("entryCriteria", "方案评审通过"); phase.put("exitCriteria", "验收标准全部通过");
    phase.put("riskAndRollback", "灰度发布并验证回退");
    return result;
  }

  private DocumentView document(long id, String title, String markdown) {
    return new DocumentView(id, title, markdown, 1L, Instant.now(), "READY", null,
        "http://outline/doc/" + id);
  }
}
