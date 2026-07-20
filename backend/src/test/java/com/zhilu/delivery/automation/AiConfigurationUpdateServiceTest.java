package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiConfigurationUpdateServiceTest {
  private final AiConfigurationService configurations = mock(AiConfigurationService.class);
  private final AuditService audit = mock(AuditService.class);
  private final AiConfigurationUpdateService updates =
      new AiConfigurationUpdateService(configurations, audit);

  @Test
  void savesAndAuditsWithoutSecretMaterial() {
    AiConfigurationDraft draft = new AiConfigurationDraft(
        new AiConnection(7300L, "https://ai.example.com/v1", "qwen-plus",
            "recognizable-secret", "ORGANIZATION"), true);
    Map<String, Object> view = new LinkedHashMap<String, Object>();
    when(configurations.saveValidated(7300L, draft)).thenReturn(view);

    Map<String, Object> saved = updates.saveValidated(7300L, 7301L, draft);

    assertSame(view, saved);
    verify(configurations).saveValidated(7300L, draft);
    ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
    verify(audit).record(eq(7300L), eq(7301L), eq("UPDATE"), eq("AI_CONFIGURATION"),
        eq("7300"), details.capture());
    assertTrue(details.getValue().contains("qwen-plus"));
    assertTrue(details.getValue().contains("apiKeyReplaced=true"));
    assertFalse(details.getValue().contains("recognizable-secret"));
  }
}
