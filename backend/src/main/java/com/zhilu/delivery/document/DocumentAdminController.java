package com.zhilu.delivery.document;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/document-center")
public class DocumentAdminController {
  private final DocumentMigrationService migrations;

  public DocumentAdminController(DocumentMigrationService migrations) {
    this.migrations = migrations;
  }

  @GetMapping("/status")
  public Map<String, Object> status(@AuthenticationPrincipal CurrentUser user) {
    return migrations.status(user.getOrganizationId());
  }

  @PostMapping("/initialize")
  public Map<String, Object> initialize(@AuthenticationPrincipal CurrentUser user) {
    return migrations.initialize(user.getOrganizationId());
  }

  @PostMapping("/migrate-knowledge")
  public Map<String, Object> migrateKnowledge(@AuthenticationPrincipal CurrentUser user) {
    return migrations.startKnowledgeMigration(user.getOrganizationId());
  }

  @PostMapping("/migrate-projects")
  public Map<String, Object> migrateProjects(@AuthenticationPrincipal CurrentUser user) {
    return migrations.startProjectMigration(user.getOrganizationId());
  }

  @PostMapping("/jobs/{id}/retry")
  public Map<String, Object> retry(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    return migrations.retry(user.getOrganizationId(), id);
  }
}
