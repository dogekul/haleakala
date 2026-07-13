package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.project.ProjectService;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AgentController {
  private final AgentJobService jobs;
  private final ProjectService projects;
  private final AgentSignatureVerifier signatures;
  private final ObjectMapper json;

  public AgentController(AgentJobService jobs, ProjectService projects,
      AgentSignatureVerifier signatures, ObjectMapper json) {
    this.jobs = jobs; this.projects = projects; this.signatures = signatures; this.json = json;
  }

  @GetMapping("/projects/{projectId}/agent-jobs")
  public List<AgentJobView> list(@PathVariable long projectId,
      @AuthenticationPrincipal CurrentUser user) {
    projects.get(projectId, user);
    return jobs.list(projectId);
  }

  @PostMapping("/projects/{projectId}/agent-jobs")
  @ResponseStatus(HttpStatus.CREATED)
  public AgentJobView submit(@PathVariable long projectId, @Valid @RequestBody SubmitRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal CurrentUser user) {
    projects.get(projectId, user);
    return jobs.submit(projectId, request.skill, request.scenario, idempotencyKey, user.getId());
  }

  @GetMapping("/agent-jobs/{id}")
  public AgentJobView get(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    AgentJobView job = jobs.get(id);
    projects.get(job.getProjectId(), user);
    return job;
  }

  @PostMapping("/agent-jobs/{id}/cancel")
  public AgentJobView cancel(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    AgentJobView job = jobs.get(id);
    projects.get(job.getProjectId(), user);
    return jobs.cancel(id);
  }

  @PostMapping("/integrations/agent/events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void callback(@RequestBody String body,
      @RequestHeader(value = "X-Agent-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "X-Agent-Signature", required = false) String signature) {
    if (!signatures.valid(timestamp, signature, body)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent 回调签名无效或已过期");
    }
    try {
      jobs.accept(json.readValue(body, AgentEvent.class));
    } catch (java.io.IOException malformed) {
      throw new IllegalArgumentException("Agent 回调 JSON 无效");
    }
  }

  public static final class SubmitRequest {
    @NotBlank public String skill;
    public String scenario = "normal";
  }
}
