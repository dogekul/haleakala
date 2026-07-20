package com.zhilu.delivery.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-ai-organization;"
        + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
class RequirementAiOrganizationTest {
  private static final long ACTOR_ID = 4101L;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RequirementService requirements;
  @Autowired private ObjectMapper json;
  @MockBean private RequirementClassificationAiService classifications;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"classification_suggestion", "requirement_item",
        "delivery_project", "product_version", "product", "app_user", "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (4100,'AI 组织','AI-ORG')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (4101,4100,'actor','操作人','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (4100,4100,'AI-PRODUCT','AI 产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (4100,4100,'V1','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) values "
        + "(4100,4100,'AI-PROJECT','AI 项目','客户',4100,4100,4101,4101)");
    jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,"
        + "title,description,source,priority,status,created_by) values "
        + "(701,4100,4100,'REQ-701','智能分类','需要基于组织配置进行分类','调研','P1','DRAFT',4101)");

    when(classifications.analyze(701L)).thenReturn(completeResult());
  }

  @Test
  void classifyPersistsCompleteEvidenceAndDeliveryTables() {
    Map<String, Object> result = requirements.classify(701L, ACTOR_ID);

    verify(classifications).analyze(701L);
    assertEquals("L1", result.get("suggestedLevel"));
    assertEquals("客户校验 Spec/当前能力",
        ((List<?>) result.get("classificationEvidence")).get(0));
    assertEquals("ENHANCEMENT",
        ((Map<?, ?>) ((List<?>) result.get("constructionContents")).get(0)).get("changeType"));
    assertEquals("开发与验证",
        ((Map<?, ?>) ((List<?>) result.get("productionPlan")).get(0)).get("phase"));
  }

  private ObjectNode completeResult() {
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
}
