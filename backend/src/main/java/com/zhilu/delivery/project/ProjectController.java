package com.zhilu.delivery.project;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
  private final ProjectService projects;

  public ProjectController(ProjectService projects) {
    this.projects = projects;
  }

  @GetMapping
  public List<ProjectView> projects(@AuthenticationPrincipal CurrentUser user) {
    return projects.list(user);
  }

  @GetMapping("/{id}")
  public ProjectView project(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    return projects.get(id, user);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectView create(
      @Valid @RequestBody CreateRequest request, @AuthenticationPrincipal CurrentUser user) {
    long managerId = request.managerUserId == null ? user.getId() : request.managerUserId;
    return projects.create(new CreateProjectCommand(
        user.getOrganizationId(), request.code, request.name, request.customerName,
        request.productId, request.productVersionId, managerId, user.getId(), request.startDate,
        request.plannedEndDate, request.gateMode));
  }

  @PostMapping("/{id}/advance")
  public ProjectView advance(
      @PathVariable long id,
      @Valid @RequestBody AdvanceRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.advanceStage(id, DeliveryStage.valueOf(request.targetStage),
        GateMode.valueOf(request.mode), user.getId());
  }

  @PutMapping("/{id}/stages/{stageCode}/gate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setGate(
      @PathVariable long id,
      @PathVariable String stageCode,
      @Valid @RequestBody GateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    projects.setGate(id, stageCode, request.status, request.message, user.getId());
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> member(
      @PathVariable long id,
      @Valid @RequestBody MemberRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.addMember(id, request.userId, request.projectRole,
        request.allocationPercent, user.getId());
  }

  @PostMapping("/{id}/risks")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> risk(
      @PathVariable long id,
      @Valid @RequestBody RiskRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.addRisk(id, request.title, request.category, request.probability,
        request.impact, request.ownerUserId, request.mitigation, request.dueDate, user.getId());
  }

  @PutMapping("/{id}/risks/{riskId}")
  public Map<String, Object> updateRisk(
      @PathVariable long id,
      @PathVariable long riskId,
      @Valid @RequestBody RiskUpdateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.updateRisk(id, riskId, request.status, request.mitigation, user.getId());
  }

  @PostMapping("/{id}/milestones")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> milestone(
      @PathVariable long id,
      @Valid @RequestBody MilestoneRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.addMilestone(
        id, request.name, request.dueDate, request.ownerUserId, user.getId());
  }

  @PostMapping("/{id}/templates")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> template(
      @PathVariable long id,
      @Valid @RequestBody TemplateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.saveTemplate(id, null, request.templateKey, request.title,
        request.contentMarkdown, request.status, 0, user.getId());
  }

  @PutMapping("/{id}/templates/{templateId}")
  public Map<String, Object> updateTemplate(
      @PathVariable long id,
      @PathVariable long templateId,
      @Valid @RequestBody TemplateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.saveTemplate(id, templateId, request.templateKey, request.title,
        request.contentMarkdown, request.status, request.version, user.getId());
  }

  @PutMapping("/{id}/settings")
  public ProjectView settings(
      @PathVariable long id,
      @Valid @RequestBody SettingsRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return projects.updateSettings(id, request.name, request.status, request.riskLevel,
        request.gateMode, request.plannedEndDate, request.version, user.getId());
  }

  public static final class CreateRequest {
    @NotBlank public String code;
    @NotBlank public String name;
    @NotBlank public String customerName;
    @NotNull public Long productId;
    @NotNull public Long productVersionId;
    public Long managerUserId;
    public LocalDate startDate;
    public LocalDate plannedEndDate;
    @NotBlank public String gateMode = "BLOCK";
  }

  public static final class AdvanceRequest {
    @NotBlank public String targetStage;
    @NotBlank public String mode = "BLOCK";
  }

  public static final class GateRequest {
    @NotBlank public String status;
    public String message;
  }

  public static final class MemberRequest {
    @NotNull public Long userId;
    @NotBlank public String projectRole;
    @Min(1) @Max(100) public int allocationPercent = 100;
  }

  public static final class RiskRequest {
    @NotBlank public String title;
    @NotBlank public String category;
    @Min(1) @Max(5) public int probability;
    @Min(1) @Max(5) public int impact;
    public Long ownerUserId;
    public String mitigation;
    public LocalDate dueDate;
  }

  public static final class RiskUpdateRequest {
    @NotBlank public String status;
    public String mitigation;
  }

  public static final class MilestoneRequest {
    @NotBlank public String name;
    @NotNull public LocalDate dueDate;
    public Long ownerUserId;
  }

  public static final class TemplateRequest {
    @NotBlank public String templateKey;
    @NotBlank public String title;
    @NotNull public String contentMarkdown;
    @NotBlank public String status = "DRAFT";
    public long version;
  }

  public static final class SettingsRequest {
    @NotBlank public String name;
    @NotBlank public String status;
    @NotBlank public String riskLevel;
    @NotBlank public String gateMode;
    public LocalDate plannedEndDate;
    public long version;
  }
}
