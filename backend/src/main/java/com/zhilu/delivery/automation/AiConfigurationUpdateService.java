package com.zhilu.delivery.automation;

import com.zhilu.delivery.audit.AuditService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConfigurationUpdateService {
  private final AiConfigurationService configurations;
  private final AuditService audit;

  public AiConfigurationUpdateService(
      AiConfigurationService configurations, AuditService audit) {
    this.configurations = configurations;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> saveValidated(
      long organizationId, long actorUserId, AiConfigurationDraft draft) {
    Map<String, Object> value = configurations.saveValidated(organizationId, draft);
    audit.record(organizationId, actorUserId, "UPDATE", "AI_CONFIGURATION",
        String.valueOf(organizationId), "更新 AI 配置 · model="
            + draft.getConnection().getModel()
            + " · apiKeyReplaced=" + draft.isApiKeyChanged());
    return value;
  }
}
