package com.zhilu.delivery.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.common.error.ConflictException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-document;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class RequirementDocumentServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RequirementService requirements;
  @Autowired private RequirementDocumentService requirementDocuments;
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

    jdbc.update("insert into organization(id,name,code) values (920,'智鹿','REQ-DOC')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (920,920,'collector','需求采集人','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (920,920,'XBG','消保合规','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (920,920,'V1.0','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
        + "product_id,product_version_id,manager_user_id,created_by) "
        + "values (920,920,'PRJ-920','消保合规交付','示例银行',920,920,920,920)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) "
        + "values (920,920,'ENGINEER')");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "status,owner_user_id) values (9210,920,'TEMPLATE','需求调研报告模版',"
        + "'需求调研报告','模版正文','PUBLISHED',920)");
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (9210,'OPPORTUNITY_RESEARCH','REQUIRED',true,7,'需求调研报告模版',"
        + "'# {{系统/项目名称}}\\n\\n负责人：{{姓名}}\\n\\n"
        + "{{简要说明当前业务背景、现有工作方式及启动本项目的原因。}}\\n\\n"
        + "日期：{{YYYY-MM-DD}}\\n\\n{{未填写字段}}')");
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
        + "outline_collection_id,title_cache) "
        + "values (9203,920,'REQUIREMENT:TEST:RESEARCH_REPORT','REQUIREMENT_RESEARCH',"
        + "'collection','测试需求调研报告')");

    when(documents.ensureIndex(eq(920L), anyString(), anyString(), eq(null)))
        .thenReturn(9200L);
    when(documents.ensureIndex(eq(920L), eq("PROJECT:920"), anyString(), eq(9200L)))
        .thenReturn(9201L);
    when(documents.ensureIndex(eq(920L), eq("PROJECT:920:REQUIREMENTS"),
        eq("需求文档"), eq(9201L))).thenReturn(9202L);
    when(documents.createDocument(eq(920L), anyString(), eq("REQUIREMENT_RESEARCH"),
        anyString(), anyString(), eq(9202L))).thenReturn(9203L);
    ObjectNode generated = json.createObjectNode();
    generated.put("title", "REQ 智能需求调研报告");
    generated.put("markdown", "# 智能需求调研报告\n\n## 业务背景\n示例银行消保合规交付。\n\n"
        + "## 需求说明\n自动核验客户证件，开户时自动核验证件有效期，校验结果可追溯。\n\n"
        + "## 验收标准\n结果准确且留痕。\n\n## 待确认事项\n待确认。");
    when(ai.completeJson(eq(920L), anyString(), anyString(), any())).thenReturn(generated);
  }

  @Test
  void completingCollectionUsesAiTemplateAndContextThenLinksTheGeneratedReport() {
    Map<String, Object> result = requirements.collect(920L, "自动核验客户证件",
        "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920L);

    assertEquals(Long.valueOf(9203L), result.get("outlineLinkId"));
    Map<String, Object> link = jdbc.queryForMap(
        "select outline_link_id,source_template_id,source_template_revision "
            + "from requirement_item where id=?", result.get("id"));
    assertEquals(9203L, ((Number) link.get("outline_link_id")).longValue());
    assertEquals(9210L, ((Number) link.get("source_template_id")).longValue());
    assertEquals(7L, ((Number) link.get("source_template_revision")).longValue());

    ArgumentCaptor<String> businessKey = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
    verify(documents).createDocument(eq(920L), businessKey.capture(),
        eq("REQUIREMENT_RESEARCH"), title.capture(), markdown.capture(), eq(9202L));
    assertEquals("REQUIREMENT:" + result.get("id") + ":RESEARCH_REPORT",
        businessKey.getValue());
    assertEquals("REQ 智能需求调研报告", title.getValue());
    assertTrue(markdown.getValue().contains("# 智能需求调研报告"));
    assertTrue(markdown.getValue().contains("自动核验客户证件"));
    assertTrue(markdown.getValue().contains("开户时自动核验证件有效期，校验结果可追溯"));
    assertTrue(markdown.getValue().contains("示例银行"));
    assertFalse(markdown.getValue().contains("{{"));

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(ai).completeJson(eq(920L), anyString(), prompt.capture(), any());
    assertTrue(prompt.getValue().contains("需求调研报告模版"));
    assertTrue(prompt.getValue().contains("{{系统/项目名称}}"));
    assertTrue(prompt.getValue().contains("示例银行"));
    assertTrue(prompt.getValue().contains("消保合规"));
    assertTrue(prompt.getValue().contains("V1.0"));
    assertTrue(prompt.getValue().contains("需求采集人"));
  }

  @Test
  void incompatibleAiReportRollsBackTheCollectedRequirement() {
    ObjectNode invalid = json.createObjectNode();
    invalid.put("title", "不完整报告");
    invalid.put("markdown", "# 报告\n\n{{仍未填写}}\n");
    when(ai.completeJson(eq(920L), anyString(), anyString(), any())).thenReturn(invalid);

    AiServiceException failure = assertThrows(AiServiceException.class,
        () -> requirements.collect(920L, "自动核验客户证件",
            "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920L));

    assertEquals(AiServiceException.Type.INCOMPATIBLE_RESPONSE, failure.getType());
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_item where project_id=920", Integer.class));
    verify(documents, never()).createDocument(anyLong(), anyString(), anyString(),
        anyString(), anyString(), anyLong());
  }

  @Test
  void regenerationUpdatesTheExistingOutlineRevisionWithFreshAiContent() {
    Map<String, Object> result = requirements.collect(920L, "自动核验客户证件",
        "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920L);
    ObjectNode regenerated = json.createObjectNode();
    regenerated.put("title", "更新后的需求调研报告");
    regenerated.put("markdown", "# 更新后的报告\n\n## 验收标准\n全部规则通过并留痕。");
    when(ai.completeJson(eq(920L), anyString(), anyString(), any())).thenReturn(regenerated);
    when(documents.readLink(9203L, 920L)).thenReturn(new DocumentView(
        9203L, "旧报告", "# 旧正文", 4L, Instant.now(), "READY", null, "url"));
    when(documents.updateLink(9203L, 920L, "更新后的需求调研报告",
        "# 更新后的报告\n\n## 验收标准\n全部规则通过并留痕。", 4L))
        .thenReturn(new DocumentView(9203L, "更新后的需求调研报告",
            "# 更新后的报告", 5L, Instant.now(), "READY", null, "url"));

    requirementDocuments.regenerate(((Number) result.get("id")).longValue(), 920L);

    verify(documents).updateLink(9203L, 920L, "更新后的需求调研报告",
        "# 更新后的报告\n\n## 验收标准\n全部规则通过并留痕。", 4L);
  }

  @Test
  void collectionFailsClearlyAndRollsBackWhenPublishedTemplateIsMissing() {
    jdbc.update("delete from document_template_config where knowledge_item_id=9210");

    ConflictException failure = assertThrows(ConflictException.class,
        () -> requirements.collect(920L, "自动核验客户证件",
            "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920L));

    assertEquals("请先在知识库发布并启用需求调研报告模版", failure.getMessage());
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_item where project_id=920", Integer.class));
  }

  @Test
  void collectionRejectsAmbiguousPublishedTemplates() {
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "status,owner_user_id) values (9211,920,'TEMPLATE','另一份需求调研报告模版',"
        + "'需求调研报告','模版正文','PUBLISHED',920)");
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (9211,'OPPORTUNITY_RESEARCH','REQUIRED',true,2,'另一份需求调研报告模版',"
        + "'# 另一份模版')");

    ConflictException failure = assertThrows(ConflictException.class,
        () -> requirements.collect(920L, "自动核验客户证件",
            "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920L));

    assertEquals("需求调研报告模版只能启用一个", failure.getMessage());
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from requirement_item where project_id=920", Integer.class));
  }
}
