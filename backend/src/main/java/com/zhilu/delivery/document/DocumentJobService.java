package com.zhilu.delivery.document;

import com.zhilu.delivery.audit.AuditService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentJobService {
  public static final String PROJECT_INIT = "PROJECT_INIT";

  private final JdbcTemplate jdbc;
  private final ProjectDocumentService projectDocuments;
  private final DocumentMigrationService migrations;
  private final OutlineProperties properties;
  private final AuditService audit;

  public DocumentJobService(
      JdbcTemplate jdbc, ProjectDocumentService projectDocuments,
      DocumentMigrationService migrations, OutlineProperties properties, AuditService audit) {
    this.jdbc = jdbc;
    this.projectDocuments = projectDocuments;
    this.migrations = migrations;
    this.properties = properties;
    this.audit = audit;
  }

  public void enqueueProjectInitialization(long organizationId, long projectId) {
    String businessKey = "PROJECT:" + projectId;
    Integer count = jdbc.queryForObject(
        "select count(*) from document_job where organization_id=? and job_type=? "
            + "and business_key=?",
        Integer.class, organizationId, PROJECT_INIT, businessKey);
    if (count != null && count > 0) return;
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("job_type", PROJECT_INIT);
    values.put("business_key", businessKey);
    values.put("business_id", projectId);
    values.put("status", "PENDING");
    try {
      new SimpleJdbcInsert(jdbc).withTableName("document_job")
          .usingColumns(values.keySet().toArray(new String[values.size()]))
          .execute(values);
    } catch (DuplicateKeyException concurrentEnqueue) {
      // The unique business key makes concurrent project creation callbacks idempotent.
    }
  }

  @Scheduled(
      fixedDelayString = "${delivery.outline.job-scan-ms:5000}",
      initialDelayString = "${delivery.outline.job-initial-delay-ms:60000}")
  public void runDueJobs() {
    recoverStaleJobs();
    List<Long> ids = jdbc.queryForList(
        "select id from document_job where status in ('PENDING','RETRY') "
            + "and next_attempt_at<=current_timestamp order by id limit 20",
        Long.class);
    for (Long id : ids) run(id.longValue());
  }

  private void recoverStaleJobs() {
    Timestamp staleBefore = Timestamp.from(
        Instant.now().minus(properties.getStaleAfter()));
    jdbc.update("update document_job set status='RETRY',"
            + "next_attempt_at=current_timestamp,last_error='任务执行中断，已自动恢复',"
            + "updated_at=current_timestamp,version=version+1 "
            + "where status='RUNNING' and updated_at<?",
        staleBefore);
  }

  public void retryProjectInitialization(long organizationId, long projectId) {
    int changed = jdbc.update(
        "update document_job set status='PENDING',attempt_count=0,next_attempt_at=current_timestamp,"
            + "last_error=null,started_at=null,completed_at=null,updated_at=current_timestamp,"
            + "version=version+1 where organization_id=? and job_type=? and business_id=? "
            + "and status in ('FAILED','RETRY')",
        organizationId, PROJECT_INIT, projectId);
    if (changed == 0) {
      Integer existing = jdbc.queryForObject(
          "select count(*) from document_job where organization_id=? and job_type=? "
              + "and business_id=?",
          Integer.class, organizationId, PROJECT_INIT, projectId);
      if (existing != null && existing > 0) return;
      enqueueProjectInitialization(organizationId, projectId);
    }
    jdbc.update("update delivery_project set document_space_status='PENDING',"
            + "document_space_error=null,updated_at=current_timestamp where id=? "
            + "and organization_id=?",
        projectId, organizationId);
  }

  private void run(long jobId) {
    int claimed = jdbc.update(
        "update document_job set status='RUNNING',attempt_count=attempt_count+1,"
            + "started_at=current_timestamp,last_error=null,updated_at=current_timestamp,"
            + "version=version+1 where id=? and status in ('PENDING','RETRY') "
            + "and next_attempt_at<=current_timestamp",
        jobId);
    if (claimed == 0) return;
    Map<String, Object> job = jdbc.queryForMap(
        "select organization_id,job_type,business_id,attempt_count from document_job where id=?",
        jobId);
    long businessId = ((Number) job.get("business_id")).longValue();
    try {
      String jobType = String.valueOf(job.get("job_type"));
      if (PROJECT_INIT.equals(jobType)
          || DocumentMigrationService.PROJECT_MIGRATION.equals(jobType)) {
        projectDocuments.initialize(businessId);
      } else if (DocumentMigrationService.KNOWLEDGE_MIGRATION.equals(jobType)) {
        migrations.migrateKnowledge(
            ((Number) job.get("organization_id")).longValue(), businessId);
      } else {
        throw new IllegalArgumentException("不支持的文档任务类型：" + job.get("job_type"));
      }
      jdbc.update("update document_job set status='DONE',completed_at=current_timestamp,"
              + "last_error=null,updated_at=current_timestamp,version=version+1 where id=?",
          jobId);
      audit.record(
          ((Number) job.get("organization_id")).longValue(), null,
          auditAction(String.valueOf(job.get("job_type"))), "DOCUMENT_JOB",
          String.valueOf(jobId), String.valueOf(job.get("job_type")) + ":" + businessId);
    } catch (RuntimeException failure) {
      int attempts = ((Number) job.get("attempt_count")).intValue();
      String status = attempts >= properties.getMaxAttempts() ? "FAILED" : "RETRY";
      jdbc.update("update document_job set status=?,next_attempt_at=?,last_error=?,"
              + "updated_at=current_timestamp,version=version+1 where id=?",
          status, nextAttempt(attempts), truncate(failure.getMessage()), jobId);
      if (PROJECT_INIT.equals(job.get("job_type"))
          || DocumentMigrationService.PROJECT_MIGRATION.equals(job.get("job_type"))) {
        projectDocuments.markFailure(businessId, failure);
      }
    }
  }

  private String auditAction(String jobType) {
    if (PROJECT_INIT.equals(jobType)) return "INITIALIZE";
    return "MIGRATE";
  }

  private Timestamp nextAttempt(int attempts) {
    long initial = properties.getInitialBackoff().toMillis();
    long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
    long delay;
    try {
      delay = Math.multiplyExact(initial, multiplier);
    } catch (ArithmeticException overflow) {
      delay = Long.MAX_VALUE / 4;
    }
    return Timestamp.from(Instant.now().plusMillis(Math.max(delay, 0)));
  }

  private String truncate(String message) {
    if (message == null) return "文档任务执行失败";
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
