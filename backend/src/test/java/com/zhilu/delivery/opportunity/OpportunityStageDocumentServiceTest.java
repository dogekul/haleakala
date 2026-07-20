package com.zhilu.delivery.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.knowledge.KnowledgeService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class OpportunityStageDocumentServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final DocumentCenterService documents = mock(DocumentCenterService.class);
  private final OpportunityService opportunities = mock(OpportunityService.class);
  private final AiClient ai = mock(AiClient.class);
  private final ObjectMapper json = new ObjectMapper();
  private final OpportunityStageDocumentService service =
      new OpportunityStageDocumentService(jdbc, documents, opportunities, ai, json);

  @Test
  void exposesOnlyTheFiveApprovedDefinitions() {
    assertDefinition("RESEARCH_REPORT", OpportunityStage.LEAD,
        KnowledgeService.OPPORTUNITY_RESEARCH, false, true);
    assertDefinition("DECISION_MINUTES", OpportunityStage.OPPORTUNITY,
        KnowledgeService.OPPORTUNITY_DECISION, false, true);
    assertDefinition("CLIENT_REQUESTS", OpportunityStage.POC,
        KnowledgeService.OPPORTUNITY_CLIENT_REQUESTS, true, false);
    assertDefinition("GAP_ANALYSIS", OpportunityStage.POC,
        KnowledgeService.OPPORTUNITY_GAP_ANALYSIS, true, false);
    assertDefinition("REVIEW_MINUTES", OpportunityStage.CONTRACT,
        KnowledgeService.OPPORTUNITY_REVIEW, false, false);
    assertThrows(IllegalArgumentException.class,
        () -> OpportunityDocumentDefinition.forType("POC_SCORE"));
  }

  @Test
  void preparesTemplateThenGeneratesClientRequestsFromResearchAndSelectedSpecs() {
    Map<String, Object> opportunity = opportunity("POC", 3L);
    opportunity.put("productId", 41L);
    opportunity.put("productVersionId", 42L);
    when(opportunities.get(3100L, 9L)).thenReturn(opportunity);
    when(jdbc.queryForList(anyString(), anyLong(), anyString())).thenReturn(template());
    when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("opportunity_artifact")) {
        return Collections.singletonList(row("outline_link_id", 71L));
      }
      return features();
    });
    when(documents.ensureIndex(anyLong(), anyString(), anyString(), any()))
        .thenReturn(51L, 52L);
    when(documents.createDocument(anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyLong())).thenReturn(53L);
    when(documents.readBusinessDocument(3100L, "OPPORTUNITY:9:CLIENT_REQUESTS"))
        .thenReturn(view(53L, "甲方诉求清单", "# 模版", 1));
    when(documents.readLink(71L, 3100L))
        .thenReturn(view(71L, "需求调研报告", "需求调研报告正文", 4));
    when(documents.readLink(81L, 3100L))
        .thenReturn(view(81L, "功能 A Spec", "功能 A Spec 正文", 2));
    ObjectNode generated = json.createObjectNode();
    generated.put("title", "甲方诉求清单");
    generated.put("markdown", "# 已生成诉求");
    when(ai.completeJson(eq(3100L), anyString(), anyString(), any())).thenReturn(generated);
    when(documents.updateBusinessDocument(eq(3100L), eq("OPPORTUNITY:9:CLIENT_REQUESTS"),
        eq("甲方诉求清单"), eq("# 已生成诉求"), eq(1L)))
        .thenReturn(view(53L, "甲方诉求清单", "# 已生成诉求", 2));

    OpportunityStageDocumentService.PreparedDocument prepared =
        service.prepare(3100L, 9L, "CLIENT_REQUESTS", 3L);

    assertEquals("AI", prepared.getGenerationStatus());
    assertEquals("# 已生成诉求", prepared.getDocument().getMarkdown());
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(ai).completeJson(eq(3100L), anyString(), prompt.capture(), any());
    assertTrue(prompt.getValue().contains("需求调研报告正文"));
    assertTrue(prompt.getValue().contains("功能 A"));
    assertTrue(prompt.getValue().contains("INCLUDED"));
    assertTrue(prompt.getValue().contains("功能 A Spec 正文"));
  }

  @Test
  void aiFailureKeepsEditableTemplateAndWarnings() {
    Map<String, Object> opportunity = opportunity("POC", 3L);
    when(opportunities.get(3100L, 9L)).thenReturn(opportunity);
    when(jdbc.queryForList(anyString(), anyLong(), anyString())).thenReturn(template());
    when(jdbc.queryForList(anyString(), anyLong(), anyLong()))
        .thenReturn(Collections.<Map<String, Object>>emptyList());
    when(documents.ensureIndex(anyLong(), anyString(), anyString(), any()))
        .thenReturn(51L, 52L);
    when(documents.createDocument(anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyLong())).thenReturn(53L);
    when(documents.readBusinessDocument(3100L, "OPPORTUNITY:9:GAP_ANALYSIS"))
        .thenReturn(view(53L, "差距分析报告", "# 模版", 1));
    when(ai.completeJson(eq(3100L), anyString(), anyString(), any()))
        .thenThrow(new IllegalStateException("AI unavailable"));

    OpportunityStageDocumentService.PreparedDocument prepared =
        service.prepare(3100L, 9L, "GAP_ANALYSIS", 3L);

    assertEquals("FAILED", prepared.getGenerationStatus());
    assertEquals("# 模版", prepared.getDocument().getMarkdown());
    assertTrue(prepared.getGenerationError().contains("AI unavailable"));
    assertFalse(prepared.getWarnings().isEmpty());
  }

  @Test
  void generatedDocumentRejectsExtraResponseFields() {
    ObjectNode generated = json.createObjectNode();
    generated.put("title", "差距分析报告");
    generated.put("markdown", "# 已生成差距");
    generated.put("ignored", true);

    OpportunityStageDocumentService.PreparedDocument prepared =
        prepareGeneratedDocument(generated);

    assertEquals("FAILED", prepared.getGenerationStatus());
    assertEquals(new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE).getMessage(),
        prepared.getGenerationError());
    verify(documents, never()).updateBusinessDocument(
        anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void generatedDocumentRejectsWrongScalarTypes() {
    ObjectNode generated = json.createObjectNode();
    generated.put("title", 123);
    generated.put("markdown", "# 已生成差距");

    OpportunityStageDocumentService.PreparedDocument prepared =
        prepareGeneratedDocument(generated);

    assertEquals("FAILED", prepared.getGenerationStatus());
    assertEquals(new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE).getMessage(),
        prepared.getGenerationError());
    verify(documents, never()).updateBusinessDocument(
        anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void repeatedPrepareNeverOverwritesAnEditedDraft() {
    when(opportunities.get(7L, 9L)).thenReturn(opportunity("POC", 3L));
    when(jdbc.queryForList(anyString(), anyLong(), anyString())).thenReturn(template());
    when(documents.ensureIndex(anyLong(), anyString(), anyString(), any()))
        .thenReturn(51L, 52L);
    when(documents.createDocument(anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyLong())).thenReturn(53L);
    when(documents.readBusinessDocument(7L, "OPPORTUNITY:9:CLIENT_REQUESTS"))
        .thenReturn(view(53L, "甲方诉求清单", "# 人工调整", 4));

    OpportunityStageDocumentService.PreparedDocument prepared =
        service.prepare(7L, 9L, "CLIENT_REQUESTS", 3L);

    assertEquals("MANUAL", prepared.getGenerationStatus());
    assertEquals("# 人工调整", prepared.getDocument().getMarkdown());
    verify(ai, never()).completeJson(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void submitRejectsPlaceholdersAndDelegatesLinkedArtifact() {
    Map<String, Object> opportunity = opportunity("CONTRACT", 5L);
    when(opportunities.get(7L, 9L)).thenReturn(opportunity);
    when(jdbc.queryForList(anyString(), anyLong(), anyString())).thenReturn(template());
    assertThrows(IllegalArgumentException.class, () -> service.submit(
        7L, 9L, 11L, "REVIEW_MINUTES", 5L, "评审会议纪要", "# {{结论}}", 1L));

    when(documents.updateBusinessDocument(7L, "OPPORTUNITY:9:REVIEW_MINUTES",
        "评审会议纪要", "# 通过", 1L)).thenReturn(view(53L, "评审会议纪要", "# 通过", 2));
    when(opportunities.submitDocumentArtifact(7L, 9L, 11L, 5L, "REVIEW_MINUTES",
        OpportunityStage.CONTRACT, 53L, 91L, 6L, "评审会议纪要", false))
        .thenReturn(opportunity);

    OpportunityStageDocumentService.SubmitResult result = service.submit(
        7L, 9L, 11L, "REVIEW_MINUTES", 5L, "评审会议纪要", "# 通过", 1L);
    assertEquals("CONTRACT", result.getOpportunity().get("stage"));
    assertEquals(2L, result.getDocument().getRevision());
  }

  private void assertDefinition(String type, OpportunityStage stage, String scene,
      boolean aiGenerated, boolean advanceOnSubmit) {
    OpportunityDocumentDefinition value = OpportunityDocumentDefinition.forType(type);
    assertEquals(stage, value.getStage());
    assertEquals(scene, value.getTemplateScene());
    assertEquals(aiGenerated, value.isAiGenerated());
    assertEquals(advanceOnSubmit, value.isAdvanceOnSubmit());
  }

  private OpportunityStageDocumentService.PreparedDocument prepareGeneratedDocument(
      ObjectNode generated) {
    when(opportunities.get(3100L, 9L)).thenReturn(opportunity("POC", 3L));
    when(jdbc.queryForList(anyString(), anyLong(), anyString())).thenReturn(template());
    when(jdbc.queryForList(anyString(), anyLong(), anyLong()))
        .thenReturn(Collections.<Map<String, Object>>emptyList());
    when(documents.ensureIndex(anyLong(), anyString(), anyString(), any()))
        .thenReturn(51L, 52L);
    when(documents.createDocument(anyLong(), anyString(), anyString(), anyString(), anyString(),
        anyLong())).thenReturn(53L);
    when(documents.readBusinessDocument(3100L, "OPPORTUNITY:9:GAP_ANALYSIS"))
        .thenReturn(view(53L, "差距分析报告", "# 模版", 1));
    when(ai.completeJson(eq(3100L), anyString(), anyString(), any())).thenReturn(generated);
    return service.prepare(3100L, 9L, "GAP_ANALYSIS", 3L);
  }

  private Map<String, Object> opportunity(String stage, long version) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", 9L); value.put("customerName", "华东银行");
    value.put("title", "合规系统"); value.put("stage", stage);
    value.put("status", "OPEN"); value.put("version", version);
    value.put("productId", null); value.put("productVersionId", null);
    return value;
  }

  private List<Map<String, Object>> template() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", 91L); value.put("published_revision", 6L);
    value.put("published_title_snapshot", "甲方诉求清单");
    value.put("published_markdown_snapshot", "# 模版");
    return Collections.singletonList(value);
  }

  private List<Map<String, Object>> features() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("feature_id", 61L); value.put("feature_name", "功能 A");
    value.put("feature_code", "A"); value.put("availability", "INCLUDED");
    value.put("spec_outline_link_id", 81L);
    return Collections.singletonList(value);
  }

  private Map<String, Object> row(String key, Object value) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(key, value); return result;
  }

  private DocumentView view(long id, String title, String markdown, long revision) {
    return new DocumentView(id, title, markdown, revision, Instant.now(),
        "READY", null, "http://outline/doc/" + id);
  }
}
