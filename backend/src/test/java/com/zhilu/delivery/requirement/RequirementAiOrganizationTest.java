package com.zhilu.delivery.requirement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
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
  @MockBean private AiClient ai;

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

    ObjectNode generated = json.createObjectNode();
    generated.put("level", "L1");
    generated.put("confidence", 0.9);
    generated.put("reason", "需要二次开发");
    when(ai.completeJson(eq(4100L), anyString(), anyString(), any()))
        .thenReturn(generated);
  }

  @Test
  void classifyUsesTheRequirementsOrganization() {
    requirements.classify(701L, ACTOR_ID);

    verify(ai).completeJson(eq(4100L), anyString(), anyString(), any());
  }
}
