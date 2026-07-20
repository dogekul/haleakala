package com.zhilu.delivery.requirement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-collection-api;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class RequirementCollectionApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @MockBean private DocumentCenterService documents;
  @MockBean private AiClient ai;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"audit_log", "requirement_item",
        "document_template_config", "knowledge_item", "outline_document_link",
        "project_member", "delivery_project", "product_version", "product",
        "app_user", "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

    jdbc.update("insert into organization(id,name,code) values (930,'智鹿','REQ-COLLECT')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (930,930,'writer','需求工程师','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (931,930,'outsider','非项目成员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (930,930,'XBG','消保合规','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (930,930,'V1.0','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (930,930,'PRJ-930','消保合规交付','示例银行',930,930,930,930)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) "
        + "values (930,930,'ENGINEER')");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "status,owner_user_id) values (9310,930,'TEMPLATE','需求调研报告模版',"
        + "'需求调研报告','模版正文','PUBLISHED',930)");
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (9310,'OPPORTUNITY_RESEARCH','REQUIRED',true,5,'需求调研报告模版',"
        + "'# {{系统/项目名称}}\\n\\n{{未填写字段}}')");
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
        + "outline_collection_id,title_cache) "
        + "values (9303,930,'REQUIREMENT:TEST:RESEARCH_REPORT','REQUIREMENT_RESEARCH',"
        + "'collection','交易限额校验需求调研报告')");

    when(documents.ensureIndex(eq(930L), anyString(), anyString(), eq(null)))
        .thenReturn(9300L);
    when(documents.ensureIndex(eq(930L), eq("PROJECT:930"), anyString(), eq(9300L)))
        .thenReturn(9301L);
    when(documents.ensureIndex(eq(930L), eq("PROJECT:930:REQUIREMENTS"),
        eq("需求文档"), eq(9301L))).thenReturn(9302L);
    when(documents.createDocument(eq(930L), anyString(), eq("REQUIREMENT_RESEARCH"),
        anyString(), anyString(), eq(9302L))).thenReturn(9303L);
    ObjectNode generated = json.createObjectNode();
    generated.put("title", "交易限额校验需求调研报告");
    generated.put("markdown", "# 交易限额校验需求调研报告\n\n## 业务背景\n付款前校验限额。"
        + "\n\n## 验收标准\n校验结果准确并保留审计记录。");
    when(ai.completeJson(eq(930L), anyString(), anyString(), any())).thenReturn(generated);
    when(documents.readLink(9303L, 930L)).thenReturn(new DocumentView(
        9303L, "交易限额校验需求调研报告", "# 正文", 1L, Instant.now(),
        "READY", null, "http://localhost:3000/doc/req-9303"));
  }

  @Test
  void completingCollectionReturnsLinkedDocumentAndAllowsScopedRead() throws Exception {
    mvc.perform(post("/api/v1/requirements").with(writer()).with(csrf())
            .contentType("application/json")
            .content("{\"projectId\":930,\"title\":\"交易限额校验\","
                + "\"description\":\"付款前校验客户交易限额并保留结果\","
                + "\"source\":\"需求调研\",\"priority\":\"P1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.outlineLinkId").value(9303))
        .andExpect(jsonPath("$.sourceTemplateId").value(9310))
        .andExpect(jsonPath("$.sourceTemplateRevision").value(5));

    Long requirementId = jdbc.queryForObject(
        "select id from requirement_item where organization_id=930", Long.class);
    mvc.perform(get("/api/v1/requirements/{id}/document", requirementId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.linkId").value(9303))
        .andExpect(jsonPath("$.outlineUrl")
            .value("http://localhost:3000/doc/req-9303"));
    mvc.perform(get("/api/v1/requirements/{id}/document", requirementId).with(outsider()))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor writer() {
    return authentication(token(930L, "writer", "需求工程师", "requirement:write"));
  }

  private RequestPostProcessor reader() {
    return authentication(token(930L, "writer", "需求工程师", "requirement:read"));
  }

  private RequestPostProcessor outsider() {
    return authentication(token(931L, "outsider", "非项目成员", "requirement:read"));
  }

  private UsernamePasswordAuthenticationToken token(
      long userId, String username, String displayName, String permission) {
    CurrentUser user = new CurrentUser(userId, 930L, username, displayName,
        Collections.singletonList("DELIVERY_ENGINEER"), Arrays.asList(permission));
    return new UsernamePasswordAuthenticationToken(user, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission)));
  }
}
