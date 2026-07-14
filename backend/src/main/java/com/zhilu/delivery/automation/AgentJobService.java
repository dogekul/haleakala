package com.zhilu.delivery.automation;

import com.zhilu.delivery.admin.SystemSettingService;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.storage.FileObjectView;
import com.zhilu.delivery.storage.FileService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentJobService {
  private static final int MAX_DISPATCH_ATTEMPTS = 3;
  private static final Set<String> SKILLS = Collections.unmodifiableSet(new HashSet<String>(
      Arrays.asList("deliver-init", "deliver-require", "deliver-dev", "deliver-transition",
          "deliver-standardize", "deliver-close")));
  private static final Set<String> TERMINAL = Collections.unmodifiableSet(new HashSet<String>(
      Arrays.asList("SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED")));

  private final JdbcTemplate jdbc;
  private final AgentGateway gateway;
  private final FileService files;
  private final AuditService audit;
  private final SystemSettingService settings;
  private final String callbackUrl;

  public AgentJobService(JdbcTemplate jdbc, AgentGateway gateway, FileService files,
      AuditService audit, SystemSettingService settings,
      @Value("${delivery.agent.callback-url:http://backend:8080/api/v1/integrations/agent/events}") String callbackUrl) {
    this.jdbc = jdbc; this.gateway = gateway; this.files = files; this.audit = audit;
    this.settings = settings; this.callbackUrl = callbackUrl;
  }

  @Transactional
  public AgentJobView submit(long projectId, String skill, String scenario,
      String idempotencyKey, long actorUserId) {
    validate(skill, idempotencyKey);
    List<AgentJobView> existing = jdbc.query(
        "select * from agent_job where project_id=? and idempotency_key=?", this::map,
        projectId, idempotencyKey);
    if (!existing.isEmpty()) return existing.get(0);
    Map<String, Object> project = projectContext(projectId);
    long organizationId = ((Number) project.get("organization_id")).longValue();
    LocalDateTime timeoutAt =
        LocalDateTime.now().plusMinutes(settings.agentTimeoutMinutes(organizationId));
    jdbc.update("insert into agent_job(project_id,skill_code,scenario,status,progress,"
            + "idempotency_key,created_by,timeout_at) values (?,?,?,'QUEUED',0,?,?,?)",
        projectId, skill, normalizeScenario(scenario), idempotencyKey, actorUserId,
        Timestamp.valueOf(timeoutAt));
    Long id = jdbc.queryForObject(
        "select id from agent_job where project_id=? and idempotency_key=?", Long.class,
        projectId, idempotencyKey);
    audit.record(organizationId, actorUserId, "AGENT_JOB_SUBMITTED", "AGENT_JOB",
        String.valueOf(id), skill);
    return get(id);
  }

  @Scheduled(fixedDelayString = "${delivery.agent.dispatch-scan-ms:2000}",
      initialDelayString = "${delivery.agent.dispatch-initial-delay-ms:1000}")
  public void dispatchPending() {
    recoverAbandonedClaims();
    List<Long> ids = jdbc.queryForList(
        "select id from agent_job where status='QUEUED' and external_job_id is null "
            + "and dispatch_status in ('PENDING','RETRY') "
            + "and (next_dispatch_at is null or next_dispatch_at <= current_timestamp) "
            + "order by created_at limit 20", Long.class);
    for (Long id : ids) dispatch(id.longValue());
  }

  @Scheduled(fixedDelayString = "${delivery.agent.reconcile-scan-ms:5000}",
      initialDelayString = "${delivery.agent.reconcile-initial-delay-ms:3000}")
  public void reconcileActive() {
    Timestamp before = Timestamp.valueOf(LocalDateTime.now().minusSeconds(3));
    List<Map<String, Object>> active = jdbc.queryForList(
        "select id,external_job_id from agent_job where external_job_id is not null "
            + "and status in ('QUEUED','RUNNING') "
            + "and (last_polled_at is null or last_polled_at < ?) order by updated_at limit 50",
        before);
    for (Map<String, Object> row : active) {
      long id = ((Number) row.get("id")).longValue();
      String externalId = String.valueOf(row.get("external_job_id"));
      jdbc.update("update agent_job set last_polled_at=current_timestamp where id=?", id);
      try {
        AgentEvent remote = gateway.status(externalId);
        if (remote == null || blank(remote.getStatus())) continue;
        accept(new AgentEvent("poll:" + id + ":" + remote.getStatus() + ":"
            + remote.getProgress(), externalId, remote.getStatus(), remote.getProgress(),
            remote.getError(), remote.getArtifacts()));
      } catch (RuntimeException ignored) {
        // A callback or the next reconciliation pass can still converge the durable job.
      }
    }
  }

  public List<AgentJobView> list(long projectId) {
    return jdbc.query("select * from agent_job where project_id=? order by created_at desc",
        this::map, projectId);
  }

  public AgentJobView get(long id) {
    List<AgentJobView> rows = jdbc.query("select * from agent_job where id=?", this::map, id);
    if (rows.isEmpty()) throw new NotFoundException("Agent 任务不存在");
    return rows.get(0);
  }

  @Transactional
  public void accept(AgentEvent event) {
    validateEvent(event);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select j.*,p.organization_id from agent_job j join delivery_project p on p.id=j.project_id "
            + "where j.external_job_id=? for update", event.getExternalJobId());
    if (rows.isEmpty()) throw new NotFoundException("Agent 外部任务不存在");
    Integer seen = jdbc.queryForObject(
        "select count(*) from callback_receipt where event_id=?", Integer.class, event.getEventId());
    if (seen != null && seen > 0) return;
    Map<String, Object> row = rows.get(0);
    long id = ((Number) row.get("id")).longValue();
    String current = String.valueOf(row.get("status"));
    String next = event.getStatus().toUpperCase(java.util.Locale.ROOT);
    ensureTransition(current, next);
    jdbc.update("insert into callback_receipt(event_id,agent_job_id,status) values (?,?,?)",
        event.getEventId(), id, next);
    if (TERMINAL.contains(current)) return;
    int progress = Math.max(0, Math.min(100, event.getProgress()));
    boolean terminal = TERMINAL.contains(next);
    jdbc.update("update agent_job set status=?,progress=?,error_message=?,"
            + (terminal ? "finished_at=current_timestamp," : "")
            + "updated_at=current_timestamp,version=version+1 where id=?",
        next, progress, event.getError(), id);
    if ("SUCCEEDED".equals(next)) {
      bindArtifacts(row, event.getArtifacts());
    }
    audit.record(((Number) row.get("organization_id")).longValue(), null,
        "AGENT_CALLBACK", "AGENT_JOB", String.valueOf(id), event.getEventId() + ":" + next);
  }

  @Transactional
  public AgentJobView cancel(long id) {
    AgentJobView job = get(id);
    if (TERMINAL.contains(job.getStatus())) return job;
    if (!blank(job.getExternalJobId())) gateway.cancel(job.getExternalJobId());
    jdbc.update("update agent_job set status='CANCELLED',dispatch_status='FAILED',"
        + "finished_at=current_timestamp,"
        + "updated_at=current_timestamp,version=version+1 where id=?", id);
    return get(id);
  }

  @Scheduled(fixedDelayString = "${delivery.agent.timeout-scan-ms:60000}")
  @Transactional
  public void markTimedOut() {
    jdbc.update("update agent_job set status='TIMED_OUT',error_message='任务执行超时',"
        + "finished_at=current_timestamp,updated_at=current_timestamp,version=version+1 "
        + "where status in ('QUEUED','RUNNING') and timeout_at < current_timestamp");
  }

  private void bindArtifacts(Map<String, Object> job, List<AgentArtifact> artifacts) {
    if (artifacts == null) return;
    for (AgentArtifact artifact : artifacts) {
      if (artifact == null || blank(artifact.getName()) || artifact.getContent() == null) continue;
      byte[] bytes = artifact.getContent().getBytes(StandardCharsets.UTF_8);
      String mime = blank(artifact.getMimeType()) ? "text/markdown" : artifact.getMimeType();
      long organizationId = ((Number) job.get("organization_id")).longValue();
      long actor = ((Number) job.get("created_by")).longValue();
      FileObjectView file = files.store(new ByteArrayInputStream(bytes), artifact.getName(), mime,
          bytes.length, organizationId, actor);
      jdbc.update("insert into project_artifact(project_id,stage_code,file_id,artifact_type,name) "
              + "values (?,?,?,?,?)", ((Number) job.get("project_id")).longValue(), null,
          file.getId(), blank(artifact.getArtifactType()) ? "AGENT_OUTPUT" : artifact.getArtifactType(),
          artifact.getName());
    }
  }

  private void recoverAbandonedClaims() {
    Timestamp stale = Timestamp.valueOf(LocalDateTime.now().minusMinutes(2));
    jdbc.update("update agent_job set dispatch_status='RETRY',next_dispatch_at=current_timestamp,"
        + "dispatch_claimed_at=null where status='QUEUED' and external_job_id is null "
        + "and dispatch_status='DISPATCHING' and dispatch_claimed_at < ?", stale);
  }

  private void dispatch(long id) {
    int claimed = jdbc.update("update agent_job set dispatch_status='DISPATCHING',"
        + "dispatch_attempts=dispatch_attempts+1,dispatch_claimed_at=current_timestamp,"
        + "updated_at=current_timestamp where id=? and status='QUEUED' "
        + "and external_job_id is null and dispatch_status in ('PENDING','RETRY') "
        + "and (next_dispatch_at is null or next_dispatch_at <= current_timestamp)", id);
    if (claimed == 0) return;
    Map<String, Object> job = jdbc.queryForMap(
        "select id,project_id,skill_code,scenario,dispatch_attempts from agent_job where id=?", id);
    int attempt = ((Number) job.get("dispatch_attempts")).intValue();
    try {
      long projectId = ((Number) job.get("project_id")).longValue();
      AgentSubmission submission = gateway.submit("platform-job-" + id, new AgentRequest(
          String.valueOf(job.get("skill_code")), String.valueOf(job.get("scenario")), callbackUrl,
          projectContext(projectId)));
      if (submission == null || blank(submission.getExternalJobId())) {
        throw new IllegalStateException("Agent 未返回 externalJobId");
      }
      String status = normalizeActive(submission.getStatus());
      int updated = jdbc.update("update agent_job set external_job_id=?,dispatch_status='SUBMITTED',"
              + "status=?,started_at=coalesce(started_at,current_timestamp),error_message=null,"
              + "dispatch_claimed_at=null,updated_at=current_timestamp,version=version+1 "
              + "where id=? and status='QUEUED' and dispatch_status='DISPATCHING'",
          submission.getExternalJobId(), status, id);
      jdbc.update("insert into agent_attempt(agent_job_id,attempt_no,outcome) "
          + "values (?,?,'ACCEPTED')", id, attempt);
      if (updated == 0) {
        gateway.cancel(submission.getExternalJobId());
      }
    } catch (RuntimeException error) {
      recordDispatchFailure(id, attempt, error);
    }
  }

  private void recordDispatchFailure(long id, int attempt, RuntimeException error) {
    String message = concise(error);
    jdbc.update("insert into agent_attempt(agent_job_id,attempt_no,outcome,error_message) "
        + "values (?,?,'FAILED',?)", id, attempt, message);
    if (attempt >= MAX_DISPATCH_ATTEMPTS) {
      jdbc.update("update agent_job set status='FAILED',dispatch_status='FAILED',"
          + "error_message=?,finished_at=current_timestamp,dispatch_claimed_at=null,"
          + "updated_at=current_timestamp,version=version+1 where id=?", message, id);
      return;
    }
    Timestamp retryAt = Timestamp.valueOf(LocalDateTime.now().plusSeconds(5L * attempt));
    jdbc.update("update agent_job set dispatch_status='RETRY',error_message=?,"
        + "dispatch_claimed_at=null,next_dispatch_at=?,updated_at=current_timestamp,"
        + "version=version+1 where id=?", message, retryAt, id);
  }

  private Map<String, Object> projectContext(long projectId) {
    return jdbc.queryForMap(
        "select p.organization_id,p.code,p.name,p.customer_name,p.current_stage,"
            + "pr.name product_name,pv.version_name from delivery_project p "
            + "join product pr on pr.id=p.product_id "
            + "join product_version pv on pv.id=p.product_version_id where p.id=?", projectId);
  }

  private AgentJobView map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
    Timestamp created = row.getTimestamp("created_at");
    Timestamp finished = row.getTimestamp("finished_at");
    return new AgentJobView(row.getLong("id"), row.getLong("project_id"),
        row.getString("skill_code"), row.getString("scenario"), row.getString("status"),
        row.getInt("progress"), row.getString("external_job_id"), row.getString("error_message"),
        created == null ? null : created.toLocalDateTime(),
        finished == null ? null : finished.toLocalDateTime());
  }

  private void validate(String skill, String key) {
    if (!SKILLS.contains(skill)) throw new IllegalArgumentException("不支持的 Skill: " + skill);
    if (blank(key) || key.length() > 96) throw new IllegalArgumentException("幂等键不能为空且最多 96 个字符");
  }
  private void validateEvent(AgentEvent event) {
    if (event == null || blank(event.getEventId()) || blank(event.getExternalJobId())
        || blank(event.getStatus())) throw new IllegalArgumentException("Agent 回调字段不完整");
    String status = event.getStatus().toUpperCase(java.util.Locale.ROOT);
    if (!Arrays.asList("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED").contains(status))
      throw new IllegalArgumentException("Agent 状态不受支持");
  }
  private void ensureTransition(String current, String next) {
    if (current.equals(next)) return;
    if (TERMINAL.contains(current)) throw new ConflictException("终态 Agent 任务不能回退到 " + next);
    if ("QUEUED".equals(next)) throw new ConflictException("Agent 任务不能回退到排队状态");
  }
  private String normalizeScenario(String scenario) { return blank(scenario) ? "normal" : scenario.trim(); }
  private String normalizeActive(String status) {
    return "QUEUED".equalsIgnoreCase(status) ? "QUEUED" : "RUNNING";
  }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
  private String concise(Throwable error) {
    String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    return value.length() > 1000 ? value.substring(0, 1000) : value;
  }
}
