package com.zhilu.delivery.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:opportunity-research;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class OpportunityResearchReportServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OpportunityResearchReportService reports;
  @MockBean private DocumentCenterService documents;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"opportunity_artifact","sales_opportunity",
        "document_template_config","knowledge_item","outline_document_link","customer",
        "app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (910,'智鹿','RESEARCH')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (910,910,'owner','负责人','ACTIVE')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (910,910,'华东银行','ACTIVE')");
    jdbc.update("insert into sales_opportunity(id,organization_id,customer_id,"
        + "customer_name_snapshot,title,stage,status,amount,created_by) "
        + "values (910,910,910,'华东银行','合规审查系统','LEAD','OPEN',0,910)");
    jdbc.update("insert into outline_document_link(id,organization_id,business_key,purpose,"
        + "outline_collection_id,outline_document_id,title_cache,revision,sync_status) "
        + "values (919,910,'OPPORTUNITY:910:RESEARCH_REPORT','OPPORTUNITY_RESEARCH',"
        + "'collection','report-document','需求调研报告',1,'READY')");
    publishTemplate(911, 4);
    DocumentView prepared = view(919, "需求调研报告", "# {{调研结论}}", 1);
    when(documents.ensureIndex(anyLong(), anyString(), anyString(),
        org.mockito.ArgumentMatchers.<Long>nullable(Long.class))).thenReturn(917L, 918L);
    when(documents.createDocument(anyLong(), anyString(), anyString(), anyString(),
        anyString(), anyLong())).thenReturn(919L);
    when(documents.readBusinessDocument(910, "OPPORTUNITY:910:RESEARCH_REPORT"))
        .thenReturn(prepared);
    when(documents.updateBusinessDocument(anyLong(), anyString(), anyString(), anyString(),
        anyLong())).thenAnswer(invocation -> view(919, invocation.getArgument(2),
            invocation.getArgument(3), ((Long) invocation.getArgument(4)) + 1));
  }

  @Test void preparesPublishedTemplateIdempotently() {
    OpportunityResearchReportService.PreparedReport first = reports.prepare(910, 910, 0);
    OpportunityResearchReportService.PreparedReport second = reports.prepare(910, 910, 0);

    assertEquals(911, first.getSourceTemplateId());
    assertEquals(4, first.getSourceTemplateRevision());
    assertEquals(919, first.getDocument().getLinkId());
    assertEquals(first.getDocument().getLinkId(), second.getDocument().getLinkId());
  }

  @Test void requiresExactlyOnePublishedTemplate() {
    jdbc.update("delete from document_template_config");
    assertThrows(ConflictException.class, () -> reports.prepare(910, 910, 0));
    publishTemplate(912, 2);
    publishTemplate(913, 3);
    assertThrows(ConflictException.class, () -> reports.prepare(910, 910, 0));
  }

  @Test void submitsOutlineLinkedArtifactAndAdvancesOpportunity() {
    Map<String,Object> advanced = reports.submit(
        910, 910, 910, 0, "合规审查系统需求调研报告", "# 调研结论\n需求明确", 1);

    assertEquals("OPPORTUNITY", advanced.get("stage"));
    Map<String,Object> artifact = jdbc.queryForMap(
        "select outline_link_id,source_template_id,source_template_revision,content_markdown "
            + "from opportunity_artifact where opportunity_id=910");
    assertEquals(919, ((Number) artifact.get("outline_link_id")).longValue());
    assertEquals(911, ((Number) artifact.get("source_template_id")).longValue());
    assertEquals(4, ((Number) artifact.get("source_template_revision")).longValue());
    assertNull(artifact.get("content_markdown"));
  }

  @Test void rejectsUnresolvedPlaceholdersWithoutAdvancing() {
    assertThrows(IllegalArgumentException.class, () -> reports.submit(
        910, 910, 910, 0, "需求调研报告", "# {{调研结论}}", 1));
    assertEquals("LEAD", jdbc.queryForObject(
        "select stage from sales_opportunity where id=910", String.class));
  }

  private void publishTemplate(long id, long revision) {
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "visibility,status,owner_user_id) values (?,910,'TEMPLATE','需求调研报告','商机调研',"
        + "'# {{调研结论}}','ORGANIZATION','PUBLISHED',910)", id);
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (?,'OPPORTUNITY_RESEARCH','REQUIRED',true,?,'需求调研报告',"
        + "'# {{调研结论}}')", id, revision);
  }

  private DocumentView view(long linkId, String title, String markdown, long revision) {
    return new DocumentView(linkId, title, markdown, revision, Instant.now(),
        "READY", null, "http://outline/doc/report-document");
  }
}
