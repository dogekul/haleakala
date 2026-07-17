package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.document.DocumentExportService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
  private final OpportunityService opportunities;
  private final OpportunityResearchReportService researchReports;
  private final DocumentExportService exports;
  private final AuditService audit;

  public OpportunityController(
      OpportunityService opportunities, OpportunityResearchReportService researchReports,
      DocumentExportService exports, AuditService audit) {
    this.opportunities = opportunities;
    this.researchReports = researchReports;
    this.exports = exports;
    this.audit = audit;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) Long commercialOwnerUserId,
      @RequestParam(required = false) Long solutionOwnerUserId,
      @RequestParam(required = false) Long projectManagerUserId,
      @RequestParam(required = false) Long operationOwnerUserId,
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal CurrentUser user) {
    return opportunities.list(user.getOrganizationId(), keyword, customerId, productId,
        commercialOwnerUserId, solutionOwnerUserId, projectManagerUserId,
        operationOwnerUserId, stage, status);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    return opportunities.get(user.getOrganizationId(), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody OpportunityService.Input request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.create(
        user.getOrganizationId(), user.getId(), request);
    record(user, "CREATE", value.get("id"), request.title);
    return value;
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable long id,
      @Valid @RequestBody UpdateOpportunityRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.update(
        user.getOrganizationId(), id, request.version.longValue(), request);
    record(user, "UPDATE", id, request.title);
    return value;
  }

  @PostMapping("/{id}/advance")
  @Transactional
  public Map<String, Object> advance(@PathVariable long id,
      @Valid @RequestBody AdvanceRequest request, @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.advance(
        user.getOrganizationId(), id, request.version.longValue(), request.decision);
    record(user, "ADVANCE", id, value.get("stage") + " · " + value.get("status"));
    return value;
  }

  @GetMapping("/{id}/activities")
  public List<Map<String, Object>> activities(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    return opportunities.activities(user.getOrganizationId(), id);
  }

  @PostMapping("/{id}/activities")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> addActivity(@PathVariable long id,
      @Valid @RequestBody CreateActivityRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.addActivity(
        user.getOrganizationId(), id, user.getId(), request.title, request.sortOrder);
    audit.record(user.getOrganizationId(), user.getId(), "CREATE", "OPPORTUNITY_ACTIVITY",
        String.valueOf(value.get("id")), request.title);
    return value;
  }

  @PutMapping("/{id}/activities/{activityId}")
  @Transactional
  public Map<String, Object> updateActivity(@PathVariable long id, @PathVariable long activityId,
      @Valid @RequestBody UpdateActivityRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.updateActivity(user.getOrganizationId(), id,
        activityId, request.status, request.version.longValue());
    audit.record(user.getOrganizationId(), user.getId(), "UPDATE", "OPPORTUNITY_ACTIVITY",
        String.valueOf(activityId), request.status);
    return value;
  }

  @GetMapping("/{id}/artifacts")
  public List<Map<String, Object>> artifacts(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    return opportunities.artifacts(user.getOrganizationId(), id);
  }

  @PostMapping("/{id}/artifacts")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> addArtifact(@PathVariable long id,
      @Valid @RequestBody OpportunityService.ArtifactInput request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.addArtifact(
        user.getOrganizationId(), id, user.getId(), request);
    audit.record(user.getOrganizationId(), user.getId(), "CREATE", "OPPORTUNITY_ARTIFACT",
        String.valueOf(value.get("id")), request.title);
    return value;
  }

  @PostMapping("/{id}/handoff")
  @Transactional
  public Map<String, Object> handoff(@PathVariable long id,
      @Valid @RequestBody OpportunityService.HandoffInput request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = opportunities.handoff(
        user.getOrganizationId(), id, user.getId(), request);
    record(user, "HANDOFF", id, "转交项目 " + value.get("projectId"));
    return value;
  }

  @GetMapping("/{id}/full-link")
  public Map<String, Object> fullLink(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    return opportunities.fullLink(user.getOrganizationId(), id);
  }

  @PostMapping("/{id}/research-report/prepare")
  public Map<String, Object> prepareResearchReport(
      @PathVariable long id, @Valid @RequestBody PrepareResearchReportRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    OpportunityResearchReportService.PreparedReport prepared = researchReports.prepare(
        user.getOrganizationId(), id, request.version.longValue());
    Map<String, Object> result = document(prepared.getDocument());
    result.put("sourceTemplateId", prepared.getSourceTemplateId());
    result.put("sourceTemplateRevision", prepared.getSourceTemplateRevision());
    return result;
  }

  @GetMapping("/{id}/research-report")
  public DocumentView researchReport(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    return researchReports.read(user.getOrganizationId(), id);
  }

  @PutMapping("/{id}/research-report")
  public DocumentView saveResearchReport(
      @PathVariable long id, @Valid @RequestBody SaveResearchReportRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView saved = researchReports.saveDraft(
        user.getOrganizationId(), id, request.title, request.markdown,
        request.revision.longValue());
    audit.record(user.getOrganizationId(), user.getId(), "EDIT", "OPPORTUNITY_RESEARCH_REPORT",
        String.valueOf(id), request.title + " · revision " + saved.getRevision());
    return saved;
  }

  @PostMapping("/{id}/research-report/submit")
  @Transactional
  public Map<String, Object> submitResearchReport(
      @PathVariable long id, @Valid @RequestBody SubmitResearchReportRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> opportunity = researchReports.submit(
        user.getOrganizationId(), id, user.getId(), request.opportunityVersion.longValue(),
        request.title, request.markdown, request.revision.longValue());
    record(user, "ADVANCE", id, "提交需求调研报告 · " + opportunity.get("stage"));
    Map<String, Object> value = document(researchReports.read(user.getOrganizationId(), id));
    value.put("opportunity", opportunity);
    return value;
  }

  @GetMapping("/{id}/research-report/export")
  public ResponseEntity<byte[]> exportResearchReport(
      @PathVariable long id, @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView report = researchReports.read(user.getOrganizationId(), id);
    audit.record(user.getOrganizationId(), user.getId(), "EXPORT",
        "OPPORTUNITY_RESEARCH_REPORT", String.valueOf(id), format);
    DocumentExportService.Result result = exports.export(report, format);
    String fileName = safeName(report.getTitle()) + "." + result.getExtension();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"research-report." + result.getExtension()
                + "\"; filename*=UTF-8''" + encode(fileName))
        .body(result.getBytes());
  }

  private Map<String, Object> document(DocumentView document) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("linkId", document.getLinkId());
    value.put("title", document.getTitle());
    value.put("markdown", document.getMarkdown());
    value.put("renderedHtml", document.getRenderedHtml());
    value.put("revision", document.getRevision());
    value.put("updatedAt", document.getUpdatedAt());
    value.put("syncStatus", document.getSyncStatus());
    value.put("lastError", document.getLastError());
    value.put("outlineUrl", document.getOutlineUrl());
    return value;
  }

  private String safeName(String value) {
    String safe = value == null ? "需求调研报告"
        : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    return safe.isEmpty() ? "需求调研报告" : safe;
  }

  private String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private void record(CurrentUser user, String action, Object id, String details) {
    audit.record(user.getOrganizationId(), user.getId(), action, "OPPORTUNITY",
        String.valueOf(id), details);
  }

  public static final class UpdateOpportunityRequest extends OpportunityService.Input {
    @NotNull public Long version;
  }

  public static final class AdvanceRequest {
    @NotNull public Long version;
    public String decision;
  }

  public static final class PrepareResearchReportRequest {
    @NotNull public Long version;
  }

  public static class SaveResearchReportRequest {
    @NotBlank public String title;
    @NotBlank public String markdown;
    @NotNull @Min(0) public Long revision;
  }

  public static final class SubmitResearchReportRequest extends SaveResearchReportRequest {
    @NotNull public Long opportunityVersion;
  }

  public static final class CreateActivityRequest {
    @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max = 240)
    public String title;
    public int sortOrder;
  }

  public static final class UpdateActivityRequest {
    @javax.validation.constraints.NotBlank public String status;
    @NotNull public Long version;
  }
}
