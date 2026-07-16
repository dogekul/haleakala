package com.zhilu.delivery.document;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/document-center")
public class DocumentAdminController {
  private final DocumentMigrationService migrations;
  private final AuditService audit;

  public DocumentAdminController(DocumentMigrationService migrations, AuditService audit) {
    this.migrations = migrations;
    this.audit = audit;
  }

  @GetMapping("/status")
  public Map<String, Object> status(@AuthenticationPrincipal CurrentUser user) {
    return migrations.status(user.getOrganizationId());
  }

  @PostMapping("/initialize")
  public Map<String, Object> initialize(@AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = migrations.initialize(user.getOrganizationId());
    audit(user, "INITIALIZE", "DOCUMENT_CENTER", null, "初始化文档中心根目录");
    return value;
  }

  @PostMapping("/migrate-knowledge")
  public Map<String, Object> migrateKnowledge(@AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = migrations.startKnowledgeMigration(user.getOrganizationId());
    audit(user, "MIGRATE", "KNOWLEDGE_DOCUMENT", null, String.valueOf(value));
    return value;
  }

  @PostMapping("/migrate-projects")
  public Map<String, Object> migrateProjects(@AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = migrations.startProjectMigration(user.getOrganizationId());
    audit(user, "MIGRATE", "PROJECT_DOCUMENT", null, String.valueOf(value));
    return value;
  }

  @GetMapping("/jobs")
  public List<Map<String, Object>> jobs(
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal CurrentUser user) {
    return migrations.jobs(user.getOrganizationId(), status);
  }

  @PostMapping("/jobs/{id}/retry")
  public Map<String, Object> retry(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = migrations.retry(user.getOrganizationId(), id);
    audit(user, "RETRY", "DOCUMENT_JOB", String.valueOf(id), String.valueOf(value));
    return value;
  }

  private void audit(
      CurrentUser user, String action, String resourceType, String resourceId, String details) {
    audit.record(
        user.getOrganizationId(), user.getId(), action, resourceType, resourceId, details);
  }
}
