package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.security.SettingSecretCipher;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
  void blankApiKeyMayBeRetainedAcrossEquivalentDefaultPortOrigins() {
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://ai.example.com", "model-one", "stored-secret"));

    AiConfigurationDraft draft = configurations.draft(
        9100, "https://AI.EXAMPLE.COM:443/v1", "model-two", "");

    assertEquals("stored-secret", draft.getConnection().getApiKey());
  }

  @Test
  void retainedKeyIsNeverSentToAChangedOrigin() throws IOException {
    AtomicReference<String> originalAuthorization = new AtomicReference<String>();
    AtomicReference<String> changedAuthorization = new AtomicReference<String>();
    HttpServer original = readinessServer(originalAuthorization);
    HttpServer changed = readinessServer(changedAuthorization);
    try {
      String originalBaseUrl = "http://127.0.0.1:" + original.getAddress().getPort();
      String changedBaseUrl = "http://127.0.0.1:" + changed.getAddress().getPort();
      configurations.saveValidated(9100, configurations.draft(
          9100, originalBaseUrl, "model-one", "retained-test-secret"));
      OpenAiCompatibleClient client = new OpenAiCompatibleClient(
          new ObjectMapper(), configurations, 500, 500);
      client.completeJson(configurations.resolve(9100), "system", "user",
          new ObjectMapper().createObjectNode());

      boolean rejected = false;
      try {
        AiConfigurationDraft draft = configurations.draft(
            9100, changedBaseUrl, "model-two", "");
        client.completeJson(draft.getConnection(), "system", "user",
            new ObjectMapper().createObjectNode());
      } catch (IllegalArgumentException expected) {
        rejected = true;
        assertFalse(expected.getMessage().contains("retained-test-secret"));
      }

      assertEquals("Bearer retained-test-secret", originalAuthorization.get());
      assertNull(changedAuthorization.get(), "changed origin received the retained key");
      assertTrue(rejected, "changed origin must require an explicit API key");
    } finally {
      original.stop(0);
      changed.stop(0);
    }
  }

  @Test
  void staleRetainedKeyDraftCannotOverwriteACompleteValidatedSave() {
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://initial.example.com", "initial-model", "initial-key"));
    AiConfigurationDraft staleRetainedKey = configurations.draft(
        9100, "https://initial.example.com/v1", "stale-model", "");
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://winner.example.com", "winner-model", "winner-key"));

    assertThrows(ConflictException.class,
        () -> configurations.saveValidated(9100, staleRetainedKey));
    AiConnection saved = configurations.resolve(9100);
    assertEquals("https://winner.example.com", saved.getBaseUrl());
    assertEquals("winner-model", saved.getModel());
    assertEquals("winner-key", saved.getApiKey());
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

  @Test
  void rejectsSavingADraftForAnotherOrganization() {
    AiConfigurationDraft draft = configurations.draft(
        9100, "https://ai.example.com", "model", "secret");

    assertThrows(IllegalArgumentException.class,
        () -> configurations.saveValidated(9200, draft));
  }

  @Test
  void rejectsForgedDraftWithUnsafeConnectionValues() {
    AiConfigurationDraft forged = new AiConfigurationDraft(
        new AiConnection(9100, "https://ai.example.com?unsafe=true", " ", "secret", "MIXED"),
        true);

    assertThrows(IllegalArgumentException.class,
        () -> configurations.saveValidated(9100, forged));
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from system_setting where organization_id=9100", Integer.class));
  }

  @Test
  void rejectsStoredApiKeysThatAreNotMarkedEncrypted() {
    jdbc.update("insert into system_setting(organization_id,setting_key,setting_value,encrypted) "
        + "values (9100,'ai.apiKey','plaintext-secret',false)");

    assertThrows(IllegalStateException.class, () -> configurations.resolve(9100));
  }

  private HttpServer readinessServer(AtomicReference<String> authorization) throws IOException {
    HttpServer value = HttpServer.create(new InetSocketAddress(0), 0);
    value.createContext("/", exchange -> readiness(exchange, authorization));
    value.start();
    return value;
  }

  private void readiness(
      HttpExchange exchange, AtomicReference<String> authorization) throws IOException {
    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    byte[] body = ("{\"choices\":[{\"message\":{\"content\":"
        + "\"{\\\"status\\\":\\\"ok\\\"}\"}}]}").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
