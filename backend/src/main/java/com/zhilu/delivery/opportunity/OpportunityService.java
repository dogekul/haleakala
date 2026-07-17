package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.customer.CustomerService;
import com.zhilu.delivery.project.CreateProjectCommand;
import com.zhilu.delivery.project.ProjectService;
import com.zhilu.delivery.project.ProjectView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityService {
  private static final Set<String> STATUSES =
      new HashSet<String>(Arrays.asList("OPEN", "WON", "LOST"));
  private final JdbcTemplate jdbc;
  private final CustomerService customers;
  private final OpportunityGate gate;
  private final ProjectService projects;

  public OpportunityService(
      JdbcTemplate jdbc, CustomerService customers, OpportunityGate gate, ProjectService projects) {
    this.jdbc = jdbc;
    this.customers = customers;
    this.gate = gate;
    this.projects = projects;
  }

  public List<Map<String, Object>> list(long organizationId, String keyword, Long customerId,
      Long productId, Long commercialOwnerUserId, Long solutionOwnerUserId,
      Long projectManagerUserId, Long operationOwnerUserId, String stage, String status) {
    validateStageAndStatus(stage, status);
    StringBuilder sql = new StringBuilder(selectSql()).append(" where o.organization_id=?");
    List<Object> args = new ArrayList<Object>();
    args.add(organizationId);
    if (!blank(keyword)) {
      sql.append(" and (lower(o.title) like ? or lower(coalesce(c.name,o.customer_name_snapshot)) like ?)");
      String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      args.add(pattern); args.add(pattern);
    }
    appendFilter(sql, args, "o.customer_id", customerId);
    appendFilter(sql, args, "o.product_id", productId);
    appendFilter(sql, args, "o.commercial_owner_user_id", commercialOwnerUserId);
    appendFilter(sql, args, "o.solution_owner_user_id", solutionOwnerUserId);
    appendFilter(sql, args, "o.project_manager_user_id", projectManagerUserId);
    appendFilter(sql, args, "o.operation_owner_user_id", operationOwnerUserId);
    if (!blank(stage)) { sql.append(" and o.stage=?"); args.add(stage); }
    if (!blank(status)) { sql.append(" and o.status=?"); args.add(status); }
    sql.append(" order by o.updated_at desc,o.id desc");
    return jdbc.query(sql.toString(), (row, index) -> row(row), args.toArray());
  }

  public Map<String, Object> get(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        selectSql() + " where o.organization_id=? and o.id=?",
        (row, index) -> row(row), organizationId, id);
    if (values.isEmpty()) throw new NotFoundException("商机不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> create(long organizationId, long actorId, Input input) {
    References references = validateReferences(organizationId, input);
    Map<String, Object> values = editableValues(input, references);
    values.put("organization_id", organizationId);
    values.put("stage", OpportunityStage.LEAD.name());
    values.put("status", "OPEN");
    values.put("created_by", actorId);
    String[] columns = values.keySet().toArray(new String[values.size()]);
    long id = new SimpleJdbcInsert(jdbc).withTableName("sales_opportunity")
        .usingColumns(columns).usingGeneratedKeyColumns("id")
        .executeAndReturnKey(values).longValue();
    return get(organizationId, id);
  }

  @Transactional
  public Map<String, Object> update(
      long organizationId, long id, long version, Input input) {
    Map<String, Object> current = get(organizationId, id);
    if (((Number) current.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
    if (((Number) current.get("customerId")).longValue() != input.customerId.longValue()) {
      throw new ConflictException("商机客户不能修改");
    }
    References references = validateReferences(organizationId, input);
    int changed = jdbc.update("update sales_opportunity set customer_id=?,"
            + "customer_name_snapshot=?,title=?,note=?,amount=?,product_id=?,product_version_id=?,"
            + "commercial_owner_user_id=?,solution_owner_user_id=?,project_manager_user_id=?,"
            + "operation_owner_user_id=?,updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and version=?",
        input.customerId, references.customerName, input.title.trim(), clean(input.note),
        input.amount == null ? BigDecimal.ZERO : input.amount, input.productId,
        input.productVersionId, input.commercialOwnerUserId, input.solutionOwnerUserId,
        input.projectManagerUserId, input.operationOwnerUserId, id, organizationId, version);
    if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    return get(organizationId, id);
  }

  public List<Map<String, Object>> ownerOptions(long organizationId) {
    return jdbc.query("select id,display_name from app_user "
            + "where organization_id=? and status='ACTIVE' order by id",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("displayName", row.getString("display_name"));
          return value;
        }, organizationId);
  }

  @Transactional
  public Map<String, Object> advance(
      long organizationId, long id, long version, String decision) {
    Map<String, Object> current = get(organizationId, id);
    assertVersion(current, version);
    assertOpen(current);
    OpportunityStage stage = OpportunityStage.valueOf(String.valueOf(current.get("stage")));
    boolean decisionStage = stage == OpportunityStage.OPPORTUNITY
        || stage == OpportunityStage.BIDDING || stage == OpportunityStage.CONTRACT;
    if (decisionStage && !"PASS".equals(decision) && !"REJECT".equals(decision)) {
      throw new IllegalArgumentException("当前阶段必须选择 PASS 或 REJECT");
    }
    if (!decisionStage && !blank(decision)) {
      throw new IllegalArgumentException("当前阶段不接受关口决策");
    }
    if (stage == OpportunityStage.CONTRACT && "PASS".equals(decision)) {
      throw new ConflictException("合同通过请使用转交实施操作");
    }
    List<String> missing = gate.missingArtifacts(id, stage, decision);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("缺少必需产出物：" + join(missing));
    }
    boolean lost = "REJECT".equals(decision);
    String targetStage = lost ? stage.name() : stage.next().name();
    int changed = jdbc.update("update sales_opportunity set stage=?,status=?,"
            + "stage_entered_at=case when ? then stage_entered_at else current_timestamp end,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and version=? and status='OPEN'",
        targetStage, lost ? "LOST" : "OPEN", lost, id, organizationId, version);
    if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    return get(organizationId, id);
  }

  public List<Map<String, Object>> activities(long organizationId, long opportunityId) {
    get(organizationId, opportunityId);
    return jdbc.query("select * from opportunity_activity where organization_id=? "
            + "and opportunity_id=? order by sort_order,id",
        (row, index) -> activityRow(row), organizationId, opportunityId);
  }

  @Transactional
  public Map<String, Object> addActivity(long organizationId, long opportunityId,
      long actorId, String title, int sortOrder) {
    Map<String, Object> opportunity = get(organizationId, opportunityId);
    assertOpen(opportunity);
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("opportunity_id", opportunityId);
    values.put("stage_code", opportunity.get("stage"));
    values.put("title", title.trim());
    values.put("status", "TODO");
    values.put("sort_order", sortOrder);
    values.put("created_by", actorId);
    long id = insert("opportunity_activity", values);
    return activity(organizationId, opportunityId, id);
  }

  @Transactional
  public Map<String, Object> updateActivity(long organizationId, long opportunityId,
      long activityId, String status, long version) {
    get(organizationId, opportunityId);
    if (!"TODO".equals(status) && !"DONE".equals(status)) {
      throw new IllegalArgumentException("活动状态不受支持");
    }
    Map<String, Object> current = activity(organizationId, opportunityId, activityId);
    assertVersion(current, version);
    int changed = jdbc.update("update opportunity_activity set status=?,"
            + "completed_at=case when ?='DONE' then current_timestamp else null end,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where id=? and organization_id=? and opportunity_id=? and version=?",
        status, status, activityId, organizationId, opportunityId, version);
    if (changed == 0) throw new ConflictException("数据已被更新，请刷新后重试");
    return activity(organizationId, opportunityId, activityId);
  }

  public List<Map<String, Object>> artifacts(long organizationId, long opportunityId) {
    get(organizationId, opportunityId);
    return jdbc.query("select a.*,f.original_name file_name from opportunity_artifact a "
            + "left join file_object f on f.id=a.file_id "
            + "where a.organization_id=? and a.opportunity_id=? order by a.created_at,a.id",
        (row, index) -> artifactRow(row), organizationId, opportunityId);
  }

  @Transactional
  public Map<String, Object> addArtifact(long organizationId, long opportunityId,
      long actorId, ArtifactInput input) {
    Map<String, Object> opportunity = get(organizationId, opportunityId);
    assertOpen(opportunity);
    OpportunityStage stage = OpportunityStage.valueOf(String.valueOf(opportunity.get("stage")));
    if (stage == OpportunityStage.LEAD && "RESEARCH_REPORT".equals(input.artifactType)) {
      throw new IllegalArgumentException("需求调研报告请通过商机推进填写并提交");
    }
    gate.validateArtifact(stage, input.artifactType, input.contentMarkdown, input.fileId);
    if (input.fileId != null) {
      Integer files = jdbc.queryForObject(
          "select count(*) from file_object where id=? and organization_id=?",
          Integer.class, input.fileId, organizationId);
      if (files == null || files == 0) throw new NotFoundException("文件不存在");
    }
    if (!blank(input.decision)
        && !"PASS".equals(input.decision) && !"REJECT".equals(input.decision)) {
      throw new IllegalArgumentException("关口决策不受支持");
    }
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("opportunity_id", opportunityId);
    values.put("stage_from", stage.name());
    values.put("artifact_type", input.artifactType);
    values.put("title", input.title.trim());
    values.put("content_markdown", gate.isFileType(input.artifactType)
        ? null : input.contentMarkdown.trim());
    values.put("file_id", input.fileId);
    values.put("decision", clean(input.decision));
    values.put("created_by", actorId);
    long id = insert("opportunity_artifact", values);
    return artifact(organizationId, opportunityId, id);
  }

  @Transactional
  public Map<String, Object> advanceWithResearchReport(
      long organizationId, long opportunityId, long actorId, long version,
      long outlineLinkId, long sourceTemplateId, long sourceTemplateRevision, String title) {
    Map<String, Object> current = get(organizationId, opportunityId);
    assertVersion(current, version);
    assertOpen(current);
    if (!OpportunityStage.LEAD.name().equals(current.get("stage"))) {
      throw new ConflictException("只有线索阶段可以提交需求调研报告");
    }
    Integer validLink = jdbc.queryForObject(
        "select count(*) from outline_document_link where id=? and organization_id=? "
            + "and business_key=?",
        Integer.class, outlineLinkId, organizationId,
        "OPPORTUNITY:" + opportunityId + ":RESEARCH_REPORT");
    if (validLink == null || validLink == 0) throw new NotFoundException("需求调研报告不存在");
    int inserted = jdbc.update(
        "insert into opportunity_artifact(organization_id,opportunity_id,stage_from,"
            + "artifact_type,title,outline_link_id,source_template_id,"
            + "source_template_revision,created_by) "
            + "select ?,?,'LEAD','RESEARCH_REPORT',?,?,?,?,? where not exists "
            + "(select 1 from opportunity_artifact where opportunity_id=? "
            + "and artifact_type='RESEARCH_REPORT')",
        organizationId, opportunityId, title.trim(), outlineLinkId, sourceTemplateId,
        sourceTemplateRevision, actorId, opportunityId);
    if (inserted == 0) throw new ConflictException("需求调研报告已提交");
    return advance(organizationId, opportunityId, version, null);
  }

  @Transactional
  public Map<String, Object> handoff(long organizationId, long opportunityId,
      long actorId, HandoffInput input) {
    Map<String, Object> opportunity = get(organizationId, opportunityId);
    assertVersion(opportunity, input.version.longValue());
    assertOpen(opportunity);
    if (!OpportunityStage.CONTRACT.name().equals(opportunity.get("stage"))) {
      throw new ConflictException("只有合同阶段可以转交实施");
    }
    validateHandoffPayload(input);
    Map<String, Object> customer = customers.get(
        organizationId, ((Number) opportunity.get("customerId")).longValue());
    if (!"ACTIVE".equals(customer.get("status"))) {
      throw new IllegalArgumentException("客户已停用，不能转交实施");
    }
    validateHandoffReferences(organizationId, opportunity);
    List<String> missing = gate.missingArtifacts(
        opportunityId, OpportunityStage.CONTRACT, "PASS");
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("缺少必需产出物：" + join(missing));
    }
    ProjectView project;
    try {
      if ("CREATE".equals(input.mode)) {
        if (input.project == null) throw new IllegalArgumentException("请填写新项目资料");
        project = projects.create(new CreateProjectCommand(organizationId,
            input.project.code, input.project.name,
            ((Number) opportunity.get("customerId")).longValue(), input.project.productId,
            input.project.productVersionId, input.project.managerUserId, actorId,
            input.project.startDate, input.project.plannedEndDate, input.project.gateMode));
      } else if ("LINK".equals(input.mode)) {
        project = projects.getForOrganization(input.projectId.longValue(), organizationId);
        if (project.getCustomerId() == null
            || project.getCustomerId().longValue()
            != ((Number) opportunity.get("customerId")).longValue()) {
          throw new IllegalArgumentException("项目客户与商机客户不一致");
        }
        if ("CLOSE".equals(project.getCurrentStage()) || "CLOSED".equals(project.getStatus())
            || "COMPLETED".equals(project.getStatus())) {
          throw new ConflictException("已收尾项目不能作为交接目标");
        }
        Integer claimed = jdbc.queryForObject(
            "select count(*) from sales_opportunity where project_id=? and id<>?",
            Integer.class, project.getId(), opportunityId);
        if (claimed != null && claimed > 0) {
          throw new ConflictException("项目已关联其他商机");
        }
      } else {
        throw new IllegalArgumentException("转交模式不受支持");
      }
      int changed = jdbc.update("update sales_opportunity set project_id=?,status='WON',"
              + "updated_at=current_timestamp,version=version+1 "
              + "where id=? and organization_id=? and version=? and status='OPEN' "
              + "and stage='CONTRACT' and project_id is null",
          project.getId(), opportunityId, organizationId, input.version);
      if (changed == 0) throw new ConflictException("商机已转交或数据已被更新");
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("项目编号或项目关联已存在");
    }
    return get(organizationId, opportunityId);
  }

  public Map<String, Object> fullLink(long organizationId, long opportunityId) {
    Map<String, Object> opportunity = get(organizationId, opportunityId);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    Map<String, Object> customer = new LinkedHashMap<String, Object>();
    customer.put("id", opportunity.get("customerId"));
    customer.put("name", opportunity.get("customerName"));
    result.put("customer", customer);
    Map<String, Object> opportunityNode = new LinkedHashMap<String, Object>();
    opportunityNode.put("id", opportunity.get("id"));
    opportunityNode.put("title", opportunity.get("title"));
    opportunityNode.put("stage", opportunity.get("stage"));
    opportunityNode.put("status", opportunity.get("status"));
    result.put("opportunity", opportunityNode);
    result.put("project", projectNode(organizationId, (Long) opportunity.get("projectId")));
    result.put("operation", operationNode(organizationId, opportunityId));
    return result;
  }

  private References validateReferences(long organizationId, Input input) {
    Map<String, Object> customer = customers.get(organizationId, input.customerId.longValue());
    if (!"ACTIVE".equals(customer.get("status"))) {
      throw new IllegalArgumentException("客户已停用，不能创建商机");
    }
    validateOwner(organizationId, input.commercialOwnerUserId);
    validateOwner(organizationId, input.solutionOwnerUserId);
    validateOwner(organizationId, input.projectManagerUserId);
    validateOwner(organizationId, input.operationOwnerUserId);
    validateProduct(organizationId, input.productId, input.productVersionId);
    return new References(String.valueOf(customer.get("name")));
  }

  private Map<String, Object> activity(
      long organizationId, long opportunityId, long activityId) {
    List<Map<String, Object>> values = jdbc.query(
        "select * from opportunity_activity where id=? and organization_id=? and opportunity_id=?",
        (row, index) -> activityRow(row), activityId, organizationId, opportunityId);
    if (values.isEmpty()) throw new NotFoundException("活动不存在");
    return values.get(0);
  }

  private Map<String, Object> projectNode(long organizationId, Long projectId) {
    if (projectId == null) return null;
    List<Map<String, Object>> values = jdbc.query(
        "select id,name,current_stage,status from delivery_project "
            + "where id=? and organization_id=?",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("name", row.getString("name"));
          value.put("stage", row.getString("current_stage"));
          value.put("status", row.getString("status"));
          return value;
        }, projectId, organizationId);
    return values.isEmpty() ? null : values.get(0);
  }

  private Map<String, Object> operationNode(long organizationId, long opportunityId) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,title,stage,status from customer_operation "
            + "where opportunity_id=? and organization_id=?",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("title", row.getString("title"));
          value.put("stage", row.getString("stage"));
          value.put("status", row.getString("status"));
          return value;
        }, opportunityId, organizationId);
    return values.isEmpty() ? null : values.get(0);
  }

  private Map<String, Object> artifact(
      long organizationId, long opportunityId, long artifactId) {
    List<Map<String, Object>> values = jdbc.query(
        "select a.*,f.original_name file_name from opportunity_artifact a "
            + "left join file_object f on f.id=a.file_id "
            + "where a.id=? and a.organization_id=? and a.opportunity_id=?",
        (row, index) -> artifactRow(row), artifactId, organizationId, opportunityId);
    if (values.isEmpty()) throw new NotFoundException("产出物不存在");
    return values.get(0);
  }

  private Map<String, Object> activityRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("opportunityId", row.getLong("opportunity_id"));
    value.put("stageCode", row.getString("stage_code"));
    value.put("title", row.getString("title"));
    value.put("status", row.getString("status"));
    value.put("sortOrder", row.getInt("sort_order"));
    value.put("createdAt", row.getTimestamp("created_at").toLocalDateTime());
    value.put("completedAt", row.getTimestamp("completed_at") == null
        ? null : row.getTimestamp("completed_at").toLocalDateTime());
    value.put("version", row.getLong("version"));
    return value;
  }

  private Map<String, Object> artifactRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("opportunityId", row.getLong("opportunity_id"));
    value.put("stageFrom", row.getString("stage_from"));
    value.put("artifactType", row.getString("artifact_type"));
    value.put("title", row.getString("title"));
    value.put("contentMarkdown", row.getString("content_markdown"));
    value.put("outlineLinkId", nullableLong(row, "outline_link_id"));
    value.put("sourceTemplateId", nullableLong(row, "source_template_id"));
    value.put("sourceTemplateRevision", nullableLong(row, "source_template_revision"));
    value.put("fileId", nullableLong(row, "file_id"));
    value.put("fileName", row.getString("file_name"));
    value.put("decision", row.getString("decision"));
    value.put("createdAt", row.getTimestamp("created_at").toLocalDateTime());
    return value;
  }

  private long insert(String table, Map<String, Object> values) {
    String[] columns = values.keySet().toArray(new String[values.size()]);
    return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(columns)
        .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
  }

  private void assertOpen(Map<String, Object> opportunity) {
    if (!"OPEN".equals(opportunity.get("status"))) {
      throw new ConflictException("终态商机不能继续推进");
    }
  }

  private void assertVersion(Map<String, Object> value, long version) {
    if (((Number) value.get("version")).longValue() != version) {
      throw new ConflictException("数据已被更新，请刷新后重试");
    }
  }

  private String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) result.append("、");
      result.append(value);
    }
    return result.toString();
  }

  private void validateOwner(long organizationId, Long userId) {
    if (userId == null) return;
    List<String> statuses = jdbc.queryForList(
        "select status from app_user where id=? and organization_id=?",
        String.class, userId, organizationId);
    if (statuses.isEmpty()) throw new NotFoundException("负责人不存在");
    if (!"ACTIVE".equals(statuses.get(0))) throw new IllegalArgumentException("负责人已停用");
  }

  private void validateProduct(long organizationId, Long productId, Long versionId) {
    if (productId == null && versionId == null) return;
    if (productId == null) throw new IllegalArgumentException("选择产品版本前必须选择产品");
    Integer products = jdbc.queryForObject(
        "select count(*) from product where id=? and organization_id=? and status='ACTIVE'",
        Integer.class, productId, organizationId);
    if (products == null || products == 0) throw new NotFoundException("产品不存在或已归档");
    if (versionId == null) return;
    Integer versions = jdbc.queryForObject(
        "select count(*) from product_version where id=? and product_id=? and status='RELEASED'",
        Integer.class, versionId, productId);
    if (versions == null || versions == 0) {
      throw new IllegalArgumentException("产品版本不属于所选产品");
    }
  }

  private void validateHandoffReferences(
      long organizationId, Map<String, Object> opportunity) {
    validateOwner(organizationId, nullableNumber(opportunity.get("commercialOwnerUserId")));
    validateOwner(organizationId, nullableNumber(opportunity.get("solutionOwnerUserId")));
    validateOwner(organizationId, nullableNumber(opportunity.get("projectManagerUserId")));
    validateOwner(organizationId, nullableNumber(opportunity.get("operationOwnerUserId")));
    validateProduct(organizationId, nullableNumber(opportunity.get("productId")),
        nullableNumber(opportunity.get("productVersionId")));
  }

  private void validateHandoffPayload(HandoffInput input) {
    if ("CREATE".equals(input.mode)) {
      if (input.project == null) throw new IllegalArgumentException("请填写新项目资料");
      if (input.projectId != null) {
        throw new IllegalArgumentException("交接参数不能同时包含新项目和已有项目");
      }
      if (input.project.startDate == null || input.project.plannedEndDate == null) {
        throw new IllegalArgumentException("项目开始和计划结束日期必填");
      }
      if (input.project.plannedEndDate.isBefore(input.project.startDate)) {
        throw new IllegalArgumentException("计划结束日期不能早于开始日期");
      }
      return;
    }
    if ("LINK".equals(input.mode)) {
      if (input.projectId == null) throw new IllegalArgumentException("请选择已有项目");
      if (input.project != null) {
        throw new IllegalArgumentException("交接参数不能同时包含新项目和已有项目");
      }
      return;
    }
    throw new IllegalArgumentException("转交模式不受支持");
  }

  private Long nullableNumber(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private Map<String, Object> editableValues(Input input, References references) {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("customer_id", input.customerId);
    values.put("customer_name_snapshot", references.customerName);
    values.put("title", input.title.trim());
    values.put("note", clean(input.note));
    values.put("amount", input.amount == null ? BigDecimal.ZERO : input.amount);
    values.put("product_id", input.productId);
    values.put("product_version_id", input.productVersionId);
    values.put("commercial_owner_user_id", input.commercialOwnerUserId);
    values.put("solution_owner_user_id", input.solutionOwnerUserId);
    values.put("project_manager_user_id", input.projectManagerUserId);
    values.put("operation_owner_user_id", input.operationOwnerUserId);
    return values;
  }

  private String selectSql() {
    return "select o.*,coalesce(c.name,o.customer_name_snapshot) customer_display_name,"
        + "p.name product_name,pv.version_name product_version_name,"
        + "cu.display_name commercial_owner_name,su.display_name solution_owner_name,"
        + "pu.display_name project_manager_name,ou.display_name operation_owner_name,"
        + "dp.name project_name from sales_opportunity o "
        + "left join customer c on c.id=o.customer_id "
        + "left join product p on p.id=o.product_id "
        + "left join product_version pv on pv.id=o.product_version_id "
        + "left join app_user cu on cu.id=o.commercial_owner_user_id "
        + "left join app_user su on su.id=o.solution_owner_user_id "
        + "left join app_user pu on pu.id=o.project_manager_user_id "
        + "left join app_user ou on ou.id=o.operation_owner_user_id "
        + "left join delivery_project dp on dp.id=o.project_id";
  }

  private Map<String, Object> row(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("organizationId", row.getLong("organization_id"));
    value.put("customerId", row.getLong("customer_id"));
    value.put("customerName", row.getString("customer_display_name"));
    value.put("title", row.getString("title"));
    value.put("note", row.getString("note"));
    value.put("amount", row.getBigDecimal("amount"));
    value.put("productId", nullableLong(row, "product_id"));
    value.put("productName", row.getString("product_name"));
    value.put("productVersionId", nullableLong(row, "product_version_id"));
    value.put("productVersionName", row.getString("product_version_name"));
    value.put("commercialOwnerUserId", nullableLong(row, "commercial_owner_user_id"));
    value.put("commercialOwnerName", row.getString("commercial_owner_name"));
    value.put("solutionOwnerUserId", nullableLong(row, "solution_owner_user_id"));
    value.put("solutionOwnerName", row.getString("solution_owner_name"));
    value.put("projectManagerUserId", nullableLong(row, "project_manager_user_id"));
    value.put("projectManagerName", row.getString("project_manager_name"));
    value.put("operationOwnerUserId", nullableLong(row, "operation_owner_user_id"));
    value.put("operationOwnerName", row.getString("operation_owner_name"));
    value.put("stage", row.getString("stage"));
    value.put("status", row.getString("status"));
    value.put("projectId", nullableLong(row, "project_id"));
    value.put("projectName", row.getString("project_name"));
    value.put("stageEnteredAt", row.getTimestamp("stage_entered_at").toLocalDateTime());
    value.put("createdAt", row.getTimestamp("created_at").toLocalDateTime());
    value.put("updatedAt", row.getTimestamp("updated_at").toLocalDateTime());
    value.put("version", row.getLong("version"));
    return value;
  }

  private Long nullableLong(ResultSet row, String column) throws SQLException {
    long value = row.getLong(column);
    return row.wasNull() ? null : value;
  }

  private void validateStageAndStatus(String stage, String status) {
    if (!blank(stage)) {
      try { OpportunityStage.valueOf(stage); }
      catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("商机阶段不受支持"); }
    }
    if (!blank(status) && !STATUSES.contains(status)) {
      throw new IllegalArgumentException("商机状态不受支持");
    }
  }

  private void appendFilter(StringBuilder sql, List<Object> args, String column, Long value) {
    if (value != null) { sql.append(" and ").append(column).append("=?"); args.add(value); }
  }

  private String clean(String value) { return blank(value) ? null : value.trim(); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

  public static class Input {
    @NotNull public Long customerId;
    @NotBlank @Size(max = 180) public String title;
    @Size(max = 10000) public String note;
    @DecimalMin("0") public BigDecimal amount = BigDecimal.ZERO;
    public Long productId;
    public Long productVersionId;
    public Long commercialOwnerUserId;
    public Long solutionOwnerUserId;
    public Long projectManagerUserId;
    public Long operationOwnerUserId;
  }

  public static final class ArtifactInput {
    @NotBlank public String artifactType;
    @NotBlank @Size(max = 240) public String title;
    public String contentMarkdown;
    public Long fileId;
    public String decision;
  }

  public static final class HandoffInput {
    @NotBlank public String mode;
    @NotNull public Long version;
    public Long projectId;
    @javax.validation.Valid public ProjectInput project;
  }

  public static final class ProjectInput {
    @NotBlank @Size(max = 64) public String code;
    @NotBlank @Size(max = 180) public String name;
    @NotNull public Long productId;
    @NotNull public Long productVersionId;
    @NotNull public Long managerUserId;
    public java.time.LocalDate startDate;
    public java.time.LocalDate plannedEndDate;
    public String gateMode = "BLOCK";
  }

  private static final class References {
    private final String customerName;
    private References(String customerName) { this.customerName = customerName; }
  }
}
