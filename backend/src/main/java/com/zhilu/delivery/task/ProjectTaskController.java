package com.zhilu.delivery.task;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectTaskController {
  private final ProjectTaskService tasks;

  public ProjectTaskController(ProjectTaskService tasks) {
    this.tasks = tasks;
  }

  @GetMapping("/projects/{projectId}/tasks")
  public List<Map<String, Object>> list(
      @PathVariable long projectId,
      @RequestParam(defaultValue = "mine") String filter,
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.list(projectId, filter, user);
  }

  @PostMapping("/projects/{projectId}/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @PathVariable long projectId,
      @Valid @RequestBody CreateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.create(projectId,
        new ProjectTaskService.CreateCommand(
            request.title, request.assigneeUserId, request.dueAt),
        user);
  }

  @GetMapping("/projects/{projectId}/tasks/{taskId}")
  public Map<String, Object> get(
      @PathVariable long projectId,
      @PathVariable long taskId,
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.get(projectId, taskId, user);
  }

  @PutMapping("/projects/{projectId}/tasks/{taskId}")
  public Map<String, Object> update(
      @PathVariable long projectId,
      @PathVariable long taskId,
      @Valid @RequestBody UpdateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    List<ProjectTaskService.CheckItemCommand> checklist =
        new ArrayList<ProjectTaskService.CheckItemCommand>();
    for (CheckItemRequest item : request.checklist == null
        ? Collections.<CheckItemRequest>emptyList() : request.checklist) {
      checklist.add(new ProjectTaskService.CheckItemCommand(
          item.content, item.completed, item.sortOrder));
    }
    return tasks.update(projectId, taskId,
        new ProjectTaskService.UpdateCommand(
            request.title,
            request.description,
            request.priority,
            request.assigneeUserId,
            request.dueAt,
            request.stageCode,
            request.milestoneId,
            request.reminderEnabled,
            request.reminderAt,
            request.version,
            checklist),
        user);
  }

  @PostMapping("/projects/{projectId}/tasks/{taskId}/complete")
  public Map<String, Object> complete(
      @PathVariable long projectId,
      @PathVariable long taskId,
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.complete(projectId, taskId, user);
  }

  @PostMapping("/projects/{projectId}/tasks/{taskId}/reopen")
  public Map<String, Object> reopen(
      @PathVariable long projectId,
      @PathVariable long taskId,
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.reopen(projectId, taskId, user);
  }

  @DeleteMapping("/projects/{projectId}/tasks/{taskId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable long projectId,
      @PathVariable long taskId,
      @AuthenticationPrincipal CurrentUser user) {
    tasks.delete(projectId, taskId, user);
  }

  @GetMapping("/task-reminders/unread")
  public List<Map<String, Object>> unreadReminders(
      @AuthenticationPrincipal CurrentUser user) {
    return tasks.unreadReminders(user);
  }

  @PostMapping("/task-reminders/{reminderId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markReminderRead(
      @PathVariable long reminderId,
      @AuthenticationPrincipal CurrentUser user) {
    tasks.markReminderRead(reminderId, user);
  }

  public static final class CreateRequest {
    @NotBlank public String title;
    public Long assigneeUserId;
    public LocalDateTime dueAt;
  }

  public static final class UpdateRequest {
    @NotBlank public String title;
    public String description;
    @NotBlank public String priority;
    @NotNull public Long assigneeUserId;
    public LocalDateTime dueAt;
    public String stageCode;
    public Long milestoneId;
    @NotNull public Boolean reminderEnabled;
    public LocalDateTime reminderAt;
    @NotNull public Long version;
    @Valid public List<CheckItemRequest> checklist = Collections.emptyList();
  }

  public static final class CheckItemRequest {
    @NotBlank public String content;
    public boolean completed;
    public Integer sortOrder;
  }
}
