package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.knowledge.KnowledgeService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpportunityDocumentDefinition {
  private static final Map<String, OpportunityDocumentDefinition> DEFINITIONS = definitions();

  private final String artifactType;
  private final String displayName;
  private final OpportunityStage stage;
  private final String templateScene;
  private final boolean aiGenerated;
  private final boolean advanceOnSubmit;

  private OpportunityDocumentDefinition(
      String artifactType, String displayName, OpportunityStage stage, String templateScene,
      boolean aiGenerated, boolean advanceOnSubmit) {
    this.artifactType = artifactType;
    this.displayName = displayName;
    this.stage = stage;
    this.templateScene = templateScene;
    this.aiGenerated = aiGenerated;
    this.advanceOnSubmit = advanceOnSubmit;
  }

  public static OpportunityDocumentDefinition forType(String artifactType) {
    OpportunityDocumentDefinition value = DEFINITIONS.get(artifactType);
    if (value == null) throw new IllegalArgumentException("当前产出物不支持模版填写");
    return value;
  }

  public static Map<String, OpportunityDocumentDefinition> all() {
    return DEFINITIONS;
  }

  public String getArtifactType() { return artifactType; }
  public String getDisplayName() { return displayName; }
  public OpportunityStage getStage() { return stage; }
  public String getTemplateScene() { return templateScene; }
  public boolean isAiGenerated() { return aiGenerated; }
  public boolean isAdvanceOnSubmit() { return advanceOnSubmit; }

  private static Map<String, OpportunityDocumentDefinition> definitions() {
    Map<String, OpportunityDocumentDefinition> values =
        new LinkedHashMap<String, OpportunityDocumentDefinition>();
    add(values, "RESEARCH_REPORT", "需求调研报告", OpportunityStage.LEAD,
        KnowledgeService.OPPORTUNITY_RESEARCH, false, true);
    add(values, "DECISION_MINUTES", "决策评审纪要", OpportunityStage.OPPORTUNITY,
        KnowledgeService.OPPORTUNITY_DECISION, false, true);
    add(values, "CLIENT_REQUESTS", "甲方诉求清单", OpportunityStage.POC,
        KnowledgeService.OPPORTUNITY_CLIENT_REQUESTS, true, false);
    add(values, "GAP_ANALYSIS", "差距分析报告", OpportunityStage.POC,
        KnowledgeService.OPPORTUNITY_GAP_ANALYSIS, true, false);
    add(values, "REVIEW_MINUTES", "评审会议纪要", OpportunityStage.CONTRACT,
        KnowledgeService.OPPORTUNITY_REVIEW, false, false);
    return Collections.unmodifiableMap(values);
  }

  private static void add(Map<String, OpportunityDocumentDefinition> values,
      String type, String name, OpportunityStage stage, String scene,
      boolean aiGenerated, boolean advanceOnSubmit) {
    values.put(type, new OpportunityDocumentDefinition(
        type, name, stage, scene, aiGenerated, advanceOnSubmit));
  }
}
