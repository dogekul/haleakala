# Outline Admin Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在“系统管理 → 文档中心”提供组织级 Outline 配置、连接测试和安全保存，并让知识、项目及后台任务立即使用各自组织的运行时配置。

**Architecture:** 复用 `system_setting` 保存组织级连接字段，API Token 通过部署级主密钥加密；`OutlineConfigurationService` 负责解析数据库与环境变量回退并生成不可变 `OutlineConnection`。`OutlineClient` 只消费连接快照，前台请求与后台任务都从明确的 `organizationId` 解析连接，避免全局可变配置和跨组织串用。

**Tech Stack:** Java 8、Spring Boot 2.7.18、JdbcTemplate、Spring Security Crypto、MySQL/H2、React 18、TypeScript、Ant Design、TanStack Query、JUnit 5、Mockito、MockMvc、Vitest。

## Global Constraints

- 配置按当前登录用户的 `organizationId` 隔离，不做全平台共享配置。
- 配置入口必须放在现有 `/admin/document-center` 页面。
- 保存后立即生效，不重启后端。
- API Token 只允许设置或覆盖；读取接口不得返回明文或密文。
- API Token 输入留空表示保留当前有效 Token。
- Collection 输入必须兼容 UUID、URL ID、`slug-urlId` 和完整 `/collection/{identifier}` 链接。
- 连接验证后只保存 Outline 返回的规范 Collection UUID。
- 已存在有效 Outline 文档映射时禁止切换 Collection。
- 环境变量继续作为组织未保存字段时的兼容回退。
- 连接、读取超时和任务重试参数继续保持部署级配置。
- 不新增数据库表或第三方依赖。
- 不实现自动创建 Collection、Token 删除、配置清空、跨 Collection 迁移或多 Collection 路由。
- 每个行为先写失败测试，确认失败原因正确后再写最小实现。

---

## File Structure

### Backend responsibilities

- `backend/src/main/java/com/zhilu/delivery/common/security/SettingSecretCipher.java`
  - 只负责版本化密文的加密和解密。
- `backend/src/main/java/com/zhilu/delivery/document/OutlineConnection.java`
  - 一次 Outline 调用使用的不可变组织连接快照。
- `backend/src/main/java/com/zhilu/delivery/document/OutlineCollection.java`
  - `collections.info` 返回的规范 Collection 信息。
- `backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationDraft.java`
  - 管理页未保存配置、规范化 Collection 引用和 Token 是否替换。
- `backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationService.java`
  - 读取环境变量回退、读取/保存组织设置、URL 校验、Collection 切换保护和脱敏视图。
- `backend/src/main/java/com/zhilu/delivery/document/OutlineClient.java`
  - 改为显式接收 `OutlineConnection`。
- `backend/src/main/java/com/zhilu/delivery/document/HttpOutlineClient.java`
  - 使用连接快照中的 Base URL 和 Token 调用 Outline。
- `backend/src/main/java/com/zhilu/delivery/document/DocumentAdminController.java`
  - 增加配置读取、测试和保存接口。
- `backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java`
  - 文档读写、映射和跳转 URL 使用组织连接。
- `backend/src/main/java/com/zhilu/delivery/document/DocumentMigrationService.java`
  - 状态检查使用组织连接。
- `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java`
  - 知识列表中的 Outline 跳转 URL 使用组织公开地址。

### Frontend responsibilities

- `frontend/src/modules/admin/types.ts`
  - 增加配置、配置输入和连接测试类型。
- `frontend/src/modules/admin/adminApi.ts`
  - 增加配置读取、测试和保存 API。
- `frontend/src/modules/admin/DocumentCenterPage.tsx`
  - 增加连接配置卡片并协调独立的加载、测试、保存与文档操作状态。
- `frontend/src/styles/global.css`
  - 增加响应式配置卡片样式。

---

### Task 1: Add versioned secret encryption and deployment key

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/common/security/SettingSecretCipher.java`
- Create: `backend/src/test/java/com/zhilu/delivery/common/security/SettingSecretCipherTest.java`
- Modify: `backend/src/main/resources/application.yml:50`
- Modify: `.env.example:13`
- Modify: `compose.yaml:75`
- Modify: `deploy/aliyun/docker-compose.ecs.yml:76`
- Modify: `docs/operations/deployment.md:10`

**Interfaces:**

- Consumes: Spring Security `Encryptors.delux(String password, String salt)`.
- Produces:
  - `SettingSecretCipher(String masterKey)`
  - `String encrypt(String plaintext)`
  - `String decrypt(String storedValue)`
  - Stored format: `v1:<32-char-salt-hex>:<ciphertext>`

- [ ] **Step 1: Write failing cipher tests**

Create `SettingSecretCipherTest`:

```java
package com.zhilu.delivery.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingSecretCipherTest {
  private final SettingSecretCipher cipher =
      new SettingSecretCipher("test-settings-master-key-2026");

  @Test
  void encryptsWithRandomSaltAndDecryptsTheOriginalValue() {
    String first = cipher.encrypt("ol_api_secret");
    String second = cipher.encrypt("ol_api_secret");

    assertTrue(first.startsWith("v1:"));
    assertFalse(first.contains("ol_api_secret"));
    assertNotEquals(first, second);
    org.junit.jupiter.api.Assertions.assertEquals(
        "ol_api_secret", cipher.decrypt(first));
    org.junit.jupiter.api.Assertions.assertEquals(
        "ol_api_secret", cipher.decrypt(second));
  }

  @Test
  void rejectsBlankMasterKeysAndInvalidCiphertext() {
    assertThrows(IllegalArgumentException.class, () -> new SettingSecretCipher(" "));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("plain-token"));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:00:broken"));
  }
}
```

- [ ] **Step 2: Run the test and verify the missing type failure**

Run:

```bash
cd backend
mvn -q -Dtest=SettingSecretCipherTest test
```

Expected: FAIL because `SettingSecretCipher` does not exist.

- [ ] **Step 3: Implement the minimal versioned cipher**

Create `SettingSecretCipher` with this public behavior:

```java
package com.zhilu.delivery.common.security;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;

@Service
public class SettingSecretCipher {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final String masterKey;

  public SettingSecretCipher(
      @Value("${delivery.settings.encryption-key}") String masterKey) {
    if (masterKey == null || masterKey.trim().isEmpty()) {
      throw new IllegalArgumentException("系统设置加密主密钥不能为空");
    }
    this.masterKey = masterKey;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      throw new IllegalArgumentException("待加密内容不能为空");
    }
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    String saltHex = new String(Hex.encode(salt));
    String encrypted = Encryptors.delux(masterKey, saltHex).encrypt(plaintext);
    return "v1:" + saltHex + ":" + encrypted;
  }

  public String decrypt(String storedValue) {
    try {
      String[] parts = storedValue == null
          ? new String[0] : storedValue.split(":", 3);
      if (parts.length != 3 || !"v1".equals(parts[0]) || parts[1].length() != 32) {
        throw new IllegalArgumentException("invalid encrypted setting");
      }
      return Encryptors.delux(masterKey, parts[1]).decrypt(parts[2]);
    } catch (RuntimeException failure) {
      throw new IllegalStateException(
          "系统设置密文无法解密，请检查 SETTINGS_ENCRYPTION_KEY", failure);
    }
  }
}
```

- [ ] **Step 4: Bind the deployment-level master key**

Add under `delivery` in `application.yml`:

```yaml
  settings:
    encryption-key: ${SETTINGS_ENCRYPTION_KEY:${AGENT_SHARED_SECRET:change-me}}
```

Add to `.env.example` immediately after `AGENT_SHARED_SECRET`:

```dotenv
# 用于加密数据库中的 API Token；生产环境必须使用独立、稳定的高强度值
SETTINGS_ENCRYPTION_KEY=local-agent-secret
```

Add to the local backend service in `compose.yaml`:

```yaml
      SETTINGS_ENCRYPTION_KEY: ${SETTINGS_ENCRYPTION_KEY:-${AGENT_SHARED_SECRET:-local-agent-secret}}
```

Add to the production backend service in `deploy/aliyun/docker-compose.ecs.yml`:

```yaml
      SETTINGS_ENCRYPTION_KEY: ${SETTINGS_ENCRYPTION_KEY:?SETTINGS_ENCRYPTION_KEY is required}
```

Update `docs/operations/deployment.md`:

```markdown
- `SETTINGS_ENCRYPTION_KEY` 用于加密管理页保存的 API Token。生产环境必须设置独立、稳定的高强度值；更换前需先完成密钥轮换，直接更换会导致已有密文无法解密。
```

- [ ] **Step 5: Verify cipher tests and Compose interpolation**

Run:

```bash
cd backend
mvn -q -Dtest=SettingSecretCipherTest test
cd ..
docker compose config >/tmp/zhilu-compose-config.yml
grep -n "SETTINGS_ENCRYPTION_KEY" /tmp/zhilu-compose-config.yml
```

Expected: JUnit PASS; rendered Compose configuration contains a non-empty `SETTINGS_ENCRYPTION_KEY`.

- [ ] **Step 6: Commit the secret storage foundation**

```bash
git add backend/src/main/java/com/zhilu/delivery/common/security/SettingSecretCipher.java \
  backend/src/test/java/com/zhilu/delivery/common/security/SettingSecretCipherTest.java \
  backend/src/main/resources/application.yml .env.example compose.yaml \
  deploy/aliyun/docker-compose.ecs.yml docs/operations/deployment.md
git commit -m "feat: encrypt runtime integration settings"
```

---

### Task 2: Build organization-level Outline configuration resolution

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineConnection.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineCollection.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationDraft.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/OutlineConfigurationServiceTest.java`

**Interfaces:**

- Consumes:
  - `OutlineProperties`
  - `SettingSecretCipher.encrypt/decrypt`
  - Existing `system_setting` and `outline_document_link` tables.
- Produces:
  - `OutlineConnection resolve(long organizationId)`
  - `Map<String, Object> view(long organizationId)`
  - `OutlineConfigurationDraft draft(long organizationId, String baseUrl, String publicBaseUrl, String apiToken, String collectionReference)`
  - `Map<String, Object> saveValidated(long organizationId, OutlineConfigurationDraft draft, OutlineCollection verifiedCollection)`
  - `OutlineConnection.isConfigured()`
  - `OutlineConfigurationDraft.getConnection()`
  - `OutlineConfigurationDraft.getCollectionReference()`
  - `OutlineConfigurationDraft.isTokenChanged()`
  - `String documentUrl(OutlineConnection connection, String urlId)`

- [ ] **Step 1: Write failing organization configuration tests**

Create a Spring Boot H2 test with two organizations and these exact assertions:

```java
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
```

- [ ] **Step 2: Run the test and confirm missing model/service failures**

Run:

```bash
cd backend
mvn -q -Dtest=OutlineConfigurationServiceTest test
```

Expected: FAIL because the four new document configuration types do not exist.

- [ ] **Step 3: Implement immutable connection and collection values**

Implement `OutlineConnection` as a Java 8 final class with constructor and getters:

```java
public OutlineConnection(
    long organizationId, String baseUrl, String publicBaseUrl, String apiToken,
    String collectionId, String collectionName, String source)
```

Its configuration check must be:

```java
public boolean isConfigured() {
  return !blank(baseUrl) && !blank(apiToken) && !blank(collectionId);
}
```

Implement `OutlineCollection`:

```java
public OutlineCollection(String id, String name, String urlId)
```

Implement `OutlineConfigurationDraft`:

```java
public OutlineConfigurationDraft(
    OutlineConnection connection, String collectionReference, boolean tokenChanged)
```

All three classes expose getters only and do not override `toString`, preventing accidental secret dumps from a generated representation.

- [ ] **Step 4: Implement local resolution, validation, encryption and switch protection**

`OutlineConfigurationService` must:

1. Load only keys beginning with `outline.` for the requested organization.
2. Require `encrypted=true` for a stored `outline.apiToken`.
3. Decrypt the token with `SettingSecretCipher`.
4. Resolve each field independently from database first, then `OutlineProperties`.
5. Return source:
   - `ENVIRONMENT` when no organization connection field exists.
   - `ORGANIZATION` when base URL, public URL, Collection UUID and Token all exist in the organization.
   - `MIXED` otherwise.
6. Normalize Base URLs with `java.net.URI`:
   - scheme is `http` or `https`
   - host is present
   - user info, query and fragment are absent
   - path is empty or `/`
   - trailing `/` removed
7. Normalize a full Collection link by accepting only `/collection/{identifier}`, rejecting user info/query/fragment, and extracting the final path segment.
8. Treat blank `publicBaseUrl` as the normalized `baseUrl`.
9. Treat blank draft token as the current resolved token.
10. Upsert ordinary fields with `encrypted=false`; only overwrite `outline.apiToken` when `tokenChanged=true`, using `encrypted=true`.
11. Before save, query:

```sql
select distinct outline_collection_id
from outline_document_link
where organization_id=?
  and outline_document_id is not null
```

and throw `ConflictException` if a nonblank existing value differs from the verified UUID.

The returned view must contain only:

```java
baseUrl
publicBaseUrl
collectionId
collectionName
apiTokenConfigured
source
```

`documentUrl` must use a caller-resolved connection so list rendering does not perform one settings query per row:

```java
public String documentUrl(OutlineConnection connection, String urlId) {
  if (blank(urlId)) return null;
  return connection.getPublicBaseUrl() + "/doc/" + urlId;
}
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
cd backend
mvn -q -Dtest=SettingSecretCipherTest,OutlineConfigurationServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit organization configuration storage**

```bash
git add backend/src/main/java/com/zhilu/delivery/document/OutlineConnection.java \
  backend/src/main/java/com/zhilu/delivery/document/OutlineCollection.java \
  backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationDraft.java \
  backend/src/main/java/com/zhilu/delivery/document/OutlineConfigurationService.java \
  backend/src/test/java/com/zhilu/delivery/document/OutlineConfigurationServiceTest.java
git commit -m "feat: add organization Outline configuration"
```

---

### Task 3: Make all Outline calls consume organization connection snapshots

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/document/OutlineClient.java:1`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/HttpOutlineClient.java:25`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java:22`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentMigrationService.java:17`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java:24`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/HttpOutlineClientTest.java:25`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/DocumentCenterServiceTest.java:42`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/DocumentMigrationServiceTest.java:27`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/DocumentApiIT.java:30`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/DocumentExportApiIT.java:26`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentInitializationTest.java:45`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentLifecycleIT.java:40`
- Modify: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java:29`
- Modify: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeTemplateApiIT.java:36`

**Interfaces:**

- Consumes:
  - `OutlineConfigurationService.resolve(long organizationId)`
  - `OutlineConnection`
- Produces:

```java
OutlineDocument create(
    OutlineConnection connection, String documentId, String title, String text,
    String collectionId, String parentDocumentId, boolean publish);
OutlineDocument info(OutlineConnection connection, String documentId);
List<OutlineDocument> children(
    OutlineConnection connection, String parentDocumentId);
OutlineDocument update(
    OutlineConnection connection, String documentId, String title, String text);
OutlineCollection collectionInfo(
    OutlineConnection connection, String collectionReference);
OutlineCollection testConnection(
    OutlineConnection connection, String collectionReference);
String exportMarkdown(OutlineConnection connection, String documentId);
```

- [ ] **Step 1: Rewrite the HTTP client test against explicit connections**

Update `HttpOutlineClientTest` so the constructor only uses `OutlineProperties` for timeouts:

```java
properties = new OutlineProperties();
properties.setConnectTimeout(Duration.ofMillis(200));
properties.setReadTimeout(Duration.ofMillis(200));
client = new HttpOutlineClient(properties, json);
connection = new OutlineConnection(
    8100, "http://127.0.0.1:" + server.getAddress().getPort(),
    "http://outline.browser", "ol_api_test", COLLECTION_ID, "文档中心",
    "ORGANIZATION");
```

Change every call to pass `connection`, and assert `collections.info` returns canonical data:

```java
OutlineCollection collection = client.testConnection(
    connection, "delivery-D4rIACBrmU");
assertEquals(COLLECTION_ID, collection.getId());
assertEquals("文档中心", collection.getName());
```

Replace the old configuration-state assertion with:

```java
assertTrue(connection.isConfigured());
assertFalse(new OutlineConnection(
    8100, connection.getBaseUrl(), connection.getPublicBaseUrl(), "",
    COLLECTION_ID, "文档中心", "ORGANIZATION").isConfigured());
```

- [ ] **Step 2: Run the HTTP client test and verify signature failures**

Run:

```bash
cd backend
mvn -q -Dtest=HttpOutlineClientTest test
```

Expected: FAIL because `OutlineClient` and `HttpOutlineClient` still use global properties.

- [ ] **Step 3: Refactor `OutlineClient` and `HttpOutlineClient`**

Replace the interface with the signatures in this task's **Produces** block.

In `HttpOutlineClient`:

- Remove reads of Base URL, API Token and Collection UUID from `OutlineProperties`.
- Keep `OutlineProperties` only for connect/read timeouts.
- Change the internal method to:

```java
private JsonNode post(
    OutlineConnection connection, String method, Map<String, Object> body)
```

- Reject unconfigured connections before issuing HTTP:

```java
if (connection == null || !connection.isConfigured()) {
  throw new OutlineException(
      OutlineException.Type.NOT_CONFIGURED,
      "Outline integration is not configured");
}
```

- Set the Bearer Header from `connection.getApiToken()`.
- Build the endpoint from normalized `connection.getBaseUrl()`.
- Parse `collections.info` into:

```java
return new OutlineCollection(
    data.path("id").asText(),
    data.path("name").asText(""),
    data.path("urlId").asText(""));
```

- Implement `testConnection` as the explicit draft-validation entry point and have both it and `collectionInfo` call the same private `collections.info` request/parser.
- Keep error messages free of request headers, request bodies and connection objects.

- [ ] **Step 4: Add connection-snapshot use to `DocumentCenterService`**

Replace the injected `OutlineProperties` with `OutlineConfigurationService`.

At the start of each public remote operation, resolve one connection:

```java
OutlineConnection connection = configurations.resolve(organizationId);
```

Add private overloads so one operation keeps the same snapshot:

```java
private long ensureIndex(
    OutlineConnection connection, String businessKey, String title, Long parentLinkId)
private DocumentView readLink(
    OutlineConnection connection, long linkId)
private OutlineDocument createOrRecover(
    OutlineConnection connection, String businessKey, String title,
    String markdown, String parentDocumentId)
private DocumentView view(
    OutlineConnection connection, long linkId, OutlineDocument document,
    String syncStatus, String lastError)
```

Use:

```java
connection.getOrganizationId()
connection.getCollectionId()
outline.info(connection, documentId)
outline.create(connection, documentId, title, markdown,
    connection.getCollectionId(), parentDocumentId, true)
```

Build browser links from `connection.getPublicBaseUrl()` and the Outline response URL. Stop reading global Base URLs and Collection IDs.

- [ ] **Step 5: Make migration status organization-aware**

Replace `OutlineProperties` injection in `DocumentMigrationService` with `OutlineConfigurationService`.

Implement status connection handling:

```java
OutlineConnection connection = configurations.resolve(organizationId);
if (!connection.isConfigured()) {
  integrationStatus = "NOT_CONFIGURED";
} else {
  try {
    OutlineCollection collection = outline.collectionInfo(
        connection, connection.getCollectionId());
    integrationStatus = "READY";
    result.put("collectionName", collection.getName());
  } catch (OutlineException failure) {
    integrationStatus = "FAILED";
    connectionError = failure.getMessage();
  }
}
result.put("collectionId", connection.getCollectionId());
```

Initialization and migration continue to call `DocumentCenterService`, which resolves the same organization explicitly.

- [ ] **Step 6: Make knowledge list links organization-aware**

Replace `OutlineProperties` injection in `KnowledgeService` with `OutlineConfigurationService`.

Resolve once in each `search` and `get` method, then pass the connection into the row mapper:

```java
OutlineConnection connection = configurations.resolve(user.getOrganizationId());
return jdbc.query(sql.toString(),
    (row, index) -> detail(row, connection), args.toArray());
```

Change `detail` and `item` to accept `OutlineConnection`, then build:

```java
value.put("outlineUrl",
    configurations.documentUrl(connection, row.getString("outline_url_id")));
```

- [ ] **Step 7: Update Mockito contracts without weakening organization assertions**

Apply these exact signature changes in every listed test:

```java
when(outline.info(any(OutlineConnection.class), eq(DOCUMENT_ID)))
when(outline.update(any(OutlineConnection.class), eq(DOCUMENT_ID),
    eq("新标题"), eq("# 新正文")))
when(outline.create(any(OutlineConnection.class), anyString(), anyString(),
    anyString(), anyString(), nullable(String.class), anyBoolean()))
verify(outline, never()).update(
    any(OutlineConnection.class), anyString(), anyString(), anyString())
```

Where a test verifies tenant behavior, capture the connection:

```java
ArgumentCaptor<OutlineConnection> connection =
    ArgumentCaptor.forClass(OutlineConnection.class);
verify(outline).info(connection.capture(), eq(DOCUMENT_ID));
assertEquals(4100L, connection.getValue().getOrganizationId());
assertEquals("ol_api_test", connection.getValue().getApiToken());
```

Do not use `any()` for the organization assertion in:

- `DocumentApiIT`
- `DocumentMigrationServiceTest`
- `ProjectDocumentInitializationTest`
- `KnowledgeServiceTest`

- [ ] **Step 8: Run the focused backend regression suite**

Run:

```bash
cd backend
mvn -q -Dtest=HttpOutlineClientTest,OutlineConfigurationServiceTest,\
DocumentCenterServiceTest,DocumentMigrationServiceTest,DocumentApiIT,\
DocumentExportApiIT,ProjectDocumentInitializationTest,ProjectDocumentLifecycleIT,\
KnowledgeServiceTest,KnowledgeTemplateApiIT test
```

Expected: PASS; no test uses the old `OutlineClient` signatures.

- [ ] **Step 9: Commit the organization-aware client refactor**

```bash
git add backend/src/main/java/com/zhilu/delivery/document/OutlineClient.java \
  backend/src/main/java/com/zhilu/delivery/document/HttpOutlineClient.java \
  backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java \
  backend/src/main/java/com/zhilu/delivery/document/DocumentMigrationService.java \
  backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java \
  backend/src/test/java/com/zhilu/delivery/document \
  backend/src/test/java/com/zhilu/delivery/knowledge
git commit -m "refactor: isolate Outline calls by organization"
```

---

### Task 4: Add secure admin configuration APIs and audit coverage

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentAdminController.java:1`
- Modify: `backend/src/test/java/com/zhilu/delivery/document/DocumentAdminControllerTest.java:1`
- Create: `backend/src/test/java/com/zhilu/delivery/document/OutlineConfigurationApiIT.java`

**Interfaces:**

- Consumes:
  - `OutlineConfigurationService.view/draft/saveValidated`
  - `OutlineClient.testConnection`
  - `AuditService.record`
- Produces:
  - `GET /api/v1/admin/document-center/config`
  - `POST /api/v1/admin/document-center/config/test`
  - `PUT /api/v1/admin/document-center/config`

- [ ] **Step 1: Write failing MockMvc API tests**

Create `OutlineConfigurationApiIT` with real `OutlineConfigurationService`, `JdbcTemplate`, `MockMvc`, a mocked `OutlineClient`, and test property:

```java
"delivery.settings.encryption-key=test-settings-master-key-2026"
```

Seed organizations `9100` and `9200` plus admin users. Cover:

```java
@Test
void testsAndSavesACollectionLinkWithoutReturningTheToken() throws Exception {
  when(outline.testConnection(
      any(OutlineConnection.class), eq("delivery-D4rIACBrmU")))
      .thenReturn(new OutlineCollection(
          "11111111-1111-4111-8111-111111111111",
          "智鹿交付", "D4rIACBrmU"));

  String request = "{"
      + "\"baseUrl\":\"http://outline.internal:3000\","
      + "\"publicBaseUrl\":\"http://localhost:3000\","
      + "\"collectionId\":\"http://localhost:3000/collection/delivery-D4rIACBrmU/\","
      + "\"apiToken\":\"ol_api_admin_secret\"}";

  mvc.perform(post("/api/v1/admin/document-center/config/test")
          .with(admin(9100)).with(csrf())
          .contentType(MediaType.APPLICATION_JSON).content(request))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("READY"))
      .andExpect(jsonPath("$.collectionId")
          .value("11111111-1111-4111-8111-111111111111"))
      .andExpect(jsonPath("$.collectionName").value("智鹿交付"))
      .andExpect(jsonPath("$.apiToken").doesNotExist());

  mvc.perform(put("/api/v1/admin/document-center/config")
          .with(admin(9100)).with(csrf())
          .contentType(MediaType.APPLICATION_JSON).content(request))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.apiTokenConfigured").value(true))
      .andExpect(jsonPath("$.apiToken").doesNotExist());

  mvc.perform(get("/api/v1/admin/document-center/config").with(admin(9100)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.collectionName").value("智鹿交付"))
      .andExpect(jsonPath("$.apiToken").doesNotExist());

  assertEquals(0, jdbc.queryForObject(
      "select count(*) from audit_log where details_text like '%ol_api_admin_secret%'",
      Integer.class));
  mvc.perform(get("/api/v1/admin/document-center/config").with(admin(9200)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.baseUrl").value("http://localhost:3000"))
      .andExpect(jsonPath("$.collectionName").isEmpty());
}
```

Add these failure and preservation tests:

```java
@Test
void rejectsUnauthorizedUnknownAndFailedConfigurationRequests() throws Exception {
  mvc.perform(get("/api/v1/admin/document-center/config").with(nonManager(9100)))
      .andExpect(status().isForbidden());

  mvc.perform(put("/api/v1/admin/document-center/config")
          .with(admin(9100)).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"baseUrl\":\"http://outline.internal:3000\","
              + "\"publicBaseUrl\":\"http://localhost:3000\","
              + "\"collectionId\":\"D4rIACBrmU\","
              + "\"apiToken\":\"ol_api_admin_secret\","
              + "\"organizationId\":9200}"))
      .andExpect(status().isBadRequest());

  when(outline.testConnection(
      any(OutlineConnection.class), eq("D4rIACBrmU")))
      .thenThrow(new OutlineException(
          OutlineException.Type.AUTHENTICATION, "Outline authentication failed"));
  mvc.perform(put("/api/v1/admin/document-center/config")
          .with(admin(9100)).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"baseUrl\":\"http://outline.internal:3000\","
              + "\"publicBaseUrl\":\"http://localhost:3000\","
              + "\"collectionId\":\"D4rIACBrmU\","
              + "\"apiToken\":\"wrong\"}"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.code").value("OUTLINE_AUTHENTICATION"));
  assertEquals(0, jdbc.queryForObject(
      "select count(*) from system_setting where organization_id=9100",
      Integer.class));
}

@Test
void blankTokenPreservesSecretAndExistingDocumentsBlockCollectionSwitch() throws Exception {
  when(outline.testConnection(any(OutlineConnection.class), anyString()))
      .thenReturn(new OutlineCollection(
          "11111111-1111-4111-8111-111111111111", "智鹿交付", "D4rIACBrmU"));
  putConfiguration(9100, "ol_api_admin_secret", "D4rIACBrmU")
      .andExpect(status().isOk());
  String firstCiphertext = jdbc.queryForObject(
      "select setting_value from system_setting where organization_id=9100 "
          + "and setting_key='outline.apiToken'", String.class);

  putConfiguration(9100, "", "11111111-1111-4111-8111-111111111111")
      .andExpect(status().isOk());
  assertEquals(firstCiphertext, jdbc.queryForObject(
      "select setting_value from system_setting where organization_id=9100 "
          + "and setting_key='outline.apiToken'", String.class));

  jdbc.update("insert into outline_document_link(organization_id,business_key,purpose,"
          + "outline_collection_id,outline_document_id,title_cache,sync_status) "
          + "values (9100,'KNOWLEDGE_ROOT','INDEX',"
          + "'11111111-1111-4111-8111-111111111111','doc-1','知识库','READY')");
  when(outline.testConnection(any(OutlineConnection.class), eq("NewUrlId")))
      .thenReturn(new OutlineCollection(
          "22222222-2222-4222-8222-222222222222", "新集合", "NewUrlId"));
  putConfiguration(9100, "", "NewUrlId")
      .andExpect(status().isConflict());
}
```

Use these test helpers:

```java
private ResultActions putConfiguration(
    long organizationId, String token, String collection) throws Exception {
  return mvc.perform(put("/api/v1/admin/document-center/config")
      .with(admin(organizationId)).with(csrf())
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"baseUrl\":\"http://outline.internal:3000\","
          + "\"publicBaseUrl\":\"http://localhost:3000\","
          + "\"collectionId\":\"" + collection + "\","
          + "\"apiToken\":\"" + token + "\"}"));
}

private RequestPostProcessor admin(long organizationId) {
  return actor(organizationId, "system:manage");
}

private RequestPostProcessor nonManager(long organizationId) {
  return actor(organizationId, "project:read");
}

private RequestPostProcessor actor(long organizationId, String permission) {
  CurrentUser principal = new CurrentUser(
      organizationId, organizationId, "actor-" + organizationId,
      "Actor " + organizationId, Collections.<String>emptyList(),
      Arrays.asList(permission));
  return authentication(new UsernamePasswordAuthenticationToken(
      principal, null,
      Arrays.asList(new SimpleGrantedAuthority(permission))));
}
```

- [ ] **Step 2: Run the API test and verify missing endpoints**

Run:

```bash
cd backend
mvn -q -Dtest=OutlineConfigurationApiIT test
```

Expected: FAIL with 404/405 because the three configuration endpoints do not exist.

- [ ] **Step 3: Add strict request DTO validation**

Inside `DocumentAdminController`, add:

```java
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
```

Do not add `organizationId` to the DTO.

- [ ] **Step 4: Implement GET, test and save endpoints**

Inject `OutlineConfigurationService` and `OutlineClient` into `DocumentAdminController`.

Add:

```java
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
```

Never include `request.apiToken`, `draft.getConnection()` or stored ciphertext in audit details.

- [ ] **Step 5: Extend existing controller permission regression**

Update `DocumentAdminControllerTest` so:

- `GET /config` is forbidden without `system:manage`;
- existing status, initialization, migration and retry endpoints remain unchanged;
- status and config responses both assert `$.apiToken` does not exist.

Mock only stable service outputs; do not weaken the existing verification of `migrations.initialize` and `migrations.retry`.

- [ ] **Step 6: Run admin and storage tests**

Run:

```bash
cd backend
mvn -q -Dtest=SettingSecretCipherTest,OutlineConfigurationServiceTest,\
OutlineConfigurationApiIT,DocumentAdminControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the admin API**

```bash
git add backend/src/main/java/com/zhilu/delivery/document/DocumentAdminController.java \
  backend/src/test/java/com/zhilu/delivery/document/DocumentAdminControllerTest.java \
  backend/src/test/java/com/zhilu/delivery/document/OutlineConfigurationApiIT.java
git commit -m "feat: manage Outline configuration from admin API"
```

---

### Task 5: Add the Outline configuration card to the document center page

**Files:**

- Modify: `frontend/src/modules/admin/types.ts:64`
- Modify: `frontend/src/modules/admin/adminApi.ts:1`
- Modify: `frontend/src/modules/admin/DocumentCenterPage.tsx:1`
- Create: `frontend/src/modules/admin/DocumentCenterConfiguration.test.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.test.tsx:45`
- Modify: `frontend/e2e/outline-document-center.e2e.ts:1`
- Modify: `frontend/src/styles/global.css:440`

**Interfaces:**

- Consumes:
  - `GET /api/v1/admin/document-center/config`
  - `POST /api/v1/admin/document-center/config/test`
  - `PUT /api/v1/admin/document-center/config`
- Produces:

```ts
export interface OutlineConfiguration {
  baseUrl: string
  publicBaseUrl: string
  collectionId: string
  collectionName?: string
  apiTokenConfigured: boolean
  source: 'ENVIRONMENT' | 'ORGANIZATION' | 'MIXED'
}

export interface OutlineConfigurationInput {
  baseUrl: string
  publicBaseUrl: string
  collectionId: string
  apiToken?: string
}

export interface OutlineConnectionTest {
  status: 'READY'
  collectionId: string
  collectionName: string
}
```

- [ ] **Step 1: Write failing component tests for test/save/redaction behavior**

Create `DocumentCenterConfiguration.test.tsx` using the same `QueryClientProvider` test wrapper as `AdminFlows.test.tsx`.

Define the fixture and renderer at the top of the test:

```tsx
const notConfiguredStatus: DocumentCenterStatus = {
  integrationStatus: 'NOT_CONFIGURED',
  collectionId: '',
  knowledgeRoot: { status: 'PENDING' },
  projectRoot: { status: 'PENDING' },
  jobs: { pending: 0, running: 0, success: 0, failed: 0 },
  failedJobs: [],
}

const json = (value: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(value), {
    status, headers: { 'Content-Type': 'application/json' },
  }))

function show() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <DocumentCenterPage />
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())
```

First test:

```tsx
it('测试并保存 Outline 配置且不会回填 Token', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config') && !init?.method) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'old-collection',
      collectionName: '旧目录',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/config/test') && init?.method === 'POST') return json({
      status: 'READY',
      collectionId: '11111111-1111-4111-8111-111111111111',
      collectionName: '智鹿交付',
    })
    if (url.endsWith('/config') && init?.method === 'PUT') return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: '11111111-1111-4111-8111-111111111111',
      collectionName: '智鹿交付',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  expect(await screen.findByDisplayValue('http://outline.internal:3000')).toBeVisible()
  expect(screen.getByText('API Token 已配置')).toBeVisible()
  expect(screen.getByLabelText('API Token')).toHaveValue('')

  const collection = screen.getByLabelText('Collection 链接或 UUID')
  await user.clear(collection)
  await user.type(
    collection,
    'http://localhost:3000/collection/delivery-D4rIACBrmU/')
  await user.type(screen.getByLabelText('API Token'), 'ol_api_new')
  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('智鹿交付')).toBeVisible()
  expect(collection).toHaveValue('11111111-1111-4111-8111-111111111111')

  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config',
    expect.objectContaining({
      method: 'PUT',
      body: expect.stringContaining('"apiToken":"ol_api_new"'),
    }),
  ))
  await waitFor(() => expect(screen.getByLabelText('API Token')).toHaveValue(''))
})
```

Second test:

```tsx
it('未配置时禁用初始化和迁移操作', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: '',
      collectionName: '',
      apiTokenConfigured: false,
      source: 'ENVIRONMENT',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  show()
  expect(await screen.findByRole('button', { name: '初始化目录' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '迁移知识文档' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '迁移项目文档' })).toBeDisabled()
})
```

- [ ] **Step 2: Run the frontend test and verify missing UI/API failures**

Run:

```bash
cd frontend
pnpm test -- --run src/modules/admin/DocumentCenterConfiguration.test.tsx
```

Expected: FAIL because configuration types, API calls and form controls do not exist.

- [ ] **Step 3: Add frontend types and API methods**

Add the three interfaces in the task's **Produces** block to `types.ts`.

Add to `adminApi`:

```ts
outlineConfiguration: () => api<OutlineConfiguration>(
  '/api/v1/admin/document-center/config',
),
testOutlineConfiguration: (input: OutlineConfigurationInput) =>
  api<OutlineConnectionTest>(
    '/api/v1/admin/document-center/config/test',
    { method: 'POST', body: JSON.stringify(input) },
  ),
saveOutlineConfiguration: (input: OutlineConfigurationInput) =>
  api<OutlineConfiguration>(
    '/api/v1/admin/document-center/config',
    { method: 'PUT', body: JSON.stringify(input) },
  ),
```

- [ ] **Step 4: Add the configuration query, form and independent mutations**

In `DocumentCenterPage`:

1. Create `Form.useForm<OutlineConfigurationInput>()`.
2. Query `adminApi.outlineConfiguration` with key `['admin-outline-configuration']`.
3. On query data, populate Base URLs and Collection UUID, always set `apiToken: ''`.
4. Add `testConfiguration` mutation:
   - call `form.validateFields()`;
   - call `adminApi.testOutlineConfiguration`;
   - on success set `collectionId` to the returned UUID;
   - retain a local `OutlineConnectionTest` success result;
   - show `message.success('Outline 连接正常')`.
5. Add `saveConfiguration` mutation:
   - submit form values;
   - on success reset `apiToken` to `''`;
   - update/invalidate configuration, status and job queries;
   - show `message.success('Outline 配置已保存')`.
6. Keep test, save and document operations as separate mutations.

Add a card before the connection alert:

```tsx
<Card className="admin-surface admin-document-config" title="Outline 连接配置">
  <Form
    form={form}
    layout="vertical"
    requiredMark="optional"
    onFinish={values => saveConfiguration.mutate(values)}
  >
    <Row gutter={16}>
      <Col xs={24} lg={12}>
        <Form.Item
          label="服务地址"
          name="baseUrl"
          extra="后端访问地址；本地 Compose 通常使用 host.docker.internal。"
          rules={[{ required: true, type: 'url' }]}
        >
          <Input placeholder="http://host.docker.internal:3000" />
        </Form.Item>
      </Col>
      <Col xs={24} lg={12}>
        <Form.Item
          label="浏览器访问地址"
          name="publicBaseUrl"
          extra="用于“在 Outline 中打开”的用户可访问地址。"
          rules={[{ type: 'url' }]}
        >
          <Input placeholder="http://localhost:3000" />
        </Form.Item>
      </Col>
    </Row>
    <Row gutter={16}>
      <Col xs={24} lg={12}>
        <Form.Item
          label="Collection 链接或 UUID"
          name="collectionId"
          rules={[{ required: true }]}
        >
          <Input placeholder="粘贴 Outline Collection 链接或 UUID" />
        </Form.Item>
      </Col>
      <Col xs={24} lg={12}>
        <Form.Item
          label="API Token"
          name="apiToken"
          extra={configuration.data?.apiTokenConfigured
            ? 'API Token 已配置，留空将保持不变。'
            : '从 Outline 设置 → API Keys 创建。'}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Col>
    </Row>
    <div className="admin-document-config-actions">
      <Space>
        <Button
          loading={testConfiguration.isPending}
          onClick={() => void testDraft()}
        >
          测试连接
        </Button>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saveConfiguration.isPending}
          onClick={() => form.submit()}
        >
          保存配置
        </Button>
      </Space>
      {configuration.data?.apiTokenConfigured && (
        <Tag color="success">API Token 已配置</Tag>
      )}
    </div>
  </Form>
</Card>
```

When a test succeeds, show an inline success Alert with Collection name and UUID.

Disable initialization and both migrations whenever `integration !== 'READY'`; use `title="请先保存并验证 Outline 配置"` so the reason remains discoverable.

- [ ] **Step 5: Add responsive styling**

Add to `global.css`:

```css
.admin-document-config .ant-form-item { margin-bottom: 16px; }
.admin-document-config-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.admin-document-config-result { margin-top: 14px; }

@media (max-width: 767px) {
  .admin-document-config-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

- [ ] **Step 6: Update the existing document-center regression fixture**

In `AdminPage.test.tsx`, add a response for `/api/v1/admin/document-center/config`:

```ts
return json({
  baseUrl: 'http://outline.internal:3000',
  publicBaseUrl: 'http://localhost:3000',
  collectionId: 'collection-id',
  collectionName: '智鹿交付',
  apiTokenConfigured: true,
  source: 'ORGANIZATION',
})
```

Keep the existing retry assertion unchanged.

- [ ] **Step 7: Run focused frontend tests and build**

Run:

```bash
cd frontend
pnpm test -- --run \
  src/modules/admin/DocumentCenterConfiguration.test.tsx \
  src/modules/admin/AdminPage.test.tsx
pnpm build
```

Expected: both test files PASS and Vite build succeeds.

- [ ] **Step 8: Extend the existing Outline browser flow with configuration management**

At the start of `outline-document-center.e2e.ts`, immediately after `await login(page)`, add:

```tsx
await nav(page, '系统管理').click()
await page.getByRole('link', { name: '文档中心' }).click()
await expect(page.getByRole('heading', { name: '文档中心' })).toBeVisible()

await page.getByLabel('服务地址').fill('http://mock-outline:3000')
await page.getByLabel('浏览器访问地址').fill(outlineURL)
await page.getByLabel('Collection 链接或 UUID').fill(
  'a4296a54-2044-4529-ba86-d598a5322e06',
)
await page.getByLabel('API Token').fill('ol_api_e2e')
await page.getByRole('button', { name: '测试连接' }).click()
await expect(page.getByText('E2E 文档中心')).toBeVisible()
await page.getByRole('button', { name: '保存配置' }).click()
await expect(page.getByText('Outline 配置已保存')).toBeVisible()

await page.reload()
await expect(page.getByText('API Token 已配置')).toBeVisible()
await expect(page.getByLabel('API Token')).toHaveValue('')
await expect(page.getByDisplayValue(
  'a4296a54-2044-4529-ba86-d598a5322e06',
)).toBeVisible()
```

Then continue the existing knowledge-template and project-document flow unchanged.

- [ ] **Step 9: Commit the management UI**

```bash
git add frontend/src/modules/admin/types.ts \
  frontend/src/modules/admin/adminApi.ts \
  frontend/src/modules/admin/DocumentCenterPage.tsx \
  frontend/src/modules/admin/DocumentCenterConfiguration.test.tsx \
  frontend/src/modules/admin/AdminPage.test.tsx \
  frontend/e2e/outline-document-center.e2e.ts \
  frontend/src/styles/global.css
git commit -m "feat: configure Outline from document center"
```

---

### Task 6: Run full regression and live acceptance

**Files:**

- Verify: `backend/src`
- Verify: `frontend/src`
- Verify: `compose.yaml`
- Verify: `docs/operations/deployment.md`

**Interfaces:**

- Consumes: all previous task outputs.
- Produces: verified backend, frontend and local Compose behavior with no additional feature surface.

- [ ] **Step 1: Run the complete backend suite**

Run:

```bash
cd backend
mvn test
```

Expected: BUILD SUCCESS; all JUnit tests pass.

- [ ] **Step 2: Run the complete frontend suite and production build**

Run:

```bash
cd frontend
pnpm test -- --run
pnpm build
```

Expected: all Vitest tests pass and Vite build succeeds.

- [ ] **Step 3: Inspect for secret leaks and stale global access**

Run:

```bash
rg -n "getApiToken\\(|getCollectionId\\(|getPublicBaseUrl\\(|getBaseUrl\\(" \
  backend/src/main/java/com/zhilu/delivery/document \
  backend/src/main/java/com/zhilu/delivery/knowledge
rg -n "apiToken|Authorization|outline.apiToken" \
  backend/src/main/java/com/zhilu/delivery/document \
  frontend/src/modules/admin
```

Expected:

- `OutlineProperties` connection getters are used only by `OutlineConfigurationService`.
- `HttpOutlineClient` reads Token only from `OutlineConnection`.
- no response builder, view map, audit detail or frontend type contains an `apiToken` response field;
- request DTO/input types may contain `apiToken`.

- [ ] **Step 4: Run deterministic browser acceptance against mock Outline**

Run:

```bash
cd frontend
pnpm e2e -- outline-document-center.e2e.ts
```

Expected: the disposable Compose stack starts; the browser saves configuration through the admin page; the existing knowledge template, project document, external revision, export and retry flow all pass; the stack is cleaned up.

- [ ] **Step 5: Rebuild and start the local application**

Run:

```bash
cd ..
docker compose up -d --build backend frontend
docker compose ps
curl -fsS http://localhost:53990/actuator/health
```

Expected: backend and frontend are healthy; actuator returns `{"status":"UP"}`.

- [ ] **Step 6: Inspect the local page without exposing or replacing secrets**

Use the in-app browser with the existing admin session:

1. Open `http://localhost:53990/admin/document-center`.
2. Confirm the configuration card matches the current admin visual language.
3. Confirm service address, browser address and Collection fields do not overflow at desktop and narrow widths.
4. Confirm the API Token field is blank after load and only the configured/unconfigured status is visible.
5. Confirm initialization and migration actions are disabled when status is `NOT_CONFIGURED`, and available when status is `READY`.
6. Confirm no browser console error or white-screen transition occurs.

- [ ] **Step 7: Verify the worktree and final commit history**

Run:

```bash
git status --short --branch
git log --oneline -6
```

Expected: clean worktree on the implementation branch and one focused commit for each completed code task.
