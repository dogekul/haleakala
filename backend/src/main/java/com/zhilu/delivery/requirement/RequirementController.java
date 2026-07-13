package com.zhilu.delivery.requirement;

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
  public RequirementController(RequirementService requirements, ProjectService projects) {
    this.requirements = requirements; this.projects = projects;
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
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    projects.get(request.projectId, user); return requirements.create(request.projectId,request.title,request.description,request.source,request.priority,user.getId());
  }
  @PutMapping("/{id}") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody SaveRequest request,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.update(id,request.title,request.description,request.source,request.priority,request.version); }
  @PostMapping("/{id}/classify") public Map<String,Object> classify(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.classify(id,user.getId()); }
  @PostMapping("/{id}/confirm") public Map<String,Object> confirm(@PathVariable long id,@Valid @RequestBody ConfirmRequest request,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.confirm(id,request.level,request.overrideReason,user.getId()); }
  @PostMapping("/{id}/duplicates") public List<Map<String,Object>> duplicates(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){ get(id,user); return requirements.findDuplicates(id); }
  @PostMapping("/{sourceId}/merge/{targetId}") public Map<String,Object> merge(@PathVariable long sourceId,@PathVariable long targetId,@AuthenticationPrincipal CurrentUser user){ get(sourceId,user); get(targetId,user); return requirements.merge(sourceId,targetId,user.getId()); }

  public static final class SaveRequest { @NotNull public Long projectId; @NotBlank public String title; @NotBlank public String description; public String source; public String priority="P2"; public long version; }
  public static final class ConfirmRequest { @NotBlank public String level; public String overrideReason; }
}
