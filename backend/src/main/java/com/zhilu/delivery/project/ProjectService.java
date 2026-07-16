package com.zhilu.delivery.project;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.DocumentJobService;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.operation.CustomerOperationService;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
  private static final List<String> PROJECT_STATUSES =
      Arrays.asList("ACTIVE", "SUSPENDED", "CLOSING", "CLOSED");
  private final JdbcTemplate jdbc;
  private final CustomerOperationService operations;
  private final DocumentJobService documentJobs;

  public ProjectService(
      JdbcTemplate jdbc, CustomerOperationService operations, DocumentJobService documentJobs) {
    this.jdbc = jdbc;
    this.operations = operations;
    this.documentJobs = documentJobs;
  }

  @Transactional
  public ProjectView create(CreateProjectCommand command) {
    Integer validVersion = jdbc.queryForObject(
        "select count(*) from product_version pv join product p on p.id=pv.product_id "
            + "where pv.id=? and pv.product_id=? and pv.status='RELEASED' "
            + "and p.status='ACTIVE' and p.organization_id=?",
        Integer.class, command.getProductVersionId(), command.getProductId(),
        command.getOrganizationId());
    if (validVersion == null || validVersion != 1) {
      throw new IllegalArgumentException("产品或版本不可用于新项目");
    }
    Map<String, Object> customer = customerForProject(
        command.getOrganizationId(), command.getCustomerId());
    assertOrganizationUser(command.getOrganizationId(), command.getManagerUserId());
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("organization_id", command.getOrganizationId());
    values.put("code", command.getCode().trim());
    values.put("name", command.getName().trim());
    values.put("customer_id", command.getCustomerId());
    values.put("customer_name", customer.get("name"));
    values.put("product_id", command.getProductId());
    values.put("product_version_id", command.getProductVersionId());
    values.put("manager_user_id", command.getManagerUserId());
    values.put("status", "ACTIVE");
    values.put("current_stage", DeliveryStage.START.name());
    values.put("risk_level", "GREEN");
    values.put("gate_mode", normalizeGateMode(command.getGateMode()));
    values.put("start_date", date(command.getStartDate()));
    values.put("planned_end_date", date(command.getPlannedEndDate()));
    values.put("created_by", command.getCreatedByUserId());
    long projectId = insert("delivery_project", values);

    DeliveryStage[] stages = DeliveryStage.values();
    for (int index = 0; index < stages.length; index++) {
      DeliveryStage stage = stages[index];
      jdbc.update("insert into stage_instance(project_id,stage_code,stage_name,stage_order,status,"
              + "gate_status,started_at) values (?,?,?,?,?,'READY',?)",
          projectId, stage.name(), stage.getDisplayName(), index + 1,
          index == 0 ? "ACTIVE" : "PENDING",
          index == 0 ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
    }
    jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
            + "values (?,?,?,100)",
        projectId, command.getManagerUserId(), "DELIVERY_MANAGER");
    if (command.getCreatedByUserId() != command.getManagerUserId()) {
      jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
              + "values (?,?,?,100)",
          projectId, command.getCreatedByUserId(), "DELIVERY_ENGINEER");
    }
    activity(projectId, command.getCreatedByUserId(), "PROJECT_CREATED", "创建项目并初始化七阶段", null);
    documentJobs.enqueueProjectInitialization(command.getOrganizationId(), projectId);
    return get(projectId);
  }

  public List<ProjectView> list() {
    List<Long> ids = jdbc.queryForList(
        "select id from delivery_project order by updated_at desc,id desc", Long.class);
    List<ProjectView> result = new ArrayList<ProjectView>();
    for (Long id : ids) result.add(get(id));
    return result;
  }

  public List<ProjectView> list(CurrentUser user) {
    if (user == null) return list();
    String sql = "select p.id from delivery_project p";
    List<Long> ids;
    if (hasCrossProjectScope(user)) {
      ids = jdbc.queryForList(sql + " where p.organization_id=? order by p.updated_at desc",
          Long.class, user.getOrganizationId());
    } else {
      ids = jdbc.queryForList(sql + " join project_member m on m.project_id=p.id "
              + "where p.organization_id=? and m.user_id=? order by p.updated_at desc",
          Long.class, user.getOrganizationId(), user.getId());
    }
    List<ProjectView> result = new ArrayList<ProjectView>();
    for (Long id : ids) result.add(get(id));
    return result;
  }

  public ProjectView get(long projectId) {
    List<ProjectView> values = jdbc.query(
        "select p.*,coalesce(c.name,p.customer_name) customer_display_name,"
            + "pr.name product_name,pv.version_name,u.display_name manager_name "
            + "from delivery_project p join product pr on pr.id=p.product_id "
            + "join product_version pv on pv.id=p.product_version_id "
            + "join app_user u on u.id=p.manager_user_id "
            + "left join customer c on c.id=p.customer_id where p.id=?",
        (row, index) -> new ProjectView(
            row.getLong("id"), row.getLong("organization_id"), row.getString("code"),
            row.getString("name"), nullableLong(row, "customer_id"),
            row.getString("customer_display_name"), row.getLong("product_id"),
            row.getString("product_name"), row.getLong("product_version_id"),
            row.getString("version_name"), row.getLong("manager_user_id"),
            row.getString("manager_name"), row.getString("status"), row.getString("current_stage"),
            row.getString("risk_level"), row.getString("gate_mode"),
            row.getString("document_space_status"), row.getString("document_space_error"),
            localDate(row.getDate("start_date")),
            localDate(row.getDate("planned_end_date")), row.getLong("version"),
            stages(projectId), members(projectId), risks(projectId), milestones(projectId),
            templates(projectId), artifacts(projectId), activities(projectId)), projectId);
    if (values.isEmpty()) throw new NotFoundException("项目不存在");
    return values.get(0);
  }

  public ProjectView get(long projectId, CurrentUser user) {
    if (user != null) assertProjectAccess(projectId, user);
    return get(projectId);
  }

  public ProjectView getForOrganization(long projectId, long organizationId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from delivery_project where id=? and organization_id=?",
        Integer.class, projectId, organizationId);
    if (count == null || count == 0) throw new NotFoundException("项目不存在");
    return get(projectId);
  }

  @Transactional
  public ProjectView retryDocumentInitialization(long projectId, CurrentUser user) {
    ProjectView project = get(projectId, user);
    documentJobs.retryProjectInitialization(project.getOrganizationId(), projectId);
    return get(projectId);
  }

  @Transactional
  public ProjectView advanceStage(
      long projectId, DeliveryStage target, CurrentUser user) {
    ProjectView project = get(projectId, user);
    if (!"ACTIVE".equals(project.getStatus())) {
      throw new ConflictException("只有进行中的项目可以推进阶段");
    }
    DeliveryStage current = DeliveryStage.valueOf(project.getCurrentStage());
    if (target.ordinal() != current.ordinal() + 1) {
      throw new ConflictException("只能推进到下一个交付阶段");
    }
    Map<String, Object> gate = jdbc.queryForMap(
        "select gate_status,gate_message from stage_instance where project_id=? and stage_code=?",
        projectId, current.name());
    boolean blocked = "BLOCKING".equals(gate.get("gate_status"));
    if (blocked && GateMode.BLOCK.name().equals(project.getGateMode())) {
      throw new ConflictException(String.valueOf(gate.get("gate_message")));
    }
    jdbc.update("update stage_instance set status='COMPLETED',completed_at=current_timestamp,"
            + "updated_at=current_timestamp,version=version+1 where project_id=? and stage_code=?",
        projectId, current.name());
    jdbc.update("update stage_instance set status='ACTIVE',started_at=current_timestamp,"
            + "updated_at=current_timestamp,version=version+1 where project_id=? and stage_code=?",
        projectId, target.name());
    jdbc.update("update delivery_project set current_stage=?,updated_at=current_timestamp,"
            + "version=version+1 where id=?", target.name(), projectId);
    activity(projectId, user.getId(),
        blocked ? "STAGE_ADVANCED_WITH_WARNING" : "STAGE_ADVANCED",
        "项目阶段推进至" + target.getDisplayName(),
        blocked ? String.valueOf(gate.get("gate_message")) : null);
    if (target == DeliveryStage.CLOSE) {
      operations.ensureForClosedProject(user.getOrganizationId(), projectId, user.getId());
    }
    return get(projectId);
  }

  @Transactional
  public void setGate(
      long projectId, String stageCode, String status, String message, CurrentUser user) {
    assertProjectAccess(projectId, user);
    int changed = jdbc.update("update stage_instance set gate_status=?,gate_message=?,"
            + "updated_at=current_timestamp,version=version+1 where project_id=? and stage_code=?",
        status, message, projectId, stageCode);
    if (changed == 0) throw new NotFoundException("项目阶段不存在");
    activity(projectId, user.getId(), "GATE_UPDATED", "更新阶段门禁：" + stageCode, message);
  }

  @Transactional
  public Map<String, Object> addMember(
      long projectId, long userId, String projectRole, int allocation, CurrentUser user) {
    assertProjectAccess(projectId, user);
    assertOrganizationUser(user.getOrganizationId(), userId);
    jdbc.update("insert into project_member(project_id,user_id,project_role,allocation_percent) "
        + "values (?,?,?,?)", projectId, userId, projectRole, allocation);
    activity(projectId, user.getId(), "MEMBER_ADDED", "添加项目成员", String.valueOf(userId));
    return members(projectId).stream().filter(item -> ((Number) item.get("userId")).longValue() == userId)
        .findFirst().orElse(Collections.emptyMap());
  }

  @Transactional
  public Map<String, Object> addRisk(
      long projectId, String title, String category, int probability, int impact,
      Long ownerUserId, String mitigation, LocalDate dueDate, CurrentUser user) {
    assertProjectAccess(projectId, user);
    assertOrganizationUser(user.getOrganizationId(), ownerUserId);
    String level = riskLevel(probability, impact);
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("project_id", projectId);
    values.put("title", title);
    values.put("category", category);
    values.put("probability", probability);
    values.put("impact", impact);
    values.put("risk_level", level);
    values.put("status", "OPEN");
    values.put("owner_user_id", ownerUserId);
    values.put("mitigation", mitigation);
    values.put("due_date", date(dueDate));
    long id = insert("project_risk", values);
    refreshProjectRisk(projectId);
    activity(projectId, user.getId(), "RISK_CREATED", "登记风险：" + title, level);
    return findById(risks(projectId), id);
  }

  @Transactional
  public Map<String, Object> updateRisk(
      long projectId, long riskId, String status, String mitigation, CurrentUser user) {
    assertProjectAccess(projectId, user);
    int changed = jdbc.update("update project_risk set status=?,mitigation=?,"
            + "updated_at=current_timestamp,version=version+1 where id=? and project_id=?",
        status, mitigation, riskId, projectId);
    if (changed == 0) throw new NotFoundException("项目风险不存在");
    refreshProjectRisk(projectId);
    activity(projectId, user.getId(), "RISK_UPDATED", "更新风险状态：" + status, String.valueOf(riskId));
    return findById(risks(projectId), riskId);
  }

  @Transactional
  public Map<String, Object> addMilestone(
      long projectId, String name, LocalDate dueDate, Long ownerUserId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    assertOrganizationUser(user.getOrganizationId(), ownerUserId);
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("project_id", projectId);
    values.put("name", name);
    values.put("due_date", date(dueDate));
    values.put("status", "PENDING");
    values.put("progress", 0);
    values.put("owner_user_id", ownerUserId);
    long id = insert("milestone", values);
    activity(projectId, user.getId(), "MILESTONE_CREATED", "新增里程碑：" + name, null);
    return findById(milestones(projectId), id);
  }

  @Transactional
  public Map<String, Object> saveTemplate(
      long projectId, Long id, String key, String title, String markdown,
      String status, long expectedVersion, CurrentUser user) {
    assertProjectAccess(projectId, user);
    long templateId;
    if (id == null) {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("project_id", projectId);
      values.put("template_key", key);
      values.put("title", title);
      values.put("content_markdown", markdown);
      values.put("status", status);
      values.put("updated_by", user.getId());
      templateId = insert("template_instance", values);
    } else {
      int changed = jdbc.update("update template_instance set title=?,content_markdown=?,status=?,"
              + "updated_by=?,updated_at=current_timestamp,version=version+1 "
              + "where id=? and project_id=? and version=?",
          title, markdown, status, user.getId(), id, projectId, expectedVersion);
      if (changed == 0) throw new ConflictException("模板已被其他人更新，请刷新后重试");
      templateId = id;
    }
    activity(projectId, user.getId(), "TEMPLATE_SAVED", "保存模板：" + title, key);
    return findById(templates(projectId), templateId);
  }

  @Transactional
  public ProjectView updateSettings(
      long projectId, String name, String status, String riskLevel, String gateMode,
      LocalDate plannedEndDate, long expectedVersion, CurrentUser user) {
    ProjectView current = get(projectId, user);
    validateStatusTransition(current.getStatus(), status);
    int changed = jdbc.update("update delivery_project set name=?,status=?,risk_level=?,gate_mode=?,"
            + "planned_end_date=?,updated_at=current_timestamp,version=version+1 "
            + "where id=? and version=?",
        name, status, riskLevel, normalizeGateMode(gateMode), date(plannedEndDate),
        projectId, expectedVersion);
    if (changed == 0) throw new ConflictException("项目已被其他人更新，请刷新后重试");
    activity(projectId, user.getId(), "PROJECT_SETTINGS_UPDATED", "更新项目信息与设置", null);
    return get(projectId);
  }

  private List<StageView> stages(long projectId) {
    return jdbc.query("select id,stage_code,stage_name,stage_order,status,gate_status,gate_message "
            + "from stage_instance where project_id=? order by stage_order",
        (row, index) -> new StageView(row.getLong("id"), row.getString("stage_code"),
            row.getString("stage_name"), row.getInt("stage_order"), row.getString("status"),
            row.getString("gate_status"), row.getString("gate_message")), projectId);
  }

  private List<Map<String, Object>> members(long projectId) {
    return jdbc.query("select m.user_id,u.display_name,m.project_role,m.allocation_percent "
            + "from project_member m join app_user u on u.id=m.user_id where m.project_id=?",
        (row, index) -> map("userId", row.getLong("user_id"), "displayName", row.getString("display_name"),
            "projectRole", row.getString("project_role"), "allocationPercent", row.getInt("allocation_percent")),
        projectId);
  }

  private List<Map<String, Object>> risks(long projectId) {
    return jdbc.query("select id,title,category,probability,impact,risk_level,status,owner_user_id,"
            + "mitigation,due_date,version from project_risk where project_id=? order by id desc",
        (row, index) -> map("id", row.getLong("id"), "title", row.getString("title"),
            "category", row.getString("category"), "probability", row.getInt("probability"),
            "impact", row.getInt("impact"), "riskLevel", row.getString("risk_level"),
            "status", row.getString("status"), "ownerUserId", row.getObject("owner_user_id"),
            "mitigation", row.getString("mitigation"), "dueDate", localDate(row.getDate("due_date")),
            "version", row.getLong("version")), projectId);
  }

  private List<Map<String, Object>> milestones(long projectId) {
    return jdbc.query("select id,name,due_date,status,progress,owner_user_id,version from milestone "
            + "where project_id=? order by due_date",
        (row, index) -> map("id", row.getLong("id"), "name", row.getString("name"),
            "dueDate", localDate(row.getDate("due_date")), "status", row.getString("status"),
            "progress", row.getInt("progress"), "ownerUserId", row.getObject("owner_user_id"),
            "version", row.getLong("version")), projectId);
  }

  private List<Map<String, Object>> templates(long projectId) {
    return jdbc.query("select id,template_key,title,content_markdown,status,updated_by,version "
            + "from template_instance where project_id=? order by id",
        (row, index) -> map("id", row.getLong("id"), "templateKey", row.getString("template_key"),
            "title", row.getString("title"), "contentMarkdown", row.getString("content_markdown"),
            "status", row.getString("status"), "updatedBy", row.getLong("updated_by"),
            "version", row.getLong("version")), projectId);
  }

  private List<Map<String, Object>> artifacts(long projectId) {
    return jdbc.query("select id,stage_code,file_id,artifact_type,name from project_artifact "
            + "where project_id=? order by id desc",
        (row, index) -> map("id", row.getLong("id"), "stageCode", row.getString("stage_code"),
            "fileId", row.getLong("file_id"), "artifactType", row.getString("artifact_type"),
            "name", row.getString("name")), projectId);
  }

  private List<Map<String, Object>> activities(long projectId) {
    return jdbc.query("select a.id,a.actor_user_id,u.display_name,a.action,a.summary,a.details_text,"
            + "a.created_at from project_activity a left join app_user u on u.id=a.actor_user_id "
            + "where a.project_id=? order by a.created_at desc,a.id desc limit 50",
        (row, index) -> map("id", row.getLong("id"), "actorUserId", row.getObject("actor_user_id"),
            "actorName", row.getString("display_name"), "action", row.getString("action"),
            "summary", row.getString("summary"), "details", row.getString("details_text"),
            "createdAt", row.getTimestamp("created_at").toLocalDateTime()), projectId);
  }

  private void assertProjectAccess(long projectId, CurrentUser user) {
    Integer project = jdbc.queryForObject(
        "select count(*) from delivery_project where id=? and organization_id=?",
        Integer.class, projectId, user.getOrganizationId());
    if (project == null || project == 0) throw new NotFoundException("项目不存在或无权访问");
    if (hasCrossProjectScope(user)) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from project_member where project_id=? and user_id=?",
        Integer.class, projectId, user.getId());
    if (count == null || count == 0) throw new NotFoundException("项目不存在或无权访问");
  }

  private void assertOrganizationUser(long organizationId, Long userId) {
    if (userId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from app_user where id=? and organization_id=? and status='ACTIVE'",
        Integer.class, userId, organizationId);
    if (count == null || count == 0) throw new NotFoundException("用户不存在或已停用");
  }

  private Map<String, Object> customerForProject(long organizationId, long customerId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,name,status from customer where id=? and organization_id=?",
        customerId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("客户不存在");
    Map<String, Object> customer = values.get(0);
    if (!"ACTIVE".equals(customer.get("status"))) {
      throw new IllegalArgumentException("停用客户不能创建项目");
    }
    return customer;
  }

  private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
    Object value = row.getObject(column);
    return value == null ? null : ((Number) value).longValue();
  }

  private boolean hasCrossProjectScope(CurrentUser user) {
    return user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO");
  }

  private void validateStatusTransition(String current, String target) {
    if (!PROJECT_STATUSES.contains(target)) {
      throw new IllegalArgumentException("项目状态不受支持");
    }
    if (current.equals(target)) return;
    boolean legal = "ACTIVE".equals(current)
        && ("SUSPENDED".equals(target) || "CLOSING".equals(target));
    legal = legal || "SUSPENDED".equals(current)
        && ("ACTIVE".equals(target) || "CLOSING".equals(target));
    legal = legal || "CLOSING".equals(current) && "CLOSED".equals(target);
    if (!legal) throw new ConflictException("非法的项目状态转换");
  }

  private void refreshProjectRisk(long projectId) {
    List<String> levels = jdbc.queryForList(
        "select risk_level from project_risk where project_id=? and status='OPEN'",
        String.class, projectId);
    String result = levels.contains("RED") ? "RED" : levels.contains("YELLOW") ? "YELLOW" : "GREEN";
    jdbc.update("update delivery_project set risk_level=?,updated_at=current_timestamp where id=?",
        result, projectId);
  }

  private String riskLevel(int probability, int impact) {
    if (probability < 1 || probability > 5 || impact < 1 || impact > 5) {
      throw new IllegalArgumentException("风险概率和影响必须在 1 到 5 之间");
    }
    int score = probability * impact;
    return score >= 15 ? "RED" : score >= 8 ? "YELLOW" : "GREEN";
  }

  private String normalizeGateMode(String value) {
    if (!"BLOCK".equals(value) && !"WARNING".equals(value)) {
      throw new IllegalArgumentException("门禁模式只能是 BLOCK 或 WARNING");
    }
    return value;
  }

  private void activity(
      long projectId, Long actorUserId, String action, String summary, String details) {
    jdbc.update("insert into project_activity(project_id,actor_user_id,action,summary,details_text) "
        + "values (?,?,?,?,?)", projectId, actorUserId, action, summary, details);
  }

  private long insert(String table, Map<String, Object> values) {
    String[] columns = values.keySet().toArray(new String[values.size()]);
    return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(columns)
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(values).longValue();
  }

  private Map<String, Object> findById(List<Map<String, Object>> values, long id) {
    for (Map<String, Object> value : values) {
      if (((Number) value.get("id")).longValue() == id) return value;
    }
    throw new NotFoundException("记录不存在");
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  private Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
  private LocalDate localDate(Date value) { return value == null ? null : value.toLocalDate(); }
}
