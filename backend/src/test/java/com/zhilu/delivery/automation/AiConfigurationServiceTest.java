package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.zhilu.delivery.common.security.SettingSecretCipher;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-configuration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.settings.encryption-key=test-settings-master-key-2026",
    "delivery.ai.base-url=http://ai.env/v1",
    "delivery.ai.model=env-model",
    "delivery.ai.api-key=env-secret"
})
class AiConfigurationServiceTest {
  @Autowired AiConfigurationService configurations;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc.update("delete from system_setting");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values "
        + "(9100,'组织一','AI-ONE'),(9200,'组织二','AI-TWO')");
  }

  @Test
  void resolvesEnvironmentFallbackAndReturnsMaskedView() {
    AiConnection value = configurations.resolve(9100);
    assertEquals("http://ai.env/v1", value.getBaseUrl());
    assertEquals("env-model", value.getModel());
    assertEquals("env-secret", value.getApiKey());
    assertEquals("ENVIRONMENT", value.getSource());
    Map<String, Object> view = configurations.view(9100);
    assertEquals(Boolean.TRUE, view.get("apiKeyConfigured"));
    assertFalse(view.containsKey("apiKey"));
  }

  @Test
  void savesEncryptedOrganizationSettingsWithoutAffectingAnotherOrganization() {
    AiConfigurationDraft draft = configurations.draft(
        9100, "https://ai.example.com/", "qwen-plus", "new-secret");
    configurations.saveValidated(9100, draft);
    assertEquals("https://ai.example.com", configurations.resolve(9100).getBaseUrl());
    assertEquals("new-secret", configurations.resolve(9100).getApiKey());
    assertEquals("env-secret", configurations.resolve(9200).getApiKey());
    Map<String, Object> stored = jdbc.queryForMap(
        "select setting_value,encrypted from system_setting "
            + "where organization_id=9100 and setting_key='ai.apiKey'");
    assertEquals(Boolean.TRUE, stored.get("encrypted"));
    assertFalse(String.valueOf(stored.get("setting_value")).contains("new-secret"));
  }

  @Test
  void blankApiKeyKeepsTheEffectiveExistingSecret() {
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://ai.example.com", "model-one", "stored-secret"));
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://ai.example.com/v1", "model-two", ""));
    assertEquals("stored-secret", configurations.resolve(9100).getApiKey());
    assertEquals("model-two", configurations.resolve(9100).getModel());
  }

  @Test
  void rejectsMissingSecretAndUnsafeUrls() {
    jdbc.update("delete from system_setting");
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://user:pass@ai.example.com", "model", "key"));
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://ai.example.com?x=1", "model", "key"));
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://ai.example.com", " ", "key"));
  }

  @Test
  void resolvesAnIncompleteEnvironmentAsUnconfigured() {
    AiConfigurationService empty = new AiConfigurationService(
        jdbc, new SettingSecretCipher("test-settings-master-key-2026"), "", "", "");

    assertFalse(empty.resolve(9100).isConfigured());
  }
}
