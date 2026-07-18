package com.zhilu.delivery.automation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai-service")
public class AiAdminController {
  private final AiConfigurationService configurations;
  private final AiClient ai;
  private final AuditService audit;
  private final ObjectMapper json;

  public AiAdminController(
      AiConfigurationService configurations, AiClient ai, AuditService audit, ObjectMapper json) {
    this.configurations = configurations;
    this.ai = ai;
    this.audit = audit;
    this.json = json;
  }

  @GetMapping("/config")
  public Map<String, Object> configuration(@AuthenticationPrincipal CurrentUser user) {
    return configurations.view(user.getOrganizationId());
  }

  @PostMapping("/config/test")
  public Map<String, Object> testConfiguration(
      @Valid @RequestBody AiConfigurationRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    AiConfigurationDraft draft = draft(user, request);
    validate(draft.getConnection());
    return ready(draft.getConnection());
  }

  @PutMapping("/config")
  public Map<String, Object> saveConfiguration(
      @Valid @RequestBody AiConfigurationRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    AiConfigurationDraft draft = draft(user, request);
    validate(draft.getConnection());
    Map<String, Object> value = configurations.saveValidated(user.getOrganizationId(), draft);
    audit.record(user.getOrganizationId(), user.getId(), "UPDATE", "AI_CONFIGURATION",
        String.valueOf(user.getOrganizationId()), "更新 AI 配置 · model="
            + draft.getConnection().getModel() + " · apiKeyReplaced=" + draft.isApiKeyChanged());
    return value;
  }

  private AiConfigurationDraft draft(CurrentUser user, AiConfigurationRequest request) {
    request.rejectUnknownFields();
    return configurations.draft(
        user.getOrganizationId(), request.baseUrl, request.model, request.apiKey);
  }

  private void validate(AiConnection connection) {
    ObjectNode schema = json.createObjectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("status")
        .put("type", "string").putArray("enum").add("ok");
    schema.putArray("required").add("status");
    schema.put("additionalProperties", false);
    JsonNode result = ai.completeJson(connection, "Validate AI service configuration.",
        "Return the required readiness status.", schema);
    if (result == null || !"ok".equals(result.path("status").asText())) {
      throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
    }
  }

  private Map<String, Object> ready(AiConnection connection) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("status", "READY");
    result.put("model", connection.getModel());
    return result;
  }

  public static final class AiConfigurationRequest {
    private final Set<String> unknownFields = new LinkedHashSet<String>();

    @NotBlank public String baseUrl;
    @NotBlank public String model;
    public String apiKey;

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
