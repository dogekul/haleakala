package com.zhilu.delivery.task;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTaskService {
  private final JdbcTemplate jdbc;

  public ProjectTaskService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(long projectId, String filter, CurrentUser user) {
    assertProjectAccess(projectId, user);
    String normalized = filter == null ? "mine" : filter;
    String condition;
    List<Object> arguments = new ArrayList<Object>();
    arguments.add(projectId);
    if ("mine".equals(normalized)) {
      condition = " and t.status='TODO' and t.assignee_user_id=?";
      arguments.add(user.getId());
    } else if ("all".equals(normalized)) {
      condition = " and t.status='TODO'";
    } else if ("today".equals(normalized)) {
      condition = " and t.status='TODO' and t.due_at>=? and t.due_at<?";
      LocalDate today = LocalDate.now();
      arguments.add(Timestamp.valueOf(today.atStartOfDay()));
      arguments.add(Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
    } else if ("overdue".equals(normalized)) {
      condition = " and t.status='TODO' and t.due_at<?";
      arguments.add(timestamp(hour(LocalDateTime.now())));
    } else if ("completed".equals(normalized)) {
      condition = " and t.status='DONE'";
    } else {
      throw new IllegalArgumentException("任务筛选条件不受支持");
    }
    return jdbc.query(taskSelect() + " where t.project_id=? and t.deleted=false" + condition
            + " order by case when t.due_at is null then 1 else 0 end,t.due_at,t.id desc",
        (row, index) -> task(row, user, false), arguments.toArray());
  }

  public Map<String, Object> get(long projectId, long taskId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    List<Map<String, Object>> values = jdbc.query(
        taskSelect() + " where t.project_id=? and t.id=? and t.deleted=false",
        (row, index) -> task(row, user, true), projectId, taskId);
    if (values.isEmpty()) throw new NotFoundException("任务不存在");
    return values.get(0);
  }

  @Transactional
  public Map<String, Object> create(long projectId, CreateCommand command, CurrentUser user) {
    Map<String, Object> project = assertProjectAccess(projectId, user);
    long assigneeId = command.assigneeUserId == null ? user.getId() : command.assigneeUserId;
    assertProjectMember(projectId, assigneeId);
    LocalDateTime dueAt = hour(command.dueAt);

    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", project.get("organization_id"));
    values.put("project_id", projectId);
    values.put("title", title(command.title));
    values.put("status", "TODO");
    values.put("priority", "NORMAL");
    values.put("creator_user_id", user.getId());
    values.put("assignee_user_id", assigneeId);
    values.put("due_at", timestamp(dueAt));
    long taskId = insert("project_task", values);
    if (dueAt != null) {
      saveReminder(taskId, assigneeId, dueAt.minusMinutes(30));
    }
    return get(projectId, taskId, user);
  }

  @Transactional
  public Map<String, Object> update(
      long projectId, long taskId, UpdateCommand command, CurrentUser user) {
    assertProjectAccess(projectId, user);
    Map<String, Object> current = requiredTask(projectId, taskId);
    assertCanEdit(current, user);
    assertProjectMember(projectId, command.assigneeUserId);
    assertStage(projectId, command.stageCode);
    assertMilestone(projectId, command.milestoneId);
    String priority = priority(command.priority);
    LocalDateTime dueAt = hour(command.dueAt);

    int changed = jdbc.update(
        "update project_task set title=?,description=?,priority=?,assignee_user_id=?,"
            + "due_at=?,stage_code=?,milestone_id=?,updated_at=current_timestamp,"
            + "version=version+1 where id=? and project_id=? and version=? and deleted=false",
        title(command.title), blankToNull(command.description), priority, command.assigneeUserId,
        timestamp(dueAt), blankToNull(command.stageCode), command.milestoneId,
        taskId, projectId, command.version);
    if (changed == 0) {
      throw new ConflictException("任务已被其他成员更新，请刷新后重试");
    }

    replaceChecklist(taskId, command.checklist);
    if (!command.reminderEnabled) {
      jdbc.update("delete from project_task_reminder where task_id=? and channel='IN_APP'", taskId);
    } else {
      LocalDateTime remindAt = command.reminderAt;
      if (remindAt == null) {
        if (dueAt == null) throw new IllegalArgumentException("请先设置截止时间");
        remindAt = dueAt.minusMinutes(30);
      }
      saveReminder(taskId, command.assigneeUserId, remindAt.withSecond(0).withNano(0));
    }
    return get(projectId, taskId, user);
  }

  @Transactional
  public Map<String, Object> complete(long projectId, long taskId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    Map<String, Object> current = requiredTask(projectId, taskId);
    assertCanEdit(current, user);
    if ("DONE".equals(current.get("status"))) return get(projectId, taskId, user);
    jdbc.update("update project_task set status='DONE',completed_by_user_id=?,"
            + "completed_at=current_timestamp,updated_at=current_timestamp,version=version+1 "
            + "where id=? and project_id=? and deleted=false",
        user.getId(), taskId, projectId);
    jdbc.update("delete from project_task_reminder where task_id=?", taskId);
    return get(projectId, taskId, user);
  }

  @Transactional
  public Map<String, Object> reopen(long projectId, long taskId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    Map<String, Object> current = requiredTask(projectId, taskId);
    assertCanEdit(current, user);
    if ("TODO".equals(current.get("status"))) return get(projectId, taskId, user);
    jdbc.update("update project_task set status='TODO',completed_by_user_id=null,"
            + "completed_at=null,updated_at=current_timestamp,version=version+1 "
            + "where id=? and project_id=? and deleted=false",
        taskId, projectId);
    LocalDateTime dueAt = localDateTime((Timestamp) current.get("due_at"));
    if (dueAt != null) {
      saveReminder(taskId, ((Number) current.get("assignee_user_id")).longValue(),
          dueAt.minusMinutes(30));
    }
    return get(projectId, taskId, user);
  }

  @Transactional
  public void delete(long projectId, long taskId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    Map<String, Object> current = requiredTask(projectId, taskId);
    assertCanDelete(current, user);
    jdbc.update("update project_task set deleted=true,deleted_by_user_id=?,"
            + "deleted_at=current_timestamp,updated_at=current_timestamp,version=version+1 "
            + "where id=? and project_id=? and deleted=false",
        user.getId(), taskId, projectId);
    jdbc.update("delete from project_task_reminder where task_id=?", taskId);
  }

  public List<Map<String, Object>> unreadReminders(CurrentUser user) {
    return jdbc.query(
        "select r.id,r.task_id,r.remind_at,t.project_id,t.title,t.due_at,p.name project_name "
            + "from project_task_reminder r join project_task t on t.id=r.task_id "
            + "join delivery_project p on p.id=t.project_id "
            + "where r.recipient_user_id=? and r.channel='IN_APP' and r.read_at is null "
            + "and r.remind_at<=current_timestamp and t.deleted=false and t.status='TODO' "
            + "order by r.remind_at,r.id",
        (row, index) -> map(
            "id", row.getLong("id"),
            "taskId", row.getLong("task_id"),
            "projectId", row.getLong("project_id"),
            "projectName", row.getString("project_name"),
            "taskTitle", row.getString("title"),
            "dueAt", localDateTime(row.getTimestamp("due_at")),
            "remindAt", localDateTime(row.getTimestamp("remind_at"))),
        user.getId());
  }

  public void markReminderRead(long reminderId, CurrentUser user) {
    int changed = jdbc.update(
        "update project_task_reminder set read_at=current_timestamp,"
            + "updated_at=current_timestamp where id=? and recipient_user_id=? and read_at is null",
        reminderId, user.getId());
    if (changed == 0) throw new NotFoundException("提醒不存在");
  }

  private String taskSelect() {
    return "select t.*,p.manager_user_id,a.display_name assignee_name,"
        + "c.display_name creator_name,m.name milestone_name,r.id reminder_id,"
        + "r.remind_at reminder_at,"
        + "(select count(*) from project_task_check_item ci where ci.task_id=t.id) "
        + "checklist_total,"
        + "(select count(*) from project_task_check_item ci where ci.task_id=t.id "
        + "and ci.completed=true) checklist_completed "
        + "from project_task t join delivery_project p on p.id=t.project_id "
        + "join app_user a on a.id=t.assignee_user_id "
        + "join app_user c on c.id=t.creator_user_id "
        + "left join milestone m on m.id=t.milestone_id "
        + "left join project_task_reminder r on r.task_id=t.id and r.channel='IN_APP'";
  }

  private Map<String, Object> task(ResultSet row, CurrentUser user, boolean withChecklist)
      throws SQLException {
    long taskId = row.getLong("id");
    long creatorId = row.getLong("creator_user_id");
    long assigneeId = row.getLong("assignee_user_id");
    long managerId = row.getLong("manager_user_id");
    Map<String, Object> value = map(
        "id", taskId,
        "projectId", row.getLong("project_id"),
        "title", row.getString("title"),
        "description", row.getString("description"),
        "status", row.getString("status"),
        "priority", row.getString("priority"),
        "creatorUserId", creatorId,
        "creatorName", row.getString("creator_name"),
        "assigneeUserId", assigneeId,
        "assigneeName", row.getString("assignee_name"),
        "dueAt", localDateTime(row.getTimestamp("due_at")),
        "stageCode", row.getString("stage_code"),
        "milestoneId", nullableLong(row, "milestone_id"),
        "milestoneName", row.getString("milestone_name"),
        "completedByUserId", nullableLong(row, "completed_by_user_id"),
        "completedAt", localDateTime(row.getTimestamp("completed_at")),
        "reminderId", nullableLong(row, "reminder_id"),
        "reminderAt", localDateTime(row.getTimestamp("reminder_at")),
        "reminderEnabled", row.getObject("reminder_id") != null,
        "checklistTotal", row.getInt("checklist_total"),
        "checklistCompleted", row.getInt("checklist_completed"),
        "version", row.getLong("version"),
        "canEdit", canEdit(user, creatorId, assigneeId, managerId),
        "canDelete", canDelete(user, creatorId, managerId));
    value.put("checklist", withChecklist ? checklist(taskId) : Collections.emptyList());
    return value;
  }

  private List<Map<String, Object>> checklist(long taskId) {
    return jdbc.query("select id,content,completed,sort_order from project_task_check_item "
            + "where task_id=? order by sort_order,id",
        (row, index) -> map(
            "id", row.getLong("id"),
            "content", row.getString("content"),
            "completed", row.getBoolean("completed"),
            "sortOrder", row.getInt("sort_order")), taskId);
  }

  private void replaceChecklist(long taskId, List<CheckItemCommand> checklist) {
    jdbc.update("delete from project_task_check_item where task_id=?", taskId);
    int index = 0;
    for (CheckItemCommand item : checklist == null
        ? Collections.<CheckItemCommand>emptyList() : checklist) {
      String content = item.content == null ? "" : item.content.trim();
      if (content.isEmpty()) throw new IllegalArgumentException("检查项内容不能为空");
      if (content.length() > 500) throw new IllegalArgumentException("检查项不能超过500个字符");
      jdbc.update("insert into project_task_check_item(task_id,content,completed,sort_order) "
              + "values (?,?,?,?)",
          taskId, content, item.completed, item.sortOrder == null ? ++index : item.sortOrder);
    }
  }

  private void saveReminder(long taskId, long recipientUserId, LocalDateTime remindAt) {
    Integer count = jdbc.queryForObject(
        "select count(*) from project_task_reminder where task_id=? and channel='IN_APP'",
        Integer.class, taskId);
    if (count != null && count > 0) {
      jdbc.update("update project_task_reminder set recipient_user_id=?,remind_at=?,"
              + "read_at=null,updated_at=current_timestamp where task_id=? and channel='IN_APP'",
          recipientUserId, timestamp(remindAt), taskId);
      return;
    }
    jdbc.update("insert into project_task_reminder(task_id,recipient_user_id,channel,remind_at) "
        + "values (?,?,'IN_APP',?)", taskId, recipientUserId, timestamp(remindAt));
  }

  private Map<String, Object> assertProjectAccess(long projectId, CurrentUser user) {
    List<Map<String, Object>> projects = jdbc.queryForList(
        "select id,organization_id,manager_user_id from delivery_project "
            + "where id=? and organization_id=?",
        projectId, user.getOrganizationId());
    if (projects.isEmpty()) throw new NotFoundException("项目不存在或无权访问");
    if (hasCrossProjectScope(user)) return projects.get(0);
    Integer members = jdbc.queryForObject(
        "select count(*) from project_member where project_id=? and user_id=?",
        Integer.class, projectId, user.getId());
    if (members == null || members == 0) throw new NotFoundException("项目不存在或无权访问");
    return projects.get(0);
  }

  private void assertProjectMember(long projectId, long userId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from project_member pm join app_user u on u.id=pm.user_id "
            + "where pm.project_id=? and pm.user_id=? and u.status='ACTIVE'",
        Integer.class, projectId, userId);
    if (count == null || count == 0) throw new IllegalArgumentException("负责人必须是当前项目成员");
  }

  private void assertStage(long projectId, String stageCode) {
    if (blankToNull(stageCode) == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from stage_instance where project_id=? and stage_code=?",
        Integer.class, projectId, stageCode);
    if (count == null || count == 0) throw new IllegalArgumentException("项目阶段不存在");
  }

  private void assertMilestone(long projectId, Long milestoneId) {
    if (milestoneId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from milestone where project_id=? and id=?",
        Integer.class, projectId, milestoneId);
    if (count == null || count == 0) throw new IllegalArgumentException("项目里程碑不存在");
  }

  private Map<String, Object> requiredTask(long projectId, long taskId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select t.*,p.manager_user_id from project_task t "
            + "join delivery_project p on p.id=t.project_id "
            + "where t.project_id=? and t.id=? and t.deleted=false",
        projectId, taskId);
    if (values.isEmpty()) throw new NotFoundException("任务不存在");
    return values.get(0);
  }

  private void assertCanEdit(Map<String, Object> task, CurrentUser user) {
    if (!canEdit(user,
        ((Number) task.get("creator_user_id")).longValue(),
        ((Number) task.get("assignee_user_id")).longValue(),
        ((Number) task.get("manager_user_id")).longValue())) {
      throw new AccessDeniedException("只有创建人、负责人或项目经理可以修改任务");
    }
  }

  private void assertCanDelete(Map<String, Object> task, CurrentUser user) {
    if (!canDelete(user,
        ((Number) task.get("creator_user_id")).longValue(),
        ((Number) task.get("manager_user_id")).longValue())) {
      throw new AccessDeniedException("只有创建人或项目经理可以删除任务");
    }
  }

  private boolean canEdit(
      CurrentUser user, long creatorId, long assigneeId, long managerId) {
    return hasCrossProjectScope(user) || user.getId() == creatorId
        || user.getId() == assigneeId || user.getId() == managerId;
  }

  private boolean canDelete(CurrentUser user, long creatorId, long managerId) {
    return hasCrossProjectScope(user) || user.getId() == creatorId || user.getId() == managerId;
  }

  private boolean hasCrossProjectScope(CurrentUser user) {
    return user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO");
  }

  private String title(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) throw new IllegalArgumentException("任务标题不能为空");
    if (normalized.length() > 240) throw new IllegalArgumentException("任务标题不能超过240个字符");
    return normalized;
  }

  private String priority(String value) {
    if (!"LOW".equals(value) && !"NORMAL".equals(value) && !"HIGH".equals(value)) {
      throw new IllegalArgumentException("任务优先级不受支持");
    }
    return value;
  }

  private String blankToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private LocalDateTime hour(LocalDateTime value) {
    return value == null ? null : value.withMinute(0).withSecond(0).withNano(0);
  }

  private Timestamp timestamp(LocalDateTime value) {
    return value == null ? null : Timestamp.valueOf(value);
  }

  private LocalDateTime localDateTime(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  private Long nullableLong(ResultSet row, String column) throws SQLException {
    Object value = row.getObject(column);
    return value == null ? null : ((Number) value).longValue();
  }

  private long insert(String table, Map<String, Object> values) {
    String[] columns = values.keySet().toArray(new String[values.size()]);
    return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(columns)
        .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  public static final class CreateCommand {
    public final String title;
    public final Long assigneeUserId;
    public final LocalDateTime dueAt;

    public CreateCommand(String title, Long assigneeUserId, LocalDateTime dueAt) {
      this.title = title;
      this.assigneeUserId = assigneeUserId;
      this.dueAt = dueAt;
    }
  }

  public static final class UpdateCommand {
    public final String title;
    public final String description;
    public final String priority;
    public final long assigneeUserId;
    public final LocalDateTime dueAt;
    public final String stageCode;
    public final Long milestoneId;
    public final boolean reminderEnabled;
    public final LocalDateTime reminderAt;
    public final long version;
    public final List<CheckItemCommand> checklist;

    public UpdateCommand(
        String title,
        String description,
        String priority,
        long assigneeUserId,
        LocalDateTime dueAt,
        String stageCode,
        Long milestoneId,
        boolean reminderEnabled,
        LocalDateTime reminderAt,
        long version,
        List<CheckItemCommand> checklist) {
      this.title = title;
      this.description = description;
      this.priority = priority;
      this.assigneeUserId = assigneeUserId;
      this.dueAt = dueAt;
      this.stageCode = stageCode;
      this.milestoneId = milestoneId;
      this.reminderEnabled = reminderEnabled;
      this.reminderAt = reminderAt;
      this.version = version;
      this.checklist = checklist;
    }
  }

  public static final class CheckItemCommand {
    public final String content;
    public final boolean completed;
    public final Integer sortOrder;

    public CheckItemCommand(String content, boolean completed, Integer sortOrder) {
      this.content = content;
      this.completed = completed;
      this.sortOrder = sortOrder;
    }
  }
}
