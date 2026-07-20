package com.zhilu.delivery.requirement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.automation.AiClient;
import com.zhilu.delivery.automation.AiServiceException;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RequirementClassificationAiService {
  private static final int MAX_AI_CANDIDATES = 12;
  private static final Set<String> LEVELS = set("L0", "L1", "L2");
  private static final Set<String> CHANGE_TYPES = set(
      "CONFIGURATION", "ENHANCEMENT", "NEW_FEATURE", "INTEGRATION", "DATA",
      "NON_FUNCTIONAL", "OUT_OF_SCOPE");
  private static final Set<String> PRIORITIES = set("P0", "P1", "P2", "P3");
  private static final List<String> CONSTRUCTION_FIELDS = Arrays.asList(
      "moduleName", "featureCode", "featureName", "versionAvailability",
      "currentCapability", "gap", "changeType", "constructionContent",
      "acceptanceCriteria", "priority", "evidence");
  private static final List<String> PLAN_FIELDS = Arrays.asList(
      "phase", "workItem", "ownerRole", "plannedStart", "plannedEnd", "deliverable",
      "entryCriteria", "exitCriteria", "riskAndRollback");

  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;
  private final AiClient ai;
  private final ObjectMapper json;

  public RequirementClassificationAiService(JdbcTemplate jdbc,
      DocumentCenterService documents, AiClient ai, ObjectMapper json) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.ai = ai;
    this.json = json;
  }

  public JsonNode analyze(long requirementId) {
    Map<String, Object> context = context(requirementId);
    long organizationId = number(context, "organization_id");
    if (context.get("outline_link_id") == null) {
      throw new ConflictException("请先生成完整需求调研报告后再进行 AI 分类");
    }
    DocumentView report = documents.readLink(
        number(context, "outline_link_id"), organizationId);
    List<Map<String, Object>> catalog = catalog(context);
    if (catalog.isEmpty()) {
      throw new ConflictException("关联产品版本没有可分析的功能清单");
    }
    Map<Long, String> coverage = coverage(requirementId);
    JsonNode selected = ai.completeJson(organizationId, candidateSystemPrompt(),
        candidatePrompt(context, report, catalog, coverage), candidateSchema());
    List<Map<String, Object>> candidates = candidates(selected, catalog, coverage.keySet());
    List<String> warnings = new ArrayList<String>();
    String specContext = specContext(organizationId, candidates, coverage, warnings);
    JsonNode result = ai.completeJson(organizationId, analysisSystemPrompt(),
        analysisPrompt(context, report, catalog, coverage, specContext, warnings),
        analysisSchema());
    mergeWarnings(result, warnings);
    validate(result);
    return result;
  }

  private Map<String, Object> context(long requirementId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select r.id,r.organization_id,r.requirement_code,r.title,r.description,r.source,"
            + "r.priority,r.outline_link_id,p.id project_id,p.code project_code,"
            + "p.name project_name,p.customer_name,p.product_id,p.product_version_id,"
            + "p.start_date,p.planned_end_date,pr.code product_code,pr.name product_name,"
            + "pv.version_name from requirement_item r "
            + "join delivery_project p on p.id=r.project_id "
            + "join product pr on pr.id=p.product_id and pr.organization_id=r.organization_id "
            + "join product_version pv on pv.id=p.product_version_id and pv.product_id=pr.id "
            + "where r.id=?",
        requirementId);
    if (values.isEmpty()) throw new NotFoundException("需求不存在");
    return values.get(0);
  }

  private List<Map<String, Object>> catalog(Map<String, Object> context) {
    return jdbc.queryForList(
        "select f.id feature_id,m.name module_name,f.code feature_code,f.name feature_name,"
            + "f.description,pvf.availability,"
            + "coalesce(n.outline_link_id,f.outline_link_id) spec_outline_link_id "
            + "from product_version_feature pvf "
            + "join product_feature f on f.id=pvf.product_feature_id "
            + "join product_module m on m.id=f.module_id "
            + "left join product_document_node n on n.product_id=f.product_id "
            + "and n.linked_feature_id=f.id and n.node_type='DOCUMENT' "
            + "where pvf.product_version_id=? and f.product_id=? "
            + "and f.status<>'DEPRECATED' order by m.sort_order,m.id,f.id",
        number(context, "product_version_id"), number(context, "product_id"));
  }

  private Map<Long, String> coverage(long requirementId) {
    Map<Long, String> values = new LinkedHashMap<Long, String>();
    for (Map<String, Object> row : jdbc.queryForList(
        "select product_feature_id,coverage_type from requirement_product_feature "
            + "where requirement_id=? order by product_feature_id", requirementId)) {
      values.put(number(row, "product_feature_id"), text(row.get("coverage_type")));
    }
    return values;
  }

  private List<Map<String, Object>> candidates(JsonNode selected,
      List<Map<String, Object>> catalog, Set<Long> coveredIds) {
    if (selected == null || !selected.isObject() || selected.size() != 1
        || selected.get("featureIds") == null || !selected.get("featureIds").isArray()) {
      throw incompatible();
    }
    Map<Long, Map<String, Object>> known = new LinkedHashMap<Long, Map<String, Object>>();
    for (Map<String, Object> feature : catalog) {
      known.put(number(feature, "feature_id"), feature);
    }
    LinkedHashSet<Long> ids = new LinkedHashSet<Long>();
    for (JsonNode id : selected.get("featureIds")) {
      if (!id.isIntegralNumber()) throw incompatible();
      long value = id.asLong();
      if (known.containsKey(value) && ids.size() < MAX_AI_CANDIDATES) ids.add(value);
    }
    for (Long id : coveredIds) if (known.containsKey(id)) ids.add(id);
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (Long id : ids) values.add(known.get(id));
    return values;
  }

  private String specContext(long organizationId, List<Map<String, Object>> candidates,
      Map<Long, String> coverage, List<String> warnings) {
    StringBuilder value = new StringBuilder();
    if (candidates.isEmpty()) {
      warnings.add("未筛选到相关现有功能，请重点判断新增能力或范围外需求");
      return "（未筛选到相关现有功能）\n";
    }
    for (Map<String, Object> feature : candidates) {
      long featureId = number(feature, "feature_id");
      value.append("\n### ").append(text(feature.get("module_name"))).append(" / ")
          .append(text(feature.get("feature_code"))).append(" · ")
          .append(text(feature.get("feature_name"))).append('\n')
          .append("版本可用性：").append(text(feature.get("availability"))).append('\n')
          .append("人工覆盖：").append(coverage.containsKey(featureId)
              ? coverage.get(featureId) : "未标注").append('\n');
      if (feature.get("spec_outline_link_id") == null) {
        warnings.add("功能“" + text(feature.get("feature_name")) + "”缺少设计 Spec");
        value.append("设计 Spec：缺失\n");
        continue;
      }
      try {
        DocumentView spec = documents.readLink(
            number(feature, "spec_outline_link_id"), organizationId);
        value.append(spec.getMarkdown()).append('\n');
      } catch (RuntimeException unavailable) {
        warnings.add("功能“" + text(feature.get("feature_name")) + "”的设计 Spec 无法读取");
        value.append("设计 Spec：暂时无法读取\n");
      }
    }
    return value.toString();
  }

  private String candidateSystemPrompt() {
    return "你是企业产品功能检索助手。请基于正式需求调研报告，从完整产品版本功能目录中"
        + "选择最相关的功能 ID，最多 12 个。必须考虑功能描述与版本可用性，不得输出目录外 ID。"
        + "只返回符合 JSON Schema 的对象。";
  }

  private String candidatePrompt(Map<String, Object> context, DocumentView report,
      List<Map<String, Object>> catalog, Map<Long, String> coverage) {
    StringBuilder value = new StringBuilder();
    value.append("## 正式需求调研报告\n").append(report.getMarkdown())
        .append("\n\n## 产品版本完整功能目录\n");
    for (Map<String, Object> feature : catalog) {
      long id = number(feature, "feature_id");
      value.append("- ID=").append(id).append(" | ")
          .append(text(feature.get("module_name"))).append(" | ")
          .append(text(feature.get("feature_code"))).append(" | ")
          .append(text(feature.get("feature_name"))).append(" | ")
          .append(text(feature.get("description"))).append(" | availability=")
          .append(text(feature.get("availability")));
      if (coverage.containsKey(id)) value.append(" | manualCoverage=").append(coverage.get(id));
      value.append('\n');
    }
    value.append("\n需求编号：").append(text(context.get("requirement_code")))
        .append("\n产品：").append(text(context.get("product_name")))
        .append(" / ").append(text(context.get("version_name")));
    return value.toString();
  }

  private String analysisSystemPrompt() {
    return "你是资深企业产品经理、解决方案架构师和投产经理。必须严格依据需求调研报告、"
        + "完整功能目录、人工覆盖和相关功能 Spec 给出可追溯结论。L0=当前版本标品或配置可满足；"
        + "L1=需要配置、增强、新功能、集成、数据或非功能建设；L2=不属于当前产品范围。"
        + "必须产出至少一条建设内容和至少一条投产计划；资料不足填写“待确认”，不得虚构。"
        + "计划日期只能使用项目日期推导或填写“待确认”。只返回符合 JSON Schema 的对象。";
  }

  private String analysisPrompt(Map<String, Object> context, DocumentView report,
      List<Map<String, Object>> catalog, Map<Long, String> coverage, String specs,
      List<String> warnings) {
    StringBuilder value = new StringBuilder();
    value.append("## 项目上下文\n")
        .append("项目：").append(text(context.get("project_code"))).append(" · ")
        .append(text(context.get("project_name"))).append('\n')
        .append("客户：").append(text(context.get("customer_name"))).append('\n')
        .append("产品：").append(text(context.get("product_code"))).append(" · ")
        .append(text(context.get("product_name"))).append(" / ")
        .append(text(context.get("version_name"))).append('\n')
        .append("项目计划开始：").append(text(context.get("start_date"))).append('\n')
        .append("项目计划结束：").append(text(context.get("planned_end_date"))).append('\n')
        .append("需求优先级：").append(text(context.get("priority"))).append("\n\n")
        .append("## 正式需求调研报告\n").append(report.getMarkdown()).append("\n\n")
        .append("## 产品版本完整功能目录摘要\n");
    for (Map<String, Object> feature : catalog) {
      value.append("- ").append(text(feature.get("module_name"))).append(" / ")
          .append(text(feature.get("feature_code"))).append(" · ")
          .append(text(feature.get("feature_name"))).append(" [")
          .append(text(feature.get("availability"))).append("]\n");
    }
    value.append("\n## 人工功能覆盖\n");
    if (coverage.isEmpty()) value.append("（未标注）\n");
    else for (Map.Entry<Long, String> entry : coverage.entrySet()) {
      value.append("- featureId=").append(entry.getKey()).append(" coverage=")
          .append(entry.getValue()).append('\n');
    }
    value.append("\n## 候选功能设计 Spec\n").append(specs);
    if (!warnings.isEmpty()) {
      value.append("\n## 系统识别的资料缺口\n");
      for (String warning : warnings) value.append("- ").append(warning).append('\n');
    }
    return value.toString();
  }

  private ObjectNode candidateSchema() {
    ObjectNode schema = json.createObjectNode(); schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    ObjectNode ids = properties.putObject("featureIds"); ids.put("type", "array");
    ids.putObject("items").put("type", "integer"); ids.put("maxItems", MAX_AI_CANDIDATES);
    schema.putArray("required").add("featureIds");
    return schema;
  }

  private ObjectNode analysisSchema() {
    ObjectNode schema = json.createObjectNode(); schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("level").put("type", "string").putArray("enum")
        .add("L0").add("L1").add("L2");
    properties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
    properties.putObject("reason").put("type", "string");
    stringArray(properties, "evidence", 1);
    stringArray(properties, "warnings", 0);
    objectArray(properties, "constructionContents", CONSTRUCTION_FIELDS, 1);
    objectArray(properties, "productionPlan", PLAN_FIELDS, 1);
    schema.putArray("required").add("level").add("confidence").add("reason")
        .add("evidence").add("warnings").add("constructionContents").add("productionPlan");
    return schema;
  }

  private void stringArray(ObjectNode properties, String name, int minimum) {
    ObjectNode array = properties.putObject(name); array.put("type", "array");
    array.putObject("items").put("type", "string"); array.put("minItems", minimum);
  }

  private void objectArray(ObjectNode properties, String name, List<String> fields, int minimum) {
    ObjectNode array = properties.putObject(name); array.put("type", "array");
    array.put("minItems", minimum);
    ObjectNode item = array.putObject("items"); item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode itemProperties = item.putObject("properties");
    ArrayNode required = item.putArray("required");
    for (String field : fields) {
      itemProperties.putObject(field).put("type", "string"); required.add(field);
    }
    if ("constructionContents".equals(name)) {
      itemProperties.with("changeType").putArray("enum").add("CONFIGURATION")
          .add("ENHANCEMENT").add("NEW_FEATURE").add("INTEGRATION").add("DATA")
          .add("NON_FUNCTIONAL").add("OUT_OF_SCOPE");
      itemProperties.with("priority").putArray("enum").add("P0").add("P1").add("P2").add("P3");
    }
  }

  private void mergeWarnings(JsonNode result, List<String> warnings) {
    if (result == null || !result.isObject() || result.get("warnings") == null
        || !result.get("warnings").isArray()) return;
    ArrayNode values = (ArrayNode) result.get("warnings");
    Set<String> existing = new HashSet<String>();
    for (JsonNode value : values) if (value.isTextual()) existing.add(value.asText());
    for (String warning : warnings) if (existing.add(warning)) values.add(warning);
  }

  private void validate(JsonNode result) {
    if (result == null || !result.isObject() || result.size() != 7
        || !textual(result, "level") || !LEVELS.contains(result.get("level").asText())
        || result.get("confidence") == null || !result.get("confidence").isNumber()
        || result.get("confidence").asDouble() < 0 || result.get("confidence").asDouble() > 1
        || !textual(result, "reason") || blank(result.get("reason").asText())) {
      throw incompatible();
    }
    validateStrings(result.get("evidence"), true);
    validateStrings(result.get("warnings"), false);
    validateRows(result.get("constructionContents"), CONSTRUCTION_FIELDS, true);
    validateRows(result.get("productionPlan"), PLAN_FIELDS, false);
  }

  private void validateStrings(JsonNode values, boolean required) {
    if (values == null || !values.isArray() || required && values.size() == 0) throw incompatible();
    for (JsonNode value : values) if (!value.isTextual() || blank(value.asText())) throw incompatible();
  }

  private void validateRows(JsonNode rows, List<String> fields, boolean construction) {
    if (rows == null || !rows.isArray() || rows.size() == 0) throw incompatible();
    for (JsonNode row : rows) {
      if (!row.isObject() || row.size() != fields.size()) throw incompatible();
      for (String field : fields) {
        if (!textual(row, field) || blank(row.get(field).asText())) throw incompatible();
      }
      if (construction) {
        if (!CHANGE_TYPES.contains(row.get("changeType").asText())
            || !PRIORITIES.contains(row.get("priority").asText())) throw incompatible();
      } else {
        validateDate(row.get("plannedStart").asText());
        validateDate(row.get("plannedEnd").asText());
      }
    }
  }

  private void validateDate(String value) {
    if ("待确认".equals(value)) return;
    try { LocalDate.parse(value); }
    catch (DateTimeParseException invalid) { throw incompatible(); }
  }

  private boolean textual(JsonNode value, String field) {
    return value.get(field) != null && value.get(field).isTextual();
  }

  private AiServiceException incompatible() {
    return new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
  }

  private long number(Map<String, Object> value, String key) {
    return ((Number) value.get(key)).longValue();
  }

  private String text(Object value) {
    return value == null || blank(String.valueOf(value)) ? "待确认" : String.valueOf(value).trim();
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static Set<String> set(String... values) {
    return new HashSet<String>(Arrays.asList(values));
  }
}
