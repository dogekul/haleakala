package com.zhilu.delivery.standardization;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/standardization")
public class StandardizationController {
  private final StandardizationService standardization;
  private final AuditService audit;

  public StandardizationController(StandardizationService standardization, AuditService audit) {
    this.standardization = standardization;
    this.audit = audit;
  }

  @GetMapping("/baselines")
  public List<Map<String, Object>> baselines(@RequestParam(required = false) Long productVersionId) {
    return standardization.baselines(productVersionId);
  }

  @PostMapping("/baselines")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createBaseline(
      @Valid @RequestBody BaselineRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = standardization.saveBaseline(null, request.productVersionId,
        request.capabilityCode, request.capabilityName, request.dimension,
        request.scopeDescription, request.configurationOptions, request.extensionPoints,
        request.ownerUserId, 0);
    audit.record(user.getOrganizationId(), user.getId(), "CREATE", "PRODUCT_BASELINE",
        String.valueOf(value.get("id")), request.capabilityCode);
    return value;
  }

  @PutMapping("/baselines/{id}")
  public Map<String, Object> updateBaseline(
      @PathVariable long id,
      @Valid @RequestBody BaselineRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = standardization.saveBaseline(id, request.productVersionId,
        request.capabilityCode, request.capabilityName, request.dimension,
        request.scopeDescription, request.configurationOptions, request.extensionPoints,
        request.ownerUserId, request.version);
    audit.record(user.getOrganizationId(), user.getId(), "UPDATE", "PRODUCT_BASELINE",
        String.valueOf(id), request.capabilityCode);
    return value;
  }

  @PostMapping("/assessments")
  public Map<String, Object> assess(
      @RequestParam long productVersionId, @AuthenticationPrincipal CurrentUser user) {
    return standardization.assess(productVersionId, user.getId());
  }

  @GetMapping("/deviations")
  public List<Map<String, Object>> deviations(@RequestParam long productVersionId) {
    return standardization.deviations(productVersionId);
  }

  @GetMapping("/debts")
  public List<Map<String, Object>> debts(@RequestParam(required = false) Long productVersionId) {
    return standardization.debts(productVersionId);
  }

  @PostMapping("/debts/from-requirement")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createCandidateFromRequirement(
      @Valid @RequestBody CandidateRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return standardization.createCandidateFromRequirement(request.requirementId, user);
  }

  @PostMapping("/debts/{id}/convert-to-feature")
  public Map<String, Object> convertToFeature(
      @PathVariable long id,
      @Valid @RequestBody ConvertFeatureRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return standardization.convertToFeature(id, user,
        new StandardizationService.ConvertFeatureCommand(
            request.productId, request.moduleId, request.productVersionId,
            request.code, request.name, request.description, request.ownerUserId,
            request.version));
  }

  @PostMapping("/debts/evaluate")
  public List<Map<String, Object>> evaluateDebts(
      @RequestParam long productVersionId, @AuthenticationPrincipal CurrentUser user) {
    List<Map<String, Object>> values = standardization.evaluateDebts(productVersionId, user.getId());
    audit.record(user.getOrganizationId(), user.getId(), "EVALUATE", "STANDARDIZATION_DEBT",
        String.valueOf(productVersionId), "evaluated=" + values.size());
    return values;
  }

  @PutMapping("/debts/{id}/transition")
  public Map<String, Object> transitionDebt(
      @PathVariable long id,
      @Valid @RequestBody TransitionRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = standardization.transitionDebt(id, request.targetStatus,
        request.verificationNote, user.getId());
    audit.record(user.getOrganizationId(), user.getId(), "TRANSITION", "STANDARDIZATION_DEBT",
        String.valueOf(id), request.targetStatus);
    return value;
  }

  @GetMapping("/costs")
  public Map<String, Object> costs(@RequestParam long productVersionId) {
    return standardization.costs(productVersionId);
  }

  @PostMapping("/flywheel")
  public Map<String, Object> flywheel(@RequestParam long productVersionId) {
    return standardization.flywheel(productVersionId);
  }

  public static final class BaselineRequest {
    @NotNull public Long productVersionId;
    @NotBlank public String capabilityCode;
    @NotBlank public String capabilityName;
    @NotBlank public String dimension;
    @NotBlank public String scopeDescription;
    public String configurationOptions;
    public String extensionPoints;
    public Long ownerUserId;
    public long version;
  }

  public static final class TransitionRequest {
    @NotBlank public String targetStatus;
    public String verificationNote;
  }

  public static final class CandidateRequest {
    @NotNull public Long requirementId;
  }

  public static final class ConvertFeatureRequest {
    @NotNull public Long productId;
    @NotNull public Long moduleId;
    public Long productVersionId;
    @NotBlank public String code;
    @NotBlank public String name;
    public String description;
    public Long ownerUserId;
    @NotNull public Long version;
  }
}
