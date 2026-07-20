package com.zhilu.delivery.requirement;

import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.ProjectService;
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
@RequestMapping("/api/v1/requirements")
public class RequirementController {
  private final RequirementService requirements;
  private final ProjectService projects;
  private final RequirementFeatureService features;
  private final RequirementDocumentService documents;
  public RequirementController(RequirementService requirements, ProjectService projects,
      RequirementFeatureService features, RequirementDocumentService documents) {
    this.requirements = requirements; this.projects = projects; this.features = features;
    this.documents = documents;
  }

  @GetMapping public List<Map<String, Object>> list(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal CurrentUser user) {
    return requirements.list(user, projectId, keyword, status);
  }
  @GetMapping("/funnel") public Map<String, Object> funnel(@AuthenticationPrincipal CurrentUser user) { return requirements.funnel(user); }
  @GetMapping("/{id}") public Map<String, Object> get(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) { Map<String,Object> value=requirements.get(id); projects.get(((Number)value.get("projectId")).longValue(),user); return value; }
  @GetMapping("/{id}/document") public DocumentView document(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = get(id, user);
    return documents.read(id, ((Number) value.get("organizationId")).longValue());
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    projects.get(request.projectId, user); return requirements.collect(request.projectId,request.title,request.description,request.source,request.priority,user.getId());
  }
  @PutMapping("/{id}") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody SaveRequest request,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.update(id,request.title,request.description,request.source,request.priority,request.version); }
  @PostMapping("/{id}/classify") public Map<String,Object> classify(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.classify(id,user.getId()); }
  @PostMapping("/{id}/confirm") public Map<String,Object> confirm(@PathVariable long id,@Valid @RequestBody ConfirmRequest request,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.confirm(id,request.level,request.overrideReason,user.getId()); }
  @PostMapping("/{id}/duplicates") public List<Map<String,Object>> duplicates(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.findDuplicates(id); }
  @PostMapping("/{sourceId}/merge/{targetId}") public Map<String,Object> merge(@PathVariable long sourceId,@PathVariable long targetId,@AuthenticationPrincipal CurrentUser user){ get(sourceId,user); get(targetId,user); return requirements.merge(sourceId,targetId,user.getId()); }
  @GetMapping("/{id}/product-features") public Map<String,Object> coverage(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){ get(id,user); return features.coverage(id,user); }
  @PutMapping("/{id}/product-features") public Map<String,Object> replaceCoverage(@PathVariable long id,@Valid @RequestBody CoverageRequest request,@AuthenticationPrincipal CurrentUser user){ get(id,user); java.util.ArrayList<RequirementFeatureService.CoverageEntry> entries=new java.util.ArrayList<RequirementFeatureService.CoverageEntry>(); for(CoverageItem item:request.entries){ if(item==null)throw new IllegalArgumentException("功能覆盖项不能为空"); entries.add(new RequirementFeatureService.CoverageEntry(item.featureId,item.coverageType)); } return features.replaceCoverage(id,user,entries); }

  public static final class SaveRequest { @NotNull public Long projectId; @NotBlank public String title; @NotBlank public String description; public String source; public String priority="P2"; public long version; }
  public static final class ConfirmRequest { @NotBlank public String level; public String overrideReason; }
  public static final class CoverageRequest { @Valid @NotNull public List<CoverageItem> entries; }
  public static final class CoverageItem { @NotNull public Long featureId; @NotBlank public String coverageType; }
}
