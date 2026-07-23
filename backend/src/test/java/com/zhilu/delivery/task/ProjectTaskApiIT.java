package com.zhilu.delivery.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.CreateProjectCommand;
import com.zhilu.delivery.project.ProjectService;
import com.zhilu.delivery.project.ProjectView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:project-task;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
class ProjectTaskApiIT {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  @Autowired private ProjectTaskService tasks;

  private long projectId;
  private CurrentUser manager;
  private CurrentUser member;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "project_task_reminder", "project_task_check_item", "project_task",
        "project_activity", "project_artifact", "template_instance", "milestone",
        "project_risk", "stage_instance", "project_member", "delivery_project",
        "document_job", "customer", "product_version", "product", "app_user",
        "organization"
    }) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

    jdbc.update("insert into organization(id,name,code) values (720,'智鹿','ZHILU-TASK')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(720,720,'manager','项目经理','ACTIVE'),"
        + "(721,720,'member','项目成员','ACTIVE'),"
        + "(722,720,'other','其他成员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (720,720,'TASK-PRODUCT','任务产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (720,720,'V1','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (720,720,'任务客户','ACTIVE')");

    manager = user(720, "DELIVERY_MANAGER", "project:write");
    member = user(721, "DELIVERY_ENGINEER", "project:write");
    ProjectView project = projects.create(new CreateProjectCommand(
        720, "任务管理项目", 720, 720, 720, 720, 720,
        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK"));
    projectId = project.getId();
    projects.addMember(projectId, 721, "ENGINEER", 100, manager);
    projects.addMember(projectId, 722, "ENGINEER", 100, manager);
  }

  @Test
  void createsTaskWithCurrentUserAndNoDeadline() {
    Map<String, Object> task = tasks.create(projectId,
        new ProjectTaskService.CreateCommand("联系客户确认范围", null, null), member);

    assertThat(task.get("title")).isEqualTo("联系客户确认范围");
    assertThat(task.get("assigneeUserId")).isEqualTo(member.getId());
    assertThat(task.get("dueAt")).isNull();
    assertThat(jdbc.queryForObject(
        "select count(*) from project_task_reminder where task_id=?",
        Integer.class, task.get("id"))).isZero();
  }

  @Test
  void truncatesDeadlineToHourAndCreatesDefaultReminder() {
    Map<String, Object> task = tasks.create(projectId,
        new ProjectTaskService.CreateCommand("准备周报", 721L,
            LocalDateTime.of(2026, 7, 25, 18, 47)), member);

    assertThat(task.get("dueAt")).isEqualTo(LocalDateTime.of(2026, 7, 25, 18, 0));
    assertThat(task.get("reminderAt"))
        .isEqualTo(LocalDateTime.of(2026, 7, 25, 17, 30));
  }

  @Test
  void completionCanBeReopenedAndCompletedTaskAcceptsDeadlineWithoutDefaultReminder() {
    Map<String, Object> created = tasks.create(projectId,
        new ProjectTaskService.CreateCommand("整理验收材料", null, null), member);
    long taskId = ((Number) created.get("id")).longValue();

    Map<String, Object> completed = tasks.complete(projectId, taskId, member);
    assertThat(completed.get("status")).isEqualTo("DONE");
    assertThat(completed.get("completedByUserId")).isEqualTo(member.getId());

    ProjectTaskService.UpdateCommand update = new ProjectTaskService.UpdateCommand(
        "整理验收材料", "已经整理完成", "NORMAL", 721,
        LocalDateTime.of(2026, 7, 25, 20, 35), null, null,
        false, null, ((Number) completed.get("version")).longValue(),
        Collections.<ProjectTaskService.CheckItemCommand>emptyList());
    Map<String, Object> supplemented = tasks.update(projectId, taskId, update, member);
    assertThat(supplemented.get("dueAt"))
        .isEqualTo(LocalDateTime.of(2026, 7, 25, 20, 0));
    assertThat(supplemented.get("reminderAt")).isNull();

    Map<String, Object> reopened = tasks.reopen(projectId, taskId, member);
    assertThat(reopened.get("status")).isEqualTo("TODO");
    assertThat(reopened.get("completedAt")).isNull();
  }

  @Test
  void replacesChecklistAndRejectsStaleVersion() {
    Map<String, Object> created = tasks.create(projectId,
        new ProjectTaskService.CreateCommand("完成切换检查", null, null), member);
    long taskId = ((Number) created.get("id")).longValue();
    long version = ((Number) created.get("version")).longValue();
    List<ProjectTaskService.CheckItemCommand> checklist = Arrays.asList(
        new ProjectTaskService.CheckItemCommand("备份数据库", true, 1),
        new ProjectTaskService.CheckItemCommand("通知业务人员", false, 2));

    ProjectTaskService.UpdateCommand update = new ProjectTaskService.UpdateCommand(
        "完成切换检查", "上线前逐项确认", "HIGH", 721, null,
        "GO_LIVE", null, false, null, version, checklist);
    Map<String, Object> saved = tasks.update(projectId, taskId, update, member);

    assertThat(saved.get("checklistTotal")).isEqualTo(2);
    assertThat(saved.get("checklistCompleted")).isEqualTo(1);
    assertThatThrownBy(() -> tasks.update(projectId, taskId, update, member))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("任务已被其他成员更新");
  }

  private CurrentUser user(long id, String role, String permission) {
    return new CurrentUser(id, 720L, "actor-" + id, "Actor " + id,
        Collections.singletonList(role), Collections.singletonList(permission));
  }
}
