package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.security.SettingSecretCipher;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:outline-configuration;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.settings.encryption-key=test-settings-master-key-2026",
    "delivery.outline.base-url=http://outline.env",
    "delivery.outline.public-base-url=http://outline-browser.env",
    "delivery.outline.api-token=ol_api_env",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
class OutlineConfigurationServiceTest {
  @Autowired OutlineConfigurationService configurations;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc.update("delete from outline_document_link");
    jdbc.update("delete from system_setting");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values "
        + "(8100,'组织一','OUTLINE-ONE'),(8200,'组织二','OUTLINE-TWO')");
  }

  @Test
  void resolvesEnvironmentFallbackBeforeAnOrganizationSavesSettings() {
    OutlineConnection value = configurations.resolve(8100);
    assertEquals("http://outline.env", value.getBaseUrl());
    assertEquals("ol_api_env", value.getApiToken());
    assertEquals("ENVIRONMENT", value.getSource());
    assertTrue(value.isConfigured());
  }

  @Test
  void savesEncryptedOrganizationSettingsAndKeepsOrganizationsIsolated() {
    OutlineConfigurationDraft draft = configurations.draft(
        8100, "http://outline.one/", "http://browser.one/",
        "ol_api_one", "http://browser.one/collection/delivery-D4rIACBrmU/");
    assertEquals("delivery-D4rIACBrmU", draft.getCollectionReference());
    Map<String, Object> saved = configurations.saveValidated(
        8100, draft,
        new OutlineCollection(
            "11111111-1111-4111-8111-111111111111", "交付一", "D4rIACBrmU"));

    assertEquals("http://outline.one", saved.get("baseUrl"));
    assertEquals("11111111-1111-4111-8111-111111111111", saved.get("collectionId"));
    assertEquals(Boolean.TRUE, saved.get("apiTokenConfigured"));
    assertFalse(saved.containsKey("apiToken"));
    assertEquals("ol_api_one", configurations.resolve(8100).getApiToken());
    assertEquals("ol_api_env", configurations.resolve(8200).getApiToken());

    Map<String, Object> tokenRow = jdbc.queryForMap(
        "select setting_value,encrypted from system_setting "
            + "where organization_id=8100 and setting_key='outline.apiToken'");
    assertEquals(Boolean.TRUE, tokenRow.get("encrypted"));
    assertFalse(String.valueOf(tokenRow.get("setting_value")).contains("ol_api_one"));
  }

  @Test
  void blankTokenKeepsTheExistingEncryptedToken() {
    OutlineConfigurationDraft first = configurations.draft(
        8100, "http://outline.one", "http://browser.one",
        "ol_api_one", "D4rIACBrmU");
    configurations.saveValidated(
        8100, first,
        new OutlineCollection(
            "11111111-1111-4111-8111-111111111111", "交付一", "D4rIACBrmU"));
    String ciphertext = jdbc.queryForObject(
        "select setting_value from system_setting where organization_id=8100 "
            + "and setting_key='outline.apiToken'", String.class);

    OutlineConfigurationDraft second = configurations.draft(
        8100, "http://outline-new.one", "http://browser-new.one",
        "", "11111111-1111-4111-8111-111111111111");
    configurations.saveValidated(
        8100, second,
        new OutlineCollection(
            "11111111-1111-4111-8111-111111111111", "交付一", "D4rIACBrmU"));

    assertFalse(second.isTokenChanged());
    assertEquals("ol_api_one", configurations.resolve(8100).getApiToken());
    assertEquals(ciphertext, jdbc.queryForObject(
        "select setting_value from system_setting where organization_id=8100 "
            + "and setting_key='outline.apiToken'", String.class));
  }

  @Test
  void damagedOrWrongKeyCiphertextNeverFallsBackToPlaintext() {
    jdbc.update("insert into system_setting(organization_id,setting_key,setting_value,encrypted) "
        + "values (8100,'outline.apiToken','v1:00:broken',true)");
    IllegalStateException damaged = assertThrows(
        IllegalStateException.class, () -> configurations.resolve(8100));
    assertTrue(damaged.getMessage().contains("SETTINGS_ENCRYPTION_KEY"));

    jdbc.update("delete from system_setting where organization_id=8100");
    OutlineConfigurationDraft draft = configurations.draft(
        8100, "http://outline.one", "http://browser.one",
        "ol_api_one", "D4rIACBrmU");
    configurations.saveValidated(
        8100, draft,
        new OutlineCollection(
            "11111111-1111-4111-8111-111111111111", "交付一", "D4rIACBrmU"));
    OutlineConfigurationService wrongKeyService = new OutlineConfigurationService(
        jdbc, new OutlineProperties(),
        new SettingSecretCipher("different-settings-master-key-2026"));
    assertThrows(
        IllegalStateException.class, () -> wrongKeyService.resolve(8100));
  }

  @Test
  void rejectsInvalidUrlsMissingTokensAndCollectionSwitches() {
    assertThrows(IllegalArgumentException.class, () -> configurations.draft(
        8100, "file:///etc/passwd", "http://browser.one",
        "ol_api_one", "D4rIACBrmU"));

    jdbc.update("delete from system_setting where organization_id=8100");
    OutlineProperties empty = new OutlineProperties();
    empty.setBaseUrl("http://outline.empty");
    empty.setPublicBaseUrl("http://outline.empty");
    empty.setApiToken("");
    empty.setCollectionId("");
    OutlineConfigurationService emptyService = new OutlineConfigurationService(
        jdbc, empty, new SettingSecretCipher("test-settings-master-key-2026"));
    IllegalArgumentException missingToken = assertThrows(
        IllegalArgumentException.class, () -> emptyService.draft(
            8100, "http://outline.empty", "http://outline.empty",
            "", "D4rIACBrmU"));
    assertTrue(missingToken.getMessage().contains("API Token"));

    jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
            + "outline_collection_id,outline_document_id,title_cache,sync_status) "
            + "values (8100,'KNOWLEDGE_ROOT','INDEX',"
            + "'11111111-1111-4111-8111-111111111111','doc-1','知识库','READY')");
    OutlineConfigurationDraft changed = configurations.draft(
        8100, "http://outline.one", "http://browser.one",
        "ol_api_one", "D4rIACBrmU");
    ConflictException failure = assertThrows(
        ConflictException.class,
        () -> configurations.saveValidated(
            8100, changed,
            new OutlineCollection(
                "22222222-2222-4222-8222-222222222222", "新集合", "NewUrlId")));
    assertTrue(failure.getMessage().contains("不能直接更换 Collection"));
  }
}
