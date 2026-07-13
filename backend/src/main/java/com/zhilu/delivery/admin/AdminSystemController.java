package com.zhilu.delivery.admin;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSystemController {
  private final AdminAuditService audits;
  private final SystemSettingService settings;
  private final AuditService audit;

  public AdminSystemController(
      AdminAuditService audits, SystemSettingService settings, AuditService audit) {
    this.audits = audits;
    this.settings = settings;
    this.audit = audit;
  }

  @GetMapping("/audit-logs")
  public Map<String, Object> auditLogs(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @AuthenticationPrincipal CurrentUser user) {
    return audits.search(user.getOrganizationId(), keyword, action, resourceType,
        from, to, page, pageSize);
  }

  @GetMapping("/audit-log-facets")
  public Map<String, Object> auditLogFacets(@AuthenticationPrincipal CurrentUser user) {
    return audits.facets(user.getOrganizationId());
  }

  @GetMapping("/settings")
  public Map<String, Object> settings(@AuthenticationPrincipal CurrentUser user) {
    return settings.get(user.getOrganizationId());
  }

  @PutMapping("/settings")
  @Transactional
  public Map<String, Object> saveSettings(
      @Valid @RequestBody SettingsRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    request.rejectUnknownFields();
    Map<String, Object> value = settings.save(user.getOrganizationId(), request.platformName,
        request.environmentLabel, request.timezone, request.supportEmail,
        request.agentTimeoutMinutes);
    audit.record(user.getOrganizationId(), user.getId(), "UPDATE", "SYSTEM_SETTING",
        String.valueOf(user.getOrganizationId()), "更新系统配置");
    return value;
  }

  public static final class SettingsRequest {
    private final Set<String> unknownFields = new LinkedHashSet<String>();
    @NotBlank public String platformName;
    @NotBlank public String environmentLabel;
    @NotBlank public String timezone;
    public String supportEmail;
    @Min(1) @Max(240) public long agentTimeoutMinutes;

    @JsonAnySetter
    public void unknownField(String name, Object value) {
      unknownFields.add(name);
    }

    public void rejectUnknownFields() {
      if (!unknownFields.isEmpty()) {
        throw new IllegalArgumentException("不支持的配置项: " + String.join(",", unknownFields));
      }
    }
  }
}
