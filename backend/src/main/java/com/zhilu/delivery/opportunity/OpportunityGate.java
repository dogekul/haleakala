package com.zhilu.delivery.opportunity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OpportunityGate {
  private static final Map<OpportunityStage, List<String>> REQUIRED = required();
  private static final Map<String, String> LABELS = labels();
  private static final List<String> FILE_TYPES = Arrays.asList(
      "PRESENTATION", "BID_DOCUMENT", "AWARD_NOTICE", "CONTRACT",
      "EMAIL_ARCHIVE", "SEALED_CONTRACT");
  private static final Set<String> TEMPLATE_DOCUMENT_TYPES =
      Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
          "RESEARCH_REPORT", "DECISION_MINUTES", "CLIENT_REQUESTS",
          "GAP_ANALYSIS", "REVIEW_MINUTES")));
  private final JdbcTemplate jdbc;

  public OpportunityGate(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> missingArtifacts(
      long opportunityId, OpportunityStage stage, String decision) {
    if ("REJECT".equals(decision)
        && (stage == OpportunityStage.OPPORTUNITY
            || stage == OpportunityStage.BIDDING || stage == OpportunityStage.CONTRACT)) {
      return Collections.emptyList();
    }
    List<String> missing = new ArrayList<String>();
    for (String type : REQUIRED.get(stage)) {
      String linkedReport = isTemplateDocument(type)
          ? " and outline_link_id is not null" : "";
      Integer count = jdbc.queryForObject(
          "select count(*) from opportunity_artifact "
              + "where opportunity_id=? and stage_from=? and artifact_type=?" + linkedReport,
          Integer.class, opportunityId, stage.name(), type);
      if (count == null || count == 0) missing.add(LABELS.get(type));
    }
    return missing;
  }

  public void validateArtifact(
      OpportunityStage stage, String type, String contentMarkdown, Long fileId) {
    if (!LABELS.containsKey(type) || !REQUIRED.get(stage).contains(type)) {
      throw new IllegalArgumentException("当前阶段不支持该产出物");
    }
    if (FILE_TYPES.contains(type)) {
      if (fileId == null) throw new IllegalArgumentException("文件类产出物必须选择文件");
      return;
    }
    if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
      throw new IllegalArgumentException("报告类产出物必须填写正文");
    }
  }

  public boolean isFileType(String type) {
    return FILE_TYPES.contains(type);
  }

  public boolean isTemplateDocument(String type) {
    return TEMPLATE_DOCUMENT_TYPES.contains(type);
  }

  private static Map<OpportunityStage, List<String>> required() {
    Map<OpportunityStage, List<String>> values =
        new EnumMap<OpportunityStage, List<String>>(OpportunityStage.class);
    values.put(OpportunityStage.LEAD, Arrays.asList("RESEARCH_REPORT"));
    values.put(OpportunityStage.OPPORTUNITY, Arrays.asList("DECISION_MINUTES"));
    values.put(OpportunityStage.POC, Arrays.asList(
        "PRESENTATION", "CLIENT_REQUESTS", "POC_SCORE", "GAP_ANALYSIS"));
    values.put(OpportunityStage.BIDDING, Arrays.asList("BID_DOCUMENT"));
    values.put(OpportunityStage.CONTRACT, Arrays.asList(
        "AWARD_NOTICE", "CONTRACT", "REVIEW_MINUTES", "EMAIL_ARCHIVE", "SEALED_CONTRACT"));
    return Collections.unmodifiableMap(values);
  }

  private static Map<String, String> labels() {
    Map<String, String> values = new HashMap<String, String>();
    values.put("RESEARCH_REPORT", "商机调研报告");
    values.put("DECISION_MINUTES", "决策评审纪要");
    values.put("PRESENTATION", "讲解材料");
    values.put("CLIENT_REQUESTS", "甲方诉求清单");
    values.put("POC_SCORE", "POC 得分表");
    values.put("GAP_ANALYSIS", "差距分析报告");
    values.put("BID_DOCUMENT", "投标文件");
    values.put("AWARD_NOTICE", "中标公示");
    values.put("CONTRACT", "合同");
    values.put("REVIEW_MINUTES", "评审会议纪要");
    values.put("EMAIL_ARCHIVE", "邮件归档");
    values.put("SEALED_CONTRACT", "已盖章合同");
    return Collections.unmodifiableMap(values);
  }
}
