package com.zhilu.delivery.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.zhilu.delivery.automation.AiClient;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class ClassificationServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RequirementService requirements;
  @Autowired private ObjectMapper json;
  @MockBean private AiClient ai;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"custom_dev_task","duplicate_relation","classification_decision","classification_suggestion","requirement_item","delivery_project","product_version","product","app_user","organization"})
      jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (900,'智鹿','ZHILU-REQ')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (900,900,'engineer','工程师','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (900,900,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (900,900,'V1','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (900,900,'PRJ-900','需求项目','客户',900,900,900,900)");
  }

  @Test
  void aiSuggestionDoesNotChangeFunnelUntilHumanConfirms() {
    Map<String, Object> requirement = requirements.create(900, "对账差异自动定位与重跑", "需要定位批次差异并支持安全重跑", "访谈", "P1", 900);
    long id = ((Number) requirement.get("id")).longValue();
    requirements.saveSuggestion(id, "L1", 0.91, "标品缺少批次重跑扩展点", "AI");

    assertEquals(0L, ((Number) requirements.funnel(900).get("L1")).longValue());
    requirements.confirm(id, "L1", null, 900);
    assertEquals(1L, ((Number) requirements.funnel(900).get("L1")).longValue());
    assertEquals(1, jdbc.queryForObject("select count(*) from custom_dev_task where requirement_id=?", Integer.class, id));
  }

  @Test
  void completeAiSuggestionRoundTripsEvidenceAndDeliveryTables() {
    Map<String, Object> requirement = requirements.create(900, "客户校验增强",
        "需要增强客户校验并安排投产", "访谈", "P1", 900);
    long id = ((Number) requirement.get("id")).longValue();
    ObjectNode details = json.createObjectNode();
    details.put("level", "L1"); details.put("confidence", 0.91);
    details.put("reason", "现有客户校验只覆盖基础规则");
    details.putArray("evidence").add("需求调研报告/详细需求");
    details.putArray("warnings").add("生产窗口待确认");
    ObjectNode construction = details.putArray("constructionContents").addObject();
    construction.put("moduleName", "客户管理"); construction.put("featureCode", "VALIDATION");
    construction.put("featureName", "客户校验");
    ObjectNode plan = details.putArray("productionPlan").addObject();
    plan.put("phase", "开发"); plan.put("workItem", "增强客户校验");

    Map<String, Object> saved = requirements.saveSuggestion(
        id, "L1", 0.91, "现有客户校验只覆盖基础规则", "AI", details);

    assertEquals("需求调研报告/详细需求",
        ((List<?>) saved.get("classificationEvidence")).get(0));
    assertEquals("生产窗口待确认",
        ((List<?>) saved.get("classificationWarnings")).get(0));
    assertEquals("客户校验", ((Map<?, ?>) ((List<?>)
        saved.get("constructionContents")).get(0)).get("featureName"));
    assertEquals("开发", ((Map<?, ?>) ((List<?>)
        saved.get("productionPlan")).get(0)).get("phase"));
  }

  @Test
  void overrideRequiresReasonAndMergeKeepsSourceTrace() {
    long source = ((Number) requirements.create(900, "批量导入客户档案数据", "按模板导入并校验客户档案", "调研", "P2", 900).get("id")).longValue();
    long target = ((Number) requirements.create(900, "客户档案批量导入", "需要批量导入客户主数据并校验", "访谈", "P2", 900).get("id")).longValue();
    requirements.saveSuggestion(source, "L0", 0.88, "已有导入能力", "AI");
    assertThrows(IllegalArgumentException.class, () -> requirements.confirm(source, "L2", "", 900));
    requirements.confirm(source, "L2", "客户模板字段超出标品范围", 900);
    requirements.merge(source, target, 900);

    assertNotNull(jdbc.queryForObject("select merged_into_id from requirement_item where id=?", Long.class, source));
    assertEquals(1, jdbc.queryForObject("select count(*) from duplicate_relation where source_requirement_id=? and target_requirement_id=? and status='MERGED'", Integer.class, source, target));
  }
}
