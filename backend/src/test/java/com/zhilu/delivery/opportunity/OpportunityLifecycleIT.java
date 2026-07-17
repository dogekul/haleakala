package com.zhilu.delivery.opportunity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:opportunity-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class OpportunityLifecycleIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void seed() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from customer_operation");
    jdbc.update("delete from opportunity_artifact");
    jdbc.update("delete from opportunity_activity");
    jdbc.update("delete from sales_opportunity");
    jdbc.update("delete from outline_document_link");
    jdbc.update("delete from file_object");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from customer");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (500,'智鹿科技','LIFE-A')");
    jdbc.update("insert into organization(id,name,code) values (501,'其他组织','LIFE-B')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (500,500,'crm-owner','CRM 负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (501,501,'other-owner','其他负责人','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (500,500,'华东银行','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) values (501,501,'其他客户','ACTIVE')");
    jdbc.update("insert into file_object(id,organization_id,object_key,original_name,mime_type,"
        + "size_bytes,checksum_sha256,created_by) values "
        + "(500,500,'crm/500/presentation.pdf','讲解材料.pdf','application/pdf',100,'"
        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',500)");
    jdbc.update("insert into file_object(id,organization_id,object_key,original_name,mime_type,"
        + "size_bytes,checksum_sha256,created_by) values "
        + "(501,501,'crm/501/other.pdf','其他文件.pdf','application/pdf',100,'"
        + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',501)");
  }

  @Test
  void advancesAllPresaleStagesOnlyAfterRequiredArtifactsAndDecisions() throws Exception {
    long id = opportunity(500, 500, "完整售前流程", "LEAD");

    advance(id, 0, null).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("缺少必需产出物：商机调研报告"));
    addReport(id, "RESEARCH_REPORT", "调研报告", "")
        .andExpect(status().isBadRequest());
    addReport(id, "RESEARCH_REPORT", "调研报告", "## 结论\n需求明确")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("模版文档请通过商机推进材料填写并提交"));
    addLinkedResearchReport(id);
    advance(id, 0, null).andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("OPPORTUNITY"))
        .andExpect(jsonPath("$.version").value(1));

    addReport(id, "DECISION_MINUTES", "评审纪要", "同意进入 POC")
        .andExpect(status().isBadRequest());
    addLinkedDocument(id, "OPPORTUNITY", "DECISION_MINUTES", "评审纪要");
    advance(id, 1, null).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("当前阶段必须选择 PASS 或 REJECT"));
    advance(id, 1, "PASS").andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("POC"));

    addFile(id, "PRESENTATION", "讲解材料", 500).andExpect(status().isCreated());
    addReport(id, "CLIENT_REQUESTS", "甲方诉求清单", "统一账户体系").andExpect(status().isBadRequest());
    addLinkedDocument(id, "POC", "CLIENT_REQUESTS", "甲方诉求清单");
    addReport(id, "POC_SCORE", "POC 得分表", "总分 92").andExpect(status().isCreated());
    addReport(id, "GAP_ANALYSIS", "差距分析报告", "无阻断差距").andExpect(status().isBadRequest());
    addLinkedDocument(id, "POC", "GAP_ANALYSIS", "差距分析报告");
    advance(id, 2, null).andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("BIDDING"));

    addFile(id, "BID_DOCUMENT", "投标文件", 500).andExpect(status().isCreated());
    advance(id, 3, "PASS").andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("CONTRACT"));

    addFile(id, "AWARD_NOTICE", "中标公示", 500).andExpect(status().isCreated());
    addFile(id, "CONTRACT", "合同", 500).andExpect(status().isCreated());
    addReport(id, "REVIEW_MINUTES", "评审会议纪要", "评审通过").andExpect(status().isBadRequest());
    addLinkedDocument(id, "CONTRACT", "REVIEW_MINUTES", "评审会议纪要");
    addFile(id, "EMAIL_ARCHIVE", "邮件归档", 500).andExpect(status().isCreated());
    addFile(id, "SEALED_CONTRACT", "已盖章合同", 500).andExpect(status().isCreated());
    advance(id, 4, "PASS").andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("合同通过请使用转交实施操作"));
    mvc.perform(get("/api/v1/opportunities/{id}/artifacts", id).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(12))
        .andExpect(jsonPath("$[2].fileName").value("讲解材料.pdf"));
  }

  @Test
  void rejectsGateStagesAndKeepsWonOrLostOpportunitiesTerminal() throws Exception {
    long rejected = opportunity(500, 500, "评审丢单", "OPPORTUNITY");
    addReport(rejected, "DECISION_MINUTES", "评审纪要", "客户暂停预算")
        .andExpect(status().isBadRequest());
    advance(rejected, 0, "REJECT").andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOST"));
    advance(rejected, 1, "PASS").andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("终态商机不能继续推进"));

    long bidRejected = opportunity(500, 500, "投标丢单", "BIDDING");
    advance(bidRejected, 0, "REJECT").andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOST"));
    long contractRejected = opportunity(500, 500, "合同丢单", "CONTRACT");
    advance(contractRejected, 0, "REJECT").andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOST"));
  }

  @Test
  void addsAndTogglesCurrentStageActivitiesWithOptimisticLocking() throws Exception {
    long id = opportunity(500, 500, "活动协同", "LEAD");
    String body = mvc.perform(post("/api/v1/opportunities/{id}/activities", id)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"访谈财务负责人\",\"sortOrder\":10}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stageCode").value("LEAD"))
        .andExpect(jsonPath("$.status").value("TODO"))
        .andReturn().getResponse().getContentAsString();
    long activityId = json.readTree(body).get("id").asLong();

    mvc.perform(put("/api/v1/opportunities/{id}/activities/{activityId}", id, activityId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"DONE\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt").exists())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(put("/api/v1/opportunities/{id}/activities/{activityId}", id, activityId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"TODO\",\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(put("/api/v1/opportunities/{id}/activities/{activityId}", id, activityId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"TODO\",\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt").doesNotExist());
    mvc.perform(get("/api/v1/opportunities/{id}/activities", id).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("访谈财务负责人"));
  }

  @Test
  void rejectsCrossOrganizationFilesAndOpportunities() throws Exception {
    long id = opportunity(500, 500, "文件隔离", "POC");
    addFile(id, "PRESENTATION", "越权文件", 501).andExpect(status().isNotFound());
    long other = opportunity(501, 501, "其他组织商机", "LEAD");
    mvc.perform(get("/api/v1/opportunities/{id}/activities", other).with(reader()))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/opportunities/{id}/artifacts", other)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"artifactType\":\"RESEARCH_REPORT\",\"title\":\"越权\","
                + "\"contentMarkdown\":\"内容\"}"))
        .andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions addReport(
      long id, String type, String title, String content) throws Exception {
    return mvc.perform(post("/api/v1/opportunities/{id}/artifacts", id)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"artifactType\":\"" + type + "\",\"title\":\"" + title
            + "\",\"contentMarkdown\":" + json.writeValueAsString(content) + "}"));
  }

  private org.springframework.test.web.servlet.ResultActions addFile(
      long id, String type, String title, long fileId) throws Exception {
    return mvc.perform(post("/api/v1/opportunities/{id}/artifacts", id)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"artifactType\":\"" + type + "\",\"title\":\"" + title
            + "\",\"fileId\":" + fileId + "}"));
  }

  private void addLinkedResearchReport(long opportunityId) {
    addLinkedDocument(opportunityId, "LEAD", "RESEARCH_REPORT", "调研报告");
  }

  private void addLinkedDocument(
      long opportunityId, String stage, String artifactType, String title) {
    String businessKey = "OPPORTUNITY:" + opportunityId + ":" + artifactType;
    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
        + "outline_collection_id,outline_document_id,title_cache,revision,sync_status) "
        + "values (500,?,?,'collection',?,"
        + "?,1,'READY')", businessKey, artifactType, "document-" + artifactType, title);
    Long linkId = jdbc.queryForObject(
        "select id from outline_document_link where organization_id=500 and business_key=?",
        Long.class, businessKey);
    jdbc.update("insert into opportunity_artifact(organization_id,opportunity_id,stage_from,"
        + "artifact_type,title,outline_link_id,created_by) "
        + "values (500,?,?,?,?,?,500)", opportunityId, stage, artifactType, title, linkId);
  }

  private org.springframework.test.web.servlet.ResultActions advance(
      long id, long version, String decision) throws Exception {
    String decisionJson = decision == null ? "" : ",\"decision\":\"" + decision + "\"";
    return mvc.perform(post("/api/v1/opportunities/{id}/advance", id)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":" + version + decisionJson + "}"));
  }

  private long opportunity(long organizationId, long customerId, String title, String stage) {
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,stage,amount,created_by) values (?,?,?,?,?,0,?)",
        organizationId, customerId, organizationId == 500 ? "华东银行" : "其他客户", title,
        stage, organizationId);
    return jdbc.queryForObject("select id from sales_opportunity where organization_id=? and title=?",
        Long.class, organizationId, title);
  }

  private RequestPostProcessor reader() { return actor("crm:read"); }
  private RequestPostProcessor writer() { return actor("crm:write"); }
  private RequestPostProcessor actor(String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(500L, 500L, "crm-owner", "CRM 负责人",
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
