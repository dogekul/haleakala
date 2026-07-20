# Outline Unified Document Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将私有化 Outline 接入为知识库与项目文档的唯一正文、目录和修订中心，自动建立项目七阶段目录与模版副本，并完成系统内编辑预览、确认门禁、四种导出、历史迁移和失败重试。

**Architecture:** 后端新增 `document` 包统一封装 Outline RPC API、业务映射、初始化任务、迁移和导出；MySQL 只保存业务关系、缓存、版本确认及任务状态，Outline 保存 Markdown 正文与修订。知识库和项目模块通过 `DocumentCenterService` 使用同一文档能力，React 复用一个文档编辑/预览组件，浏览器永远不接触 Outline API Key。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、Flyway、MySQL/H2、Spring `RestTemplate`、CommonMark 0.21、OpenHTMLToPDF 1.0.10、Apache POI 5.5.1、React 18、TypeScript、Ant Design、React Query、Vitest、Playwright。

## Global Constraints

- Outline API 只从后端调用，`OUTLINE_API_TOKEN` 不入库、不写日志、不返回前端。
- 只管理系统创建或明确绑定的 Outline 文档；不按标题扫描或自动导入整个集合。
- Outline 文档 `revision` 是并发保存、模版发布和项目文档确认的唯一版本依据。
- 创建项目的本地事务不能依赖 Outline 可用性；只入队初始化任务并返回项目。
- 目录是 Outline 索引文档，通过 `parentDocumentId` 建立层级；所有关系通过本地映射 ID 识别。
- 现有 `knowledge_item.content_text`、`template_instance` 只在迁移完成前作为只读回退，不再成为新正文来源。培训附件继续使用现有 MinIO 文件中心。
- 文档模版后续修改不覆盖既有项目副本；项目记录固化来源模版与发布修订号。
- 阶段推进由后端实时读取必需文档修订并检查确认状态；`BLOCK` 阻断，`WARNING` 记录清单后允许推进。
- 每个行为先写失败测试，再写最小实现；每完成一个任务运行指定测试并提交。
- 不实现实时协同、评论同步、项目 ZIP 整包导出或 Outline 用户同步。

---

### Task 1: 建立文档中心数据库与配置基线

**Files:**

- Create: `backend/src/main/resources/db/migration/V14__outline_document_center.sql`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/OutlinePropertiesTest.java`

- [ ] **Step 1: 写失败的迁移契约测试**

在 `SchemaBaselineTest` 新增 `outlineDocumentCenterSchemaIsInstalled()`，断言：

```java
assertEquals(Integer.valueOf(4), jdbc.queryForObject(
    "select count(*) from information_schema.tables where table_schema='public' "
        + "and table_name in ('outline_document_link','document_template_config',"
        + "'project_document','document_job')", Integer.class));
assertEquals(Integer.valueOf(1), jdbc.queryForObject(
    "select count(*) from information_schema.columns where table_schema='public' "
        + "and table_name='knowledge_item' and column_name='outline_link_id'", Integer.class));
assertEquals(Integer.valueOf(1), jdbc.queryForObject(
    "select count(*) from information_schema.columns where table_schema='public' "
        + "and table_name='delivery_project' and column_name='document_space_status'", Integer.class));
```

继续断言 `uk_outline_business_key`、`uk_project_document_template`、`uk_document_job_business_key` 三个唯一约束存在。

- [ ] **Step 2: 写失败的配置绑定测试**

```java
@SpringBootTest(properties = {
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
class OutlinePropertiesTest {
  @Autowired OutlineProperties properties;

  @Test void bindsOutlineConnectionAndRetrySettings() {
    assertEquals("http://outline.test", properties.getBaseUrl());
    assertEquals(5, properties.getMaxAttempts());
  }
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest,OutlinePropertiesTest test`

Expected: FAIL，缺少 V14 表、字段和配置类。

- [ ] **Step 4: 写最小迁移**

`V14__outline_document_center.sql` 创建：

```sql
CREATE TABLE outline_document_link (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  business_key VARCHAR(180) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  outline_collection_id VARCHAR(64) NOT NULL,
  outline_document_id VARCHAR(64) NULL,
  outline_url_id VARCHAR(64) NULL,
  parent_link_id BIGINT NULL,
  title_cache VARCHAR(240) NOT NULL,
  summary_cache VARCHAR(1000) NULL,
  revision BIGINT NULL,
  outline_updated_at TIMESTAMP(6) NULL,
  sync_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_outline_business_key UNIQUE (organization_id,business_key),
  CONSTRAINT fk_outline_link_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_outline_link_parent FOREIGN KEY (parent_link_id) REFERENCES outline_document_link(id)
);
```

`document_template_config` 以 `knowledge_item_id` 为主键，保存 `stage_code`、`requirement`、`enabled`、`published_revision`；`project_document` 保存项目、阶段、来源模版、Outline 映射、必需性、状态、确认人/时间/修订；`document_job` 保存 `ROOT_INIT`、`PROJECT_INIT`、`KNOWLEDGE_MIGRATION`、`PROJECT_MIGRATION` 的业务键、状态、次数、下次执行时间和错误。为 `knowledge_item` 增加 `outline_link_id`，为 `delivery_project` 增加 `document_space_status` 与 `document_space_error`。

- [ ] **Step 5: 写配置类和默认值**

`OutlineProperties` 使用 `@ConfigurationProperties("delivery.outline")`，字段：

```java
private String baseUrl = "http://localhost:3000";
private String apiToken = "";
private String collectionId = "";
private Duration connectTimeout = Duration.ofSeconds(3);
private Duration readTimeout = Duration.ofSeconds(10);
private int maxAttempts = 5;
private Duration initialBackoff = Duration.ofSeconds(30);
```

在 `application.yml` 映射 `OUTLINE_BASE_URL`、`OUTLINE_API_TOKEN`、`OUTLINE_COLLECTION_ID`、超时与重试环境变量；通过 `@EnableConfigurationProperties(OutlineProperties.class)` 启用。

- [ ] **Step 6: 运行测试**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest,OutlinePropertiesTest test`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/resources/db/migration/V14__outline_document_center.sql \
  backend/src/main/java/com/zhilu/delivery/document/OutlineProperties.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java \
  backend/src/test/java/com/zhilu/delivery/document/OutlinePropertiesTest.java
git commit -m "feat: add Outline document center schema"
```

### Task 2: 实现 Outline API 客户端与错误映射

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineClient.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/HttpOutlineClient.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineDocument.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/OutlineException.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/HttpOutlineClientTest.java`

- [ ] **Step 1: 用 JDK `HttpServer` 写失败的客户端测试**

覆盖：

```java
OutlineDocument created = client.create("标题", "# 正文", collectionId, null, true);
assertEquals(1L, created.getRevision());
assertEquals("Bearer ol_api_test", receivedAuthorization.get());

OutlineDocument updated = client.update(documentId, "新标题", "新正文");
assertEquals(2L, updated.getRevision());
```

继续覆盖 `documents.info`、`documents.list(parentDocumentId)`、`documents.export`、401 映射 `AUTHENTICATION`、429 映射 `RATE_LIMIT`、5xx 映射 `UNAVAILABLE`、连接超时映射 `TIMEOUT`。响应解析使用官方 RPC 包装 `{ "data": ... }`。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=HttpOutlineClientTest test`

Expected: FAIL，客户端类型不存在。

- [ ] **Step 3: 定义最小接口**

```java
public interface OutlineClient {
  OutlineDocument create(String title, String text, String collectionId,
      String parentDocumentId, boolean publish);
  OutlineDocument info(String documentId);
  List<OutlineDocument> children(String parentDocumentId);
  OutlineDocument update(String documentId, String title, String text);
  String exportMarkdown(String documentId);
  boolean isConfigured();
}
```

`OutlineDocument` 只包含业务实际需要的 `id`、`collectionId`、`parentDocumentId`、`title`、`text`、`url`、`urlId`、`revision`、`updatedAt`。

- [ ] **Step 4: 实现 HTTP RPC**

`HttpOutlineClient` 使用一个带连接/读取超时的 `RestTemplate`，所有请求：

```java
headers.setBearerAuth(properties.getApiToken());
headers.setContentType(MediaType.APPLICATION_JSON);
post("/api/documents.info", body);
```

`documents.create` 发送 `title/text/collectionId/parentDocumentId/publish`；`documents.update` 发送 `id/title/text`；`documents.list` 发送 `parentDocumentId/limit=100/statusFilter=["published"]`。错误消息不拼接 token 或完整请求头。

- [ ] **Step 5: 运行测试**

Run: `cd backend && mvn -q -Dtest=HttpOutlineClientTest test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/document \
  backend/src/test/java/com/zhilu/delivery/document/HttpOutlineClientTest.java
git commit -m "feat: add Outline API client"
```

### Task 3: 实现统一映射、目录与文档读写服务

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentView.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentCenterServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentApiIT.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`

- [ ] **Step 1: 写映射幂等、读写冲突和组织隔离的失败测试**

使用内存 `FakeOutlineClient`，断言：

```java
long first = documents.ensureIndex(orgId, "KNOWLEDGE_ROOT", "知识库", null);
long second = documents.ensureIndex(orgId, "KNOWLEDGE_ROOT", "知识库", null);
assertEquals(first, second);
assertEquals(1, outline.createdDocuments());

DocumentView current = documents.readKnowledge(itemId, user);
assertThrows(ConflictException.class, () ->
    documents.updateKnowledge(itemId, "标题", "# 覆盖", current.getRevision() - 1, user));
```

继续断言同业务键不会重复创建、Outline 失败时映射为 `FAILED`、跨组织按 404、读取成功刷新标题/修订缓存、保存成功返回新修订。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=DocumentCenterServiceTest,DocumentApiIT test`

Expected: FAIL，服务与接口不存在。

- [ ] **Step 3: 实现映射状态机**

`DocumentCenterService` 暴露：

```java
long ensureIndex(long organizationId, String businessKey, String title, Long parentLinkId);
long createDocument(long organizationId, String businessKey, String purpose,
    String title, String markdown, long parentLinkId);
DocumentView readLink(long linkId);
DocumentView updateLink(long linkId, String title, String markdown, long expectedRevision);
String outlineUrl(DocumentView document);
```

`ensureIndex` 先按 `(organization_id,business_key)` 加载映射；有 `outline_document_id` 时调用 `info` 刷新，不存在时创建；数据库唯一约束兜底并发重复。任何 Outline 错误只更新 `sync_status/last_error/retry_count` 后重新抛出业务异常。

- [ ] **Step 4: 实现业务文档 API**

`DocumentController` 提供：

```text
GET  /api/v1/knowledge/{id}/document
PUT  /api/v1/knowledge/{id}/document
GET  /api/v1/projects/{projectId}/documents/{documentId}
PUT  /api/v1/projects/{projectId}/documents/{documentId}
```

更新请求为 `{ title, markdown, revision }`。控制器分别复用知识与项目权限校验，不接受前端传任意 Outline ID。

- [ ] **Step 5: 补安全规则并运行测试**

Run: `cd backend && mvn -q -Dtest=DocumentCenterServiceTest,DocumentApiIT,SecurityAccessTest test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/document \
  backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java \
  backend/src/test/java/com/zhilu/delivery/document
git commit -m "feat: add unified document read and write"
```

### Task 4: 将知识库正文迁入 Outline 并新增文档模版类型

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeController.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeTemplateApiIT.java`

- [ ] **Step 1: 写失败的知识与模版测试**

覆盖：

- `TEMPLATE` 是合法第四种类型。
- 新建知识先建立本地草稿，再在 `知识库/<类型目录>` 创建 Outline 文档并绑定。
- API 返回 `documentStatus`、`documentRevision`、`outlineUrl`，不再把本地 `content_text` 当成功正文。
- Outline 失败时草稿保留为 `PENDING`，保存接口可重试。
- 模版必须设置 `stageCode`、`requirement=REQUIRED|OPTIONAL`、`enabled`。
- 发布模版前读取 Outline 最新正文并记录 `published_revision`；正文为空不能发布。
- 普通知识发布同样要求 Outline 文档可读且正文有效。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=KnowledgeServiceTest,KnowledgeTemplateApiIT test`

Expected: FAIL，类型和 Outline 绑定尚未实现。

- [ ] **Step 3: 改造知识服务**

`TYPES` 增加 `TEMPLATE`。`create/update` 继续保存元数据和迁移回退正文，但正文读写委托 `DocumentCenterService`；业务键使用 `KNOWLEDGE:<id>`。类型目录业务键固定：

```text
KNOWLEDGE_ROOT
KNOWLEDGE_TYPE:CASE
KNOWLEDGE_TYPE:CODE
KNOWLEDGE_TYPE:TRAINING
KNOWLEDGE_TYPE:TEMPLATE
```

模版配置写入 `document_template_config`。知识列表只返回缓存摘要，详情正文通过独立文档 API 获取，避免列表逐条调用 Outline。

- [ ] **Step 4: 发布时固化修订**

`publish` 调用 `readLink`，非空后将知识状态设为 `PUBLISHED`；`TEMPLATE` 同事务写 `published_revision=document.revision`，并保存只用于异步复制的不可变标题/Markdown 快照。模版被再次编辑时知识状态回到 `DRAFT` 并清空发布快照，但已创建项目的副本输入不变。

- [ ] **Step 5: 运行测试**

Run: `cd backend && mvn -q -Dtest=KnowledgeServiceTest,KnowledgeTemplateApiIT test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/knowledge \
  backend/src/test/java/com/zhilu/delivery/knowledge
git commit -m "feat: store knowledge documents in Outline"
```

### Task 5: 实现项目文档空间初始化、模版复制与重试

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentJobService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectView.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentInitializationTest.java`

- [ ] **Step 1: 写失败的创建与初始化测试**

断言：

```java
ProjectView project = projects.create(command("PRJ-701"));
assertEquals("PENDING", project.getDocumentSpaceStatus());
assertEquals(1, countJobs("PROJECT_INIT", "PROJECT:701"));
```

执行任务后断言项目目录标题为 `PRJ-701 华东银行核心系统交付`，七个阶段目录按固定中文标题创建，启用且发布的模版被复制到对应阶段，`source_template_revision` 固化，停用/草稿模版不复制。重复执行任务后 Outline 创建数不增加。

继续覆盖 Outline 不可用时项目仍创建成功、任务进入 `RETRY`、达到上限进入 `FAILED`、手动重试恢复成功。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=ProjectLifecycleTest,ProjectDocumentInitializationTest test`

Expected: FAIL，项目未入队且无文档空间。

- [ ] **Step 3: 创建任务并实现后台执行**

项目事务末尾插入唯一任务：

```java
documentJobs.enqueue("PROJECT_INIT", projectId, "PROJECT:" + projectId);
```

`DocumentJobService` 使用 `@Scheduled(fixedDelayString=...)` 每批读取到期的 `PENDING/RETRY` 任务，通过条件更新抢占为 `RUNNING` 并写入租约令牌和到期时间。执行期间定时续租；只有过期租约可被回收，完成/失败写入必须匹配原令牌。执行失败按 `initialBackoff * 2^(attempt-1)` 计算 `next_attempt_at`。测试直接调用 `runDueJobs()`，不等待定时器。

- [ ] **Step 4: 创建项目目录与七阶段目录**

业务键：

```text
PROJECT_ROOT
PROJECT:<projectId>
PROJECT:<projectId>:STAGE:<stageCode>
PROJECT:<projectId>:DOC:<templateId>
```

项目创建事务先把适用模版的 ID、发布修订、阶段、必需性和发布正文快照写入 `project_document`；项目和阶段目录是发布的索引文档，后台任务只复制这份不可变快照。初始化成功更新 `delivery_project.document_space_status='READY'`。

V15→V16 升级测试保留真实旧表数据并验证迁移。旧发布模版或待初始化项目缺失快照时，运行时读取其 Outline 源文档并严格比较原修订；一致才回填快照，不一致则以可操作错误中止，防止必需模版被过滤或 SQL `NULL` 被复制为文本 `"null"`。

- [ ] **Step 5: 暴露手动重试**

新增：

```text
POST /api/v1/projects/{id}/documents/retry
```

仅项目成员或跨项目管理者可调用，将失败任务恢复为立即执行的 `PENDING`。

- [ ] **Step 6: 运行测试**

Run: `cd backend && mvn -q -Dtest=ProjectLifecycleTest,ProjectDocumentInitializationTest test`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/document \
  backend/src/main/java/com/zhilu/delivery/project \
  backend/src/test/java/com/zhilu/delivery/project \
  backend/src/test/java/com/zhilu/delivery/document
git commit -m "feat: initialize project documents from Outline templates"
```

### Task 6: 实现项目文档状态、人工确认与阶段门禁

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentLifecycleIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`

- [ ] **Step 1: 写失败的状态和门禁测试**

覆盖：

- 空白/仅模版提示正文为 `TODO`，有有效内容为 `PENDING_CONFIRMATION`。
- 项目经理确认当前修订后为 `COMPLETED`，其他成员无权确认。
- Outline 中直接修改导致 `revision != confirmed_revision`，读取时自动回到 `PENDING_CONFIRMATION`。
- 当前阶段有未完成必需文档时 `BLOCK` 返回 409 和完整文档清单。
- `WARNING` 模式允许推进并把清单写入 `project_activity.details_text`。
- 可选文档不阻断。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=ProjectDocumentLifecycleIT,ProjectLifecycleTest test`

Expected: FAIL，确认和文档门禁不存在。

- [ ] **Step 3: 实现状态刷新与确认**

`ProjectDocumentService` 提供：

```java
List<Map<String, Object>> list(long projectId, CurrentUser user);
Map<String, Object> confirm(long projectId, long projectDocumentId, CurrentUser user);
List<Map<String, Object>> incompleteRequired(long projectId, DeliveryStage stage);
```

每次详情读取和门禁检查调用 Outline `info`；若确认修订失效，清空 `confirmed_by/confirmed_at/confirmed_revision`。确认人必须是 `delivery_project.manager_user_id` 或具备 `system:manage`。

- [ ] **Step 4: 接入推进门禁**

在旧 `stage_instance.gate_status` 检查之外调用 `incompleteRequired`。`gate_mode=BLOCK` 时抛出包含标题列表的 `ConflictException`；`WARNING` 时推进并审计。请求体中的 `mode` 不再绕过持久化门禁模式。

- [ ] **Step 5: 暴露确认 API**

```text
GET  /api/v1/projects/{id}/documents
POST /api/v1/projects/{id}/documents/{documentId}/confirm
```

- [ ] **Step 6: 运行测试**

Run: `cd backend && mvn -q -Dtest=ProjectDocumentLifecycleIT,ProjectLifecycleTest test`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/document \
  backend/src/main/java/com/zhilu/delivery/project \
  backend/src/test/java/com/zhilu/delivery/document \
  backend/src/test/java/com/zhilu/delivery/project
git commit -m "feat: enforce project document completion gates"
```

### Task 7: 实现 Markdown、HTML、PDF 和 Word 单篇导出

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/zhilu/delivery/document/MarkdownRenderer.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentExportService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentExportServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentExportApiIT.java`

- [ ] **Step 1: 写失败的格式测试**

同一篇含标题、段落、列表、表格和代码块的 Markdown，断言：

```java
assertTrue(new String(export.md(), UTF_8).contains("# 验收报告"));
assertTrue(new String(export.html(), UTF_8).contains("<table>"));
assertEquals("%PDF-", new String(export.pdf(), 0, 5, ISO_8859_1));
try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(export.docx()))) {
  assertTrue(word.getParagraphs().stream().anyMatch(p -> p.getText().contains("验收报告")));
}
```

API 测试继续断言 Content-Type、UTF-8 安全文件名、知识/项目权限隔离和 Outline 读取失败时不回退旧缓存。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=DocumentExportServiceTest,DocumentExportApiIT test`

Expected: FAIL，依赖和导出服务不存在。

- [ ] **Step 3: 添加最小依赖**

在 `backend/pom.xml` 添加：

```xml
<dependency>
  <groupId>org.commonmark</groupId>
  <artifactId>commonmark</artifactId>
  <version>0.21.0</version>
</dependency>
<dependency>
  <groupId>org.commonmark</groupId>
  <artifactId>commonmark-ext-gfm-tables</artifactId>
  <version>0.21.0</version>
</dependency>
<dependency>
  <groupId>com.openhtmltopdf</groupId>
  <artifactId>openhtmltopdf-pdfbox</artifactId>
  <version>1.0.10</version>
</dependency>
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.5.1</version>
</dependency>
```

- [ ] **Step 4: 实现安全渲染和导出**

`MarkdownRenderer` 启用 GFM 表格，`HtmlRenderer` 使用 `escapeHtml(true)` 与 `sanitizeUrls(true)`。HTML 输出完整 UTF-8 页面与飞书风格基础排版。PDF 用 OpenHTMLToPDF 渲染同一 HTML；Word 遍历 CommonMark AST 写入标题、段落、列表、表格和代码块。服务端不主动下载远程图片，避免 SSRF；需要完整附件包时使用 Outline 原生集合导出。

- [ ] **Step 5: 暴露导出接口**

```text
GET /api/v1/knowledge/{id}/document/export?format=md|html|pdf|docx
GET /api/v1/projects/{projectId}/documents/{documentId}/export?format=...
```

控制器设置 `Content-Disposition: attachment; filename*=UTF-8''...`。

- [ ] **Step 6: 运行测试**

Run: `cd backend && mvn -q -Dtest=DocumentExportServiceTest,DocumentExportApiIT test`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/pom.xml backend/src/main/java/com/zhilu/delivery/document \
  backend/src/test/java/com/zhilu/delivery/document
git commit -m "feat: export Outline documents in four formats"
```

### Task 8: 实现历史迁移、运行状态和管理接口

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentMigrationService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/document/DocumentAdminController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentMigrationServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/document/DocumentAdminControllerTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`

- [ ] **Step 1: 写失败的迁移幂等测试**

断言：

- 未绑定知识用 `content_text` 创建 Outline 文档并绑定，成功后详情只读 Outline。
- 已绑定知识重复迁移不再创建文档。
- 现有项目全部入队 `PROJECT_MIGRATION`，重复执行不重复任务。
- 单条失败不终止整批，状态统计包含 pending/running/success/failed。
- 未配置 token 时状态为 `NOT_CONFIGURED`，不会泄露 token。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=DocumentMigrationServiceTest,DocumentAdminControllerTest test`

Expected: FAIL，迁移与管理接口不存在。

- [ ] **Step 3: 实现迁移服务**

`DocumentMigrationService.startKnowledgeMigration(orgId)` 为未绑定知识创建唯一 `KNOWLEDGE_MIGRATION` 任务；任务成功前允许 `content_text` 只读回退，成功后不再从本地正文响应。项目迁移复用 Task 5 的初始化器，不建立第二套逻辑。

- [ ] **Step 4: 实现管理接口**

```text
GET  /api/v1/admin/document-center/status
POST /api/v1/admin/document-center/initialize
POST /api/v1/admin/document-center/migrate-knowledge
POST /api/v1/admin/document-center/migrate-projects
POST /api/v1/admin/document-center/jobs/{id}/retry
```

状态包含 Outline 连通性、集合 ID、两个根目录状态、任务计数和最近错误；不包含 token。

- [ ] **Step 5: 运行测试**

Run: `cd backend && mvn -q -Dtest=DocumentMigrationServiceTest,DocumentAdminControllerTest,SecurityAccessTest test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/document \
  backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java \
  backend/src/test/java/com/zhilu/delivery/document
git commit -m "feat: migrate legacy content to Outline"
```

### Task 9: 构建可复用的 React 文档编辑、预览和导出组件

**Files:**

- Create: `frontend/src/modules/document/types.ts`
- Create: `frontend/src/modules/document/documentApi.ts`
- Create: `frontend/src/modules/document/DocumentWorkspace.tsx`
- Create: `frontend/src/modules/document/DocumentWorkspace.test.tsx`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 写失败的组件测试**

用 MSW 等新依赖并不必要；沿用 Vitest mock `documentApi`，覆盖：

- 打开后加载 Markdown 和修订。
- 编辑/预览双模式切换。
- 保存提交加载时的 `revision`，409 显示刷新冲突提示且保留用户文本。
- “在 Outline 中打开”使用后端返回 URL。
- 导出菜单生成四种后端下载地址。
- 同步失败显示错误和重试按钮，不把空正文渲染成成功。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && pnpm test:run -- src/modules/document/DocumentWorkspace.test.tsx`

Expected: FAIL，组件不存在。

- [ ] **Step 3: 实现复用组件**

`DocumentWorkspace` props：

```ts
interface DocumentWorkspaceProps {
  title: string
  load(): Promise<DocumentContent>
  save(input: { title: string; markdown: string; revision: number }): Promise<DocumentContent>
  exportUrl(format: DocumentFormat): string
  canEdit: boolean
  onSaved?(): void
}
```

编辑区使用等宽字体；预览直接渲染后端返回的已转义 `renderedHtml`。顶部显示同步状态、修订号、保存按钮、Outline 链接和导出菜单。抽屉宽度至少 `min(1080px, 92vw)`。

- [ ] **Step 4: 添加飞书项目风格样式**

使用浅灰画布、白色正文纸张、蓝色主操作、细边框、紧凑工具栏；文档正文设 `max-width: 820px`、长单词/代码横向滚动、表格容器可滚动，避免知识卡片已有的文字溢出问题。

- [ ] **Step 5: 运行测试与构建**

Run: `cd frontend && pnpm test:run -- src/modules/document/DocumentWorkspace.test.tsx && pnpm build`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/modules/document frontend/src/styles/global.css
git commit -m "feat: add reusable document workspace"
```

### Task 10: 改造知识库前端并交付文档模版体验

**Files:**

- Modify: `frontend/src/modules/knowledge/types.ts`
- Modify: `frontend/src/modules/knowledge/knowledgeApi.ts`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.tsx`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.test.tsx`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 写失败的知识库交互测试**

覆盖：

- 出现“文档模版”页签、图标、计数与卡片。
- 创建模版时显示阶段、必需性、启用状态字段。
- 卡片正文不直接展开 Markdown，点击后进入 `DocumentWorkspace`。
- 培训与代码卡片固定高度、摘要两行截断、长文本不溢出。
- 未初始化和同步失败卡片显示状态，不显示伪正文。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && pnpm test:run -- src/modules/knowledge/KnowledgePage.test.tsx`

Expected: FAIL，第四类型和文档工作区尚未接入。

- [ ] **Step 3: 更新类型与 API**

`KnowledgeItem.type` 增加 `TEMPLATE`，增加：

```ts
documentStatus?: 'PENDING' | 'READY' | 'FAILED'
documentRevision?: number
outlineUrl?: string
stageCode?: keyof typeof stageNames
requirement?: 'REQUIRED' | 'OPTIONAL'
enabled?: boolean
publishedRevision?: number
```

`knowledgeApi` 增加文档加载、保存与导出 URL。

- [ ] **Step 4: 重构页面**

知识编辑器只维护元数据和初始 Markdown；创建成功后打开文档工作区继续编辑。详情抽屉使用 `DocumentWorkspace` 预览正文。模版卡片展示阶段、必需/可选、启用/停用与发布修订。

- [ ] **Step 5: 运行测试与构建**

Run: `cd frontend && pnpm test:run -- src/modules/knowledge/KnowledgePage.test.tsx src/modules/document/DocumentWorkspace.test.tsx && pnpm build`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/modules/knowledge frontend/src/styles/global.css
git commit -m "feat: add Outline-backed knowledge templates"
```

### Task 11: 改造项目详情为阶段文档工作区

**Files:**

- Modify: `frontend/src/modules/project/types.ts`
- Modify: `frontend/src/modules/project/projectApi.ts`
- Create: `frontend/src/modules/project/ProjectDocuments.tsx`
- Create: `frontend/src/modules/project/ProjectDocuments.test.tsx`
- Modify: `frontend/src/modules/project/ProjectDetail.tsx`
- Modify: `frontend/src/modules/project/ProjectWorkspace.test.tsx`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 写失败的项目文档测试**

覆盖：

- 项目详情新增“项目文档”主页签，按七阶段分组，默认定位当前阶段。
- 展示必需/可选、待初始化/待填写/待确认/已完成/同步失败。
- 初始化失败有清晰错误和“重试初始化”按钮。
- 文档点击进入复用编辑器；负责人可确认，非负责人无确认按钮。
- 阶段推进被阻断时列出具体未完成文档；WARNING 模式使用项目持久化设置，不弹出前端临时绕过选择。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && pnpm test:run -- src/modules/project/ProjectDocuments.test.tsx src/modules/project/ProjectWorkspace.test.tsx`

Expected: FAIL，项目文档 UI 尚不存在。

- [ ] **Step 3: 更新 API 与类型**

`Project` 增加 `documentSpaceStatus/documentSpaceError`；`projectApi` 增加 `documents/retryDocuments/loadDocument/saveDocument/confirmDocument/exportUrl`。

- [ ] **Step 4: 实现文档工作区**

阶段左侧使用紧凑列表或 Tabs，右侧用卡片网格保留用户确认的卡片视图。每张卡显示标题、修订、来源模版、必需性、状态和最近同步时间。项目模板中心旧页签改为只读迁移提示，新的模版维护入口统一指向知识库“文档模版”。

- [ ] **Step 5: 改造阶段推进提示**

409 错误详情从后端读取未完成文档清单并展示；项目 `gateMode=WARNING` 时使用确认框记录警告后重试同一推进动作，但不能由请求体临时覆盖 `BLOCK` 配置。

- [ ] **Step 6: 运行测试与构建**

Run: `cd frontend && pnpm test:run -- src/modules/project/ProjectDocuments.test.tsx src/modules/project/ProjectWorkspace.test.tsx && pnpm build`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/modules/project frontend/src/styles/global.css
git commit -m "feat: add project stage document workspace"
```

### Task 12: 完成容器配置、模拟 Outline 与端到端验收

**Files:**

- Create: `mock-outline/Dockerfile`
- Create: `mock-outline/server.mjs`
- Modify: `compose.yaml`
- Modify: `frontend/scripts/run-e2e.mjs`
- Create: `frontend/e2e/outline-document-center.e2e.ts`
- Modify: `.env.example`
- Modify: `README.md`

- [ ] **Step 1: 写失败的端到端验收**

Playwright 流程：

1. 登录并在知识库创建必需的“项目启动检查单”文档模版。
2. 编辑 Markdown、预览并发布。
3. 创建项目，轮询项目文档空间为 READY。
4. 打开启动阶段文档、编辑、确认，推进到需求阶段。
5. 通过 mock-outline 的测试接口模拟外部修改，再打开项目确认状态已失效。
6. 下载 md/html/pdf/docx，断言响应状态、MIME 和非空文件。
7. 将 mock-outline 置为不可用，创建第二个项目仍成功并显示失败重试；恢复后重试成功。

- [ ] **Step 2: 运行并确认失败**

Run: `cd frontend && pnpm e2e -- outline-document-center.e2e.ts`

Expected: FAIL，mock-outline 与完整 UI 流程尚未配置。

- [ ] **Step 3: 实现最小 mock-outline**

Node 标准库服务只实现测试需要的：

```text
POST /api/documents.create
POST /api/documents.info
POST /api/documents.update
POST /api/documents.list
POST /api/documents.export
POST /__test__/availability
POST /__test__/documents/{id}/external-update
GET  /health
```

内存保存文档树和 `revision`，校验 Bearer token，不引入 npm 依赖。

- [ ] **Step 4: 更新 Compose**

`backend` 增加：

```yaml
OUTLINE_BASE_URL: ${OUTLINE_BASE_URL:-http://host.docker.internal:3000}
OUTLINE_API_TOKEN: ${OUTLINE_API_TOKEN:-}
OUTLINE_COLLECTION_ID: ${OUTLINE_COLLECTION_ID:-a4296a54-2044-4529-ba86-d598a5322e06}
```

E2E 运行时通过脚本覆盖为 `http://mock-outline:3000` 与测试 token。真实本地 Outline 仍使用 `host.docker.internal:3000`。

- [ ] **Step 5: 更新运行文档**

`.env.example` 和 `README.md` 说明：在 Outline 用专用服务账号创建 API Key，配置三个环境变量，执行管理页初始化和迁移；API Key 不可从现有哈希恢复。

- [ ] **Step 6: 运行分层验证**

Run:

```bash
cd backend && mvn test
cd ../frontend && pnpm test:run
pnpm build
pnpm e2e -- outline-document-center.e2e.ts
```

Expected: 全部 PASS。

- [ ] **Step 7: 验证真实 Outline 配置**

配置专用 API Key 后运行：

```bash
curl -fsS -X POST http://localhost:8082/api/v1/admin/document-center/initialize
curl -fsS http://localhost:8082/api/v1/admin/document-center/status
```

Expected: 集合 `a4296a54-2044-4529-ba86-d598a5322e06` 连通，知识库与项目文档根目录状态为 READY。若尚未提供 API Key，本步骤明确标记为外部配置待办，不伪造成功。

- [ ] **Step 8: 最终提交**

```bash
git add mock-outline compose.yaml frontend/scripts/run-e2e.mjs \
  frontend/e2e/outline-document-center.e2e.ts .env.example README.md
git commit -m "test: verify Outline document center end to end"
```

## Final Verification

- [ ] `git status --short` 只包含用户已有的无关改动或为空。
- [ ] `rg -n "TODO|FIXME|placeholder|not implemented" backend/src frontend/src mock-outline` 无本功能遗留占位。
- [ ] `cd backend && mvn test` 全量通过。
- [ ] `cd frontend && pnpm test:run && pnpm build` 全量通过。
- [ ] `cd frontend && pnpm e2e -- outline-document-center.e2e.ts` 通过。
- [ ] 真实 Outline 未配置 token 时，项目创建可用且文档空间明确显示待配置/失败；配置 token 后初始化和迁移幂等成功。
- [ ] 前端网络响应和日志中不存在 `OUTLINE_API_TOKEN` 或 `ol_api_`。
- [ ] 知识正文、项目正文、预览、保存和导出都读取 Outline 最新内容，不读取迁移成功后的本地旧正文。
