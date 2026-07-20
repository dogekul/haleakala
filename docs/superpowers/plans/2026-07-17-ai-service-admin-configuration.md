# AI Service Administration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在系统管理中提供组织级 AI 服务配置、连接测试和加密保存，并让需求分类与商机文档生成在保存后立即使用新配置。

**Architecture:** 复用 `system_setting` 与 `SettingSecretCipher` 保存组织级 OpenAI 兼容连接，新增 `AiConfigurationService` 解析组织配置和环境变量回退。`OpenAiCompatibleClient` 每次调用接收组织 ID 并解析不可变连接快照；管理接口用同一客户端测试草稿配置，前端新增独立的 AI 服务管理页面。

**Tech Stack:** Java 8、Spring Boot 2.7.18、JdbcTemplate、JDK `HttpServer`、MySQL/H2、React 18、TypeScript、Ant Design、TanStack Query、JUnit 5、Mockito、MockMvc、Vitest。

## Global Constraints

- 配置按当前登录用户的 `organizationId` 隔离。
- 保存后立即生效，不重启后端。
- API Key 只允许设置或覆盖；读取接口不得返回明文或密文。
- API Key 输入留空表示保留当前有效密钥。
- 环境变量继续作为组织未保存字段时的兼容回退。
- 保存前必须验证服务、认证、模型和 JSON Schema 结构化输出兼容性。
- 不新增数据库表或第三方依赖。
- 不实现多模型路由、自动降级、调用统计、向量模型或密钥跨项目复制。
- 每个行为先写失败测试并确认失败原因，再写最小实现。

---

## File Structure

### Backend

- `backend/src/main/java/com/zhilu/delivery/automation/AiConnection.java`：一次 AI 调用使用的不可变组织连接快照。
- `backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationDraft.java`：管理页草稿连接与密钥替换标记。
- `backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationService.java`：组织设置解析、校验、脱敏读取和加密保存。
- `backend/src/main/java/com/zhilu/delivery/automation/AiClient.java`：组织级结构化补全接口及草稿连接测试接口。
- `backend/src/main/java/com/zhilu/delivery/automation/OpenAiCompatibleClient.java`：OpenAI 兼容 HTTP 请求、响应解析和稳定错误映射。
- `backend/src/main/java/com/zhilu/delivery/automation/AiServiceException.java`：AI 上游错误类型与安全消息。
- `backend/src/main/java/com/zhilu/delivery/automation/AiAdminController.java`：配置读取、连接测试和验证保存接口。
- `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`：分类调用传入需求组织 ID。
- `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentService.java`：生成调用传入商机组织 ID。
- `backend/src/main/java/com/zhilu/delivery/common/error/GlobalExceptionHandler.java`：AI 错误映射为稳定 API 错误。

### Frontend

- `frontend/src/modules/admin/AiServicePage.tsx`：AI 配置、测试和保存页面。
- `frontend/src/modules/admin/AiServicePage.test.tsx`：页面行为测试。
- `frontend/src/modules/admin/types.ts`：AI 配置与测试结果类型。
- `frontend/src/modules/admin/adminApi.ts`：AI 管理接口调用。
- `frontend/src/modules/admin/adminApi.test.ts`：请求方法和载荷测试。
- `frontend/src/modules/admin/AdminPage.tsx`：新增 AI 服务导航和路由。
- `frontend/src/modules/admin/AdminPage.test.tsx`：导航与路由测试。
- `frontend/src/styles/global.css`：复用管理页视觉体系所需的少量响应式样式。

---

### Task 1: Build organization-level AI configuration resolution

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/automation/AiConnection.java`
- Create: `backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationDraft.java`
- Create: `backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/automation/AiConfigurationServiceTest.java`

**Interfaces:**

- Produces `AiConnection resolve(long organizationId)`.
- Produces `Map<String, Object> view(long organizationId)`.
- Produces `AiConfigurationDraft draft(long organizationId, String baseUrl, String model, String apiKey)`.
- Produces `Map<String, Object> saveValidated(long organizationId, AiConfigurationDraft draft)`.
- Uses setting keys `ai.baseUrl`, `ai.model`, `ai.apiKey`.

- [ ] **Step 1: Write failing configuration tests**

Create a Spring Boot H2 test configured with environment fallbacks:

```java
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

  @BeforeEach void seed() {
    jdbc.update("delete from system_setting");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values "
        + "(9100,'组织一','AI-ONE'),(9200,'组织二','AI-TWO')");
  }

  @Test void resolvesEnvironmentFallbackAndReturnsMaskedView() {
    AiConnection value = configurations.resolve(9100);
    assertEquals("http://ai.env/v1", value.getBaseUrl());
    assertEquals("env-model", value.getModel());
    assertEquals("env-secret", value.getApiKey());
    assertEquals("ENVIRONMENT", value.getSource());
    Map<String, Object> view = configurations.view(9100);
    assertEquals(Boolean.TRUE, view.get("apiKeyConfigured"));
    assertFalse(view.containsKey("apiKey"));
  }

  @Test void savesEncryptedOrganizationSettingsWithoutAffectingAnotherOrganization() {
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

  @Test void blankApiKeyKeepsTheEffectiveExistingSecret() {
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://ai.example.com", "model-one", "stored-secret"));
    configurations.saveValidated(9100, configurations.draft(
        9100, "https://ai.example.com/v1", "model-two", ""));
    assertEquals("stored-secret", configurations.resolve(9100).getApiKey());
    assertEquals("model-two", configurations.resolve(9100).getModel());
  }

  @Test void rejectsMissingSecretAndUnsafeUrls() {
    jdbc.update("delete from system_setting");
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://user:pass@ai.example.com", "model", "key"));
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://ai.example.com?x=1", "model", "key"));
    assertThrows(IllegalArgumentException.class,
        () -> configurations.draft(9100, "https://ai.example.com", " ", "key"));
  }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && mvn -q -Dtest=AiConfigurationServiceTest test`

Expected: compilation fails because the three AI configuration types do not exist.

- [ ] **Step 3: Implement immutable connection and draft types**

`AiConnection` exposes organization ID, normalized Base URL, model, API Key, source, plus:

```java
public boolean isConfigured() {
  return !blank(baseUrl) && !blank(model) && !blank(apiKey);
}
```

`AiConfigurationDraft` exposes the resolved `AiConnection` and `boolean apiKeyChanged`.

- [ ] **Step 4: Implement minimal configuration service**

Use constructor dependencies `JdbcTemplate`, `SettingSecretCipher`, and the three existing `delivery.ai.*` values. Query only `setting_key like 'ai.%'`. Decrypt `ai.apiKey` only when `encrypted=true`; reject plaintext secret rows. Normalize URLs through `java.net.URI`, require a root path or `/v1`, remove a trailing slash, and reject credentials/query/fragment. `saveValidated` upserts Base URL and model as plaintext and only upserts encrypted API Key when `apiKeyChanged` is true.

The view shape must be exactly:

```java
result.put("baseUrl", connection.getBaseUrl());
result.put("model", connection.getModel());
result.put("apiKeyConfigured", !blank(connection.getApiKey()));
result.put("source", connection.getSource());
```

- [ ] **Step 5: Verify GREEN**

Run: `cd backend && mvn -q -Dtest=AiConfigurationServiceTest test`

Expected: all `AiConfigurationServiceTest` tests pass.

- [ ] **Step 6: Commit configuration foundation**

```bash
git add backend/src/main/java/com/zhilu/delivery/automation/AiConnection.java \
  backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationDraft.java \
  backend/src/main/java/com/zhilu/delivery/automation/AiConfigurationService.java \
  backend/src/test/java/com/zhilu/delivery/automation/AiConfigurationServiceTest.java
git commit -m "feat: store organization AI configuration"
```

---

### Task 2: Make AI calls organization-aware and map upstream failures

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/automation/AiServiceException.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/automation/AiClient.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/automation/OpenAiCompatibleClient.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/automation/OpenAiCompatibleClientTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementAiOrganizationTest.java`

**Interfaces:**

- Change to `JsonNode completeJson(long organizationId, String systemPrompt, String userPrompt, JsonNode schema)`.
- Add `JsonNode completeJson(AiConnection connection, String systemPrompt, String userPrompt, JsonNode schema)` for admin connection testing.
- `AiServiceException.Type`: `AUTHENTICATION`, `MODEL_UNAVAILABLE`, `INCOMPATIBLE_RESPONSE`, `TIMEOUT`, `UNAVAILABLE`.

- [ ] **Step 1: Write failing client tests against a local HTTP stub**

Extend `OpenAiCompatibleClientTest` so it mocks `AiConfigurationService.resolve(1)` and uses an in-process HTTP server. Assert that a successful request targets `/v1/chat/completions`, includes `Bearer test-key`, sends model and `response_format.type=json_schema`, and parses `choices[0].message.content`.

Add one focused test for each mapping:

```java
assertEquals(AiServiceException.Type.AUTHENTICATION, failureFor(401).getType());
assertEquals(AiServiceException.Type.MODEL_UNAVAILABLE, failureFor(404).getType());
assertEquals(AiServiceException.Type.INCOMPATIBLE_RESPONSE,
    failureForBody(200, "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}").getType());
```

Keep `missingConfigurationIsExplicit`, but mock `resolve(1)` to return an incomplete connection and call `completeJson(1, ...)`.

- [ ] **Step 2: Verify client tests fail for the missing organization API**

Run: `cd backend && mvn -q -Dtest=OpenAiCompatibleClientTest test`

Expected: compilation fails because `AiClient` and `OpenAiCompatibleClient` do not accept organization ID or `AiConnection`.

- [ ] **Step 3: Implement organization-aware client and safe errors**

Inject `AiConfigurationService` into `OpenAiCompatibleClient`. The organization overload resolves a connection and delegates to the connection overload. Build the endpoint from the passed connection. Catch `HttpStatusCodeException` without copying its response body; map 401/403 to `AUTHENTICATION`, 404 to `MODEL_UNAVAILABLE`, other 4xx/5xx to `UNAVAILABLE`. Catch `ResourceAccessException`; map `SocketTimeoutException` in the cause chain to `TIMEOUT`, otherwise `UNAVAILABLE`. Missing/invalid content maps to `INCOMPATIBLE_RESPONSE`.

Add `GlobalExceptionHandler.handleAiService` returning HTTP 502 with codes `AI_AUTHENTICATION`, `AI_MODEL_UNAVAILABLE`, `AI_INCOMPATIBLE_RESPONSE`, `AI_TIMEOUT`, or `AI_UNAVAILABLE` and the fixed safe Chinese message stored by the exception.

- [ ] **Step 4: Write failing organization propagation tests**

Update opportunity mocks to four arguments:

```java
when(ai.completeJson(eq(3100L), anyString(), anyString(), any())).thenReturn(generated);
verify(ai).completeJson(eq(3100L), anyString(), prompt.capture(), any());
```

Create `RequirementAiOrganizationTest` with H2 seed data where requirement `701` belongs to organization `4100`; invoke `classify(701, actorId)` and verify:

```java
verify(ai).completeJson(eq(4100L), anyString(), anyString(), any());
```

- [ ] **Step 5: Verify propagation tests fail, then implement minimal call-site changes**

Run: `cd backend && mvn -q -Dtest=OpportunityStageDocumentServiceTest,RequirementAiOrganizationTest test`

Expected RED: current services call the three-argument interface.

Change opportunity generation to `ai.completeJson(organizationId, ...)`. In requirement classification, read `organizationId` from the requirement map and pass it as the first argument.

- [ ] **Step 6: Verify Task 2 GREEN**

Run: `cd backend && mvn -q -Dtest=OpenAiCompatibleClientTest,OpportunityStageDocumentServiceTest,RequirementAiOrganizationTest test`

Expected: all listed tests pass.

- [ ] **Step 7: Commit runtime AI routing**

```bash
git add backend/src/main/java/com/zhilu/delivery/automation \
  backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java \
  backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentService.java \
  backend/src/main/java/com/zhilu/delivery/common/error/GlobalExceptionHandler.java \
  backend/src/test/java/com/zhilu/delivery/automation/OpenAiCompatibleClientTest.java \
  backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentServiceTest.java \
  backend/src/test/java/com/zhilu/delivery/requirement/RequirementAiOrganizationTest.java
git commit -m "feat: route AI calls by organization"
```

---

### Task 3: Add protected AI administration APIs

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/automation/AiAdminController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/automation/AiAdminControllerTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`

**Interfaces:**

- `GET /api/v1/admin/ai-service/config`.
- `POST /api/v1/admin/ai-service/config/test`.
- `PUT /api/v1/admin/ai-service/config`.

- [ ] **Step 1: Write failing controller tests**

Use `@WebMvcTest(AiAdminController.class)` with mocked `AiConfigurationService`, `AiClient`, and `AuditService`. Cover:

```java
mvc.perform(get("/api/v1/admin/ai-service/config").with(admin()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.apiKeyConfigured").value(true))
    .andExpect(jsonPath("$.apiKey").doesNotExist());
```

For test and save, send:

```json
{"baseUrl":"https://ai.example.com/v1","model":"qwen-plus","apiKey":"new-secret"}
```

Verify the test endpoint calls `configurations.draft(...)` and `ai.completeJson(draft.getConnection(), ...)` but never `saveValidated`. Verify the PUT endpoint calls the same AI validation before `saveValidated`, returns the masked view, and records audit details containing model and `apiKeyReplaced=true` but not `new-secret`.

Add rejection tests for unknown JSON fields and a test proving an AI validation exception prevents saving.

- [ ] **Step 2: Verify controller tests fail because the controller is missing**

Run: `cd backend && mvn -q -Dtest=AiAdminControllerTest test`

Expected: compilation failure because `AiAdminController` does not exist.

- [ ] **Step 3: Implement the controller**

Use a nested validated request DTO with `@NotBlank baseUrl`, `@NotBlank model`, optional `apiKey`, and `@JsonAnySetter` unknown-field rejection matching `DocumentAdminController`. Build a fixed test schema:

```java
ObjectNode schema = json.createObjectNode();
schema.put("type", "object");
schema.putObject("properties").putObject("status")
    .put("type", "string").putArray("enum").add("ok");
schema.putArray("required").add("status");
schema.put("additionalProperties", false);
```

Require the response field `status` to equal `ok`. Return `{status: "READY", model: connection.getModel()}` from test. On PUT, repeat validation, then save and audit `AI_CONFIGURATION` without any secret material.

- [ ] **Step 4: Add authorization regression**

In `SecurityAccessTest.auditReadDoesNotGrantOtherAdministrationAccess`, assert an `audit:read` actor receives 403 for `GET /api/v1/admin/ai-service/config`. Existing `/api/v1/admin/**` security should make the assertion pass once the controller exists.

- [ ] **Step 5: Verify Task 3 GREEN**

Run: `cd backend && mvn -q -Dtest=AiAdminControllerTest,SecurityAccessTest test`

Expected: all listed tests pass.

- [ ] **Step 6: Commit admin APIs**

```bash
git add backend/src/main/java/com/zhilu/delivery/automation/AiAdminController.java \
  backend/src/test/java/com/zhilu/delivery/automation/AiAdminControllerTest.java \
  backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java
git commit -m "feat: manage AI configuration through admin API"
```

---

### Task 4: Build the AI service administration page

**Files:**

- Create: `frontend/src/modules/admin/AiServicePage.tsx`
- Create: `frontend/src/modules/admin/AiServicePage.test.tsx`
- Modify: `frontend/src/modules/admin/types.ts`
- Modify: `frontend/src/modules/admin/adminApi.ts`
- Modify: `frontend/src/modules/admin/adminApi.test.ts`
- Modify: `frontend/src/modules/admin/AdminPage.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.test.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**

```ts
export interface AiConfiguration {
  baseUrl: string
  model: string
  apiKeyConfigured: boolean
  source: 'ENVIRONMENT' | 'ORGANIZATION' | 'MIXED'
}

export interface AiConfigurationInput {
  baseUrl: string
  model: string
  apiKey: string
}

export interface AiConnectionTest {
  status: 'READY'
  model: string
}
```

- [ ] **Step 1: Write failing API and navigation tests**

Extend `adminApi.test.ts` to call `testAiConfiguration(input)` and `saveAiConfiguration(input)` and assert POST `/api/v1/admin/ai-service/config/test` and PUT `/api/v1/admin/ai-service/config` with identical JSON bodies.

Update the Admin page test to expect six links including `AI 服务`, click it, and assert the `AI 服务` heading. Stub `GET /api/v1/admin/ai-service/config` with a masked configuration.

- [ ] **Step 2: Verify RED**

Run: `cd frontend && pnpm test:run src/modules/admin/adminApi.test.ts src/modules/admin/AdminPage.test.tsx`

Expected: TypeScript/test failures because the types, API methods, route and page do not exist.

- [ ] **Step 3: Add types, API calls and route**

Add the exact interfaces above, implement:

```ts
aiConfiguration: () => api<AiConfiguration>('/api/v1/admin/ai-service/config'),
testAiConfiguration: (input: AiConfigurationInput) => api<AiConnectionTest>(
  '/api/v1/admin/ai-service/config/test',
  { method: 'POST', body: JSON.stringify(input) },
),
saveAiConfiguration: (input: AiConfigurationInput) => api<AiConfiguration>(
  '/api/v1/admin/ai-service/config',
  { method: 'PUT', body: JSON.stringify(input) },
),
```

Add `RobotOutlined`, the `ai-service` section immediately after document center, and `<Route path="ai-service" element={<AiServicePage />} />`.

- [ ] **Step 4: Write failing page behavior tests**

Create `AiServicePage.test.tsx` with a QueryClient and mocked fetch. Cover:

- Loaded Base URL/model and the text `API Key 已配置`.
- API Key input is empty and displays `留空则保持不变`.
- Client-side required validation for Base URL and model.
- `测试连接` sends the draft and shows `连接测试成功 · qwen-plus`.
- `保存配置` sends the draft, shows `AI 服务配置已保存`, clears the key input, and refetches configuration.
- A failed API response displays the backend safe message.
- Test and save buttons disable independently while their own request is pending.

- [ ] **Step 5: Verify page tests fail because the component is missing**

Run: `cd frontend && pnpm test:run src/modules/admin/AiServicePage.test.tsx`

Expected: compilation failure because `AiServicePage` does not exist.

- [ ] **Step 6: Implement the minimal responsive page**

Use `PageHeading`, `useQuery`, two independent `useMutation` calls, Ant Design `Card`, `Form`, `Input`, `Alert`, `Tag`, `Button`, and `Row/Col`. Use the same source labels as the document center. The Base URL validator must accept only HTTP(S) URLs without credentials/query/hash and whose path is empty, `/`, or `/v1`.

On load, populate only `baseUrl` and `model`; never populate `apiKey`. On successful test, keep the form values and show the returned model. On successful save, clear `apiKey`, invalidate `['ai-configuration']`, and show the success message.

Add these focused CSS rules:

```css
.ai-service-actions { display: flex; justify-content: flex-end; gap: 8px; }
.ai-service-status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
@media (max-width: 640px) {
  .ai-service-actions { align-items: stretch; flex-direction: column-reverse; }
  .ai-service-actions .ant-btn { width: 100%; }
}
```

- [ ] **Step 7: Verify Task 4 GREEN**

Run: `cd frontend && pnpm test:run src/modules/admin/AiServicePage.test.tsx src/modules/admin/adminApi.test.ts src/modules/admin/AdminPage.test.tsx`

Expected: all listed tests pass.

- [ ] **Step 8: Commit the administration page**

```bash
git add frontend/src/modules/admin/AiServicePage.tsx \
  frontend/src/modules/admin/AiServicePage.test.tsx \
  frontend/src/modules/admin/types.ts frontend/src/modules/admin/adminApi.ts \
  frontend/src/modules/admin/adminApi.test.ts frontend/src/modules/admin/AdminPage.tsx \
  frontend/src/modules/admin/AdminPage.test.tsx frontend/src/styles/global.css
git commit -m "feat: configure AI service in system administration"
```

---

### Task 5: Run full verification and restart the local stack

**Files:**

- Modify only files required to repair regressions found by the commands below.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn -q clean test`

Expected: all backend test suites pass with exit code 0.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend && pnpm test:run && pnpm build`

Expected: all Vitest files pass and Vite production build exits 0.

- [ ] **Step 3: Check repository and Compose configuration**

Run:

```bash
git diff --check
docker compose config >/tmp/zhilu-ai-compose.yml
```

Expected: both commands exit 0; Compose still exposes the environment fallback variables.

- [ ] **Step 4: Rebuild and start the current branch**

Run:

```bash
docker compose -p zhilu-delivery-main up -d --build backend frontend
docker compose -p zhilu-delivery-main ps
curl -fsS http://localhost:8082/actuator/health
curl -fsS -I http://localhost:53990/admin/ai-service
```

Expected: backend and frontend are running; health returns `UP`; frontend returns HTTP 200.

- [ ] **Step 5: Commit any verified regression fixes**

If Step 1–4 required code changes, stage only those files and commit:

```bash
git commit -m "test: verify AI service administration"
```

If no files changed, do not create an empty commit.
