package com.zhilu.delivery.document;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/document-center")
public class DocumentAdminController {
  private final DocumentMigrationService migrations;
  private final OutlineConfigurationService configurations;
  private final OutlineClient outline;
  private final AuditService audit;

  public DocumentAdminController(
      DocumentMigrationService migrations, OutlineConfigurationService configurations,
      OutlineClient outline, AuditService audit) {
    this.migrations = migrations;
    this.configurations = configurations;
    this.outline = outline;
    this.audit = audit;
  }

  @GetMapping("/status")
  public Map<String, Object> status(@AuthenticationPrincipal CurrentUser user) {
    return migrations.status(user.getOrganizationId());
  }

  @GetMapping("/config")
  public Map<String, Object> configuration(
      @AuthenticationPrincipal CurrentUser user) {
    return configurations.view(user.getOrganizationId());
  }

  @PostMapping("/config/test")
  public Map<String, Object> testConfiguration(
      @Valid @RequestBody OutlineConfigurationRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    request.rejectUnknownFields();
    OutlineConfigurationDraft draft = configurations.draft(
        user.getOrganizationId(), request.baseUrl, request.publicBaseUrl,
        request.apiToken, request.collectionId);
    OutlineCollection collection = outline.testConnection(
        draft.getConnection(), draft.getCollectionReference());
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("status", "READY");
    result.put("collectionId", collection.getId());
    result.put("collectionName", collection.getName());
    audit(user, "TEST", "OUTLINE_CONFIGURATION", null,
        "连接测试成功 · " + collection.getId());
    return result;
  }

  @PutMapping("/config")
  public Map<String, Object> saveConfiguration(
      @Valid @RequestBody OutlineConfigurationRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    request.rejectUnknownFields();
    OutlineConfigurationDraft draft = configurations.draft(
        user.getOrganizationId(), request.baseUrl, request.publicBaseUrl,
        request.apiToken, request.collectionId);
    OutlineCollection collection = outline.testConnection(
        draft.getConnection(), draft.getCollectionReference());
    Map<String, Object> value = configurations.saveValidated(
        user.getOrganizationId(), draft, collection);
    audit(user, "UPDATE", "OUTLINE_CONFIGURATION",
        String.valueOf(user.getOrganizationId()),
        "更新 Outline 配置 · collection=" + collection.getId()
            + " · tokenReplaced=" + draft.isTokenChanged());
    return value;
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

  public static final class OutlineConfigurationRequest {
    private final Set<String> unknownFields = new LinkedHashSet<String>();

    @NotBlank public String baseUrl;
    public String publicBaseUrl;
    @NotBlank public String collectionId;
    public String apiToken;

    @JsonAnySetter
    public void unknownField(String name, Object value) {
      unknownFields.add(name);
    }

    public void rejectUnknownFields() {
      if (!unknownFields.isEmpty()) {
        throw new IllegalArgumentException(
            "不支持的配置项: " + String.join(",", unknownFields));
      }
    }
  }
}
