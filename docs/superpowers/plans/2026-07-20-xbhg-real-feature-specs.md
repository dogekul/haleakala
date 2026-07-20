# 消保合规真实功能 Spec Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为产品 `XBHG / 消保合规` 的 124 个有效功能生成内容真实、结构完整且可验收的独立 Spec，并通过系统文档中心写入 Outline、建立功能关联。

**Architecture:** 复用现有 `ProductDocumentService` 和 Outline 关联表，只增加一个支持逐功能重试的同步接口。使用 Node.js 标准库脚本从系统读取产品结构，按功能编码匹配领域内容画像，生成并校验 Markdown，再逐份同步、读取当前修订号并更新正文；脚本内置限速和指数退避以应对 Outline 429。

**Tech Stack:** Java 1.8、Spring Boot、JUnit 5、MockMvc、Node.js 20+ 标准库、MySQL、Outline API。

## Global Constraints

- 仅处理 `product.id=102`、`product.code=XBHG`、状态为 `ACTIVE` 的 124 个原子功能。
- 每个功能必须有独立 Outline 文档并回写 `product_feature.outline_link_id`。
- 每份 Spec 必须包含元数据、目标、范围、角色权限、主流程、异常补偿、界面字段、业务规则、数据、接口事件、安全审计、非功能、验收标准、依赖演进。
- 每份 Spec 至少包含 5 条功能特定业务规则和 6 条可测试验收标准。
- 最终正文不得包含 `TODO`、`TBD`、`请补充`、空表格或模板占位符。
- 不删除或覆盖既有非当前功能文档；不修改用户现有未提交文件。
- 不新增 npm 或 Maven 依赖。

---

### Task 1: 单功能 Outline 同步接口

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentController.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentApiIT.java`

**Interfaces:**
- Consumes: `ProductDocumentService.syncFeature(long organizationId, long productId, long featureId)`。
- Produces: `POST /api/v1/products/{productId}/features/{featureId}/spec/sync`，返回 `DocumentView`。

- [x] **Step 1: 写失败的接口测试**

在 `synchronizesListsReadsAndUpdatesFeatureSpecs` 中先调用单功能同步接口，并断言返回标题、Markdown 和修订号：

```java
mvc.perform(post("/api/v1/products/3300/features/3302/spec/sync")
        .with(actor("product:write")).with(csrf()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.title").value("线索管理 Spec"))
    .andExpect(jsonPath("$.markdown").value("# 线索管理"))
    .andExpect(jsonPath("$.revision").isNumber());
```

- [x] **Step 2: 运行测试并确认接口尚不存在**

Run: `./mvnw -q -Dtest=ProductDocumentApiIT test`

Expected: FAIL，响应状态为 404 或 405。

- [x] **Step 3: 实现最小控制器入口**

在 `ProductDocumentController` 增加：

```java
@PostMapping("/features/{featureId}/spec/sync")
public DocumentView syncFeature(
    @PathVariable long productId, @PathVariable long featureId,
    @AuthenticationPrincipal CurrentUser user) {
  return documents.syncFeature(user.getOrganizationId(), productId, featureId);
}
```

- [x] **Step 4: 运行接口回归测试**

Run: `./mvnw -q -Dtest=ProductDocumentApiIT test`

Expected: PASS，全部 `ProductDocumentApiIT` 用例通过。

- [x] **Step 5: 提交接口变更**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentController.java backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentApiIT.java
git commit -m "feat: add feature spec sync endpoint"
```

### Task 2: 124 份真实 Spec 生成与校验工具

**Files:**
- Create: `scripts/xbhg-feature-specs.mjs`
- Create: `scripts/xbhg-feature-specs.test.mjs`

**Interfaces:**
- Consumes: `GET /api/v1/products/102/modules`、`GET /api/v1/products/102/features`、Task 1 同步接口、Spec 读写接口。
- Produces: `generateFeatureSpec(context): string`、`validateFeatureSpec(markdown, context): string[]`、CLI 执行报告 JSON。

- [x] **Step 1: 写生成器失败测试**

使用 `node:test` 覆盖法规采集、语义风险、公平性、审查任务、报告、集成、安全、模型运营等代表功能，并遍历脚本导出的 124 个功能定义：

```javascript
test('all 124 definitions generate complete feature-specific specs', () => {
  assert.equal(FEATURE_DEFINITIONS.size, 124);
  for (const [code, definition] of FEATURE_DEFINITIONS) {
    const markdown = generateFeatureSpec(sampleContext(code, definition));
    assert.deepEqual(validateFeatureSpec(markdown, { code, name: definition.name }), []);
    assert.match(markdown, new RegExp(escapeRegExp(code)));
  }
});
```

另写测试断言缺章节、少于 5 条规则、少于 6 条验收条件和包含禁用占位词时返回明确错误。

- [x] **Step 2: 运行测试并确认模块尚不存在**

Run: `node --test scripts/xbhg-feature-specs.test.mjs`

Expected: FAIL，提示无法导入 `scripts/xbhg-feature-specs.mjs`。

- [x] **Step 3: 实现领域定义和 Markdown 生成器**

在 `scripts/xbhg-feature-specs.mjs` 中定义 124 个功能编码的业务画像；每个画像包含目的、参与者、输入、输出、关键字段、状态、至少 5 条规则、异常场景、数据实体、接口/事件、安全控制、性能目标和至少 6 条验收条件。生成器输出统一章节，但所有关键内容来自该功能画像，不输出空项或占位文本。

导出以下接口：

```javascript
export const FEATURE_DEFINITIONS = new Map([...]);
export function generateFeatureSpec(context) { /* 返回完整 Markdown */ }
export function validateFeatureSpec(markdown, context) { /* 返回错误字符串数组 */ }
```

- [x] **Step 4: 实现本地系统写入 CLI**

CLI 从 `ZHILU_USERNAME`、`ZHILU_PASSWORD` 读取凭据，通过 Cookie + CSRF 登录；校验产品编码及有效功能总数必须分别为 `XBHG` 和 `124`。对每个功能执行“同步文档 → 读取修订号 → PUT 更新”，每份间隔至少 1100ms；遇到 `OUTLINE_RATE_LIMIT`、429、502、503、504 时按 2、4、8、16 秒重试，最多 5 次。成功后再次读取并校验正文，最终打印 `generated/updated/verified/failed` 数量和失败明细；任一失败时进程码为 1。

运行方式：

```bash
ZHILU_USERNAME=admin ZHILU_PASSWORD='<local-password>' node scripts/xbhg-feature-specs.mjs --base-url=http://localhost:8082 --product-id=102
```

- [x] **Step 5: 运行生成器测试**

Run: `node --test scripts/xbhg-feature-specs.test.mjs`

Expected: PASS，124 个定义全部通过结构、规则数、验收数和占位词校验。

- [x] **Step 6: 提交生成工具**

```bash
git add scripts/xbhg-feature-specs.mjs scripts/xbhg-feature-specs.test.mjs
git commit -m "feat: generate real xbhg feature specs"
```

### Task 3: 写入 Outline 并完成验收

**Files:**
- Modify: `docs/superpowers/plans/2026-07-20-xbhg-real-feature-specs.md`（仅勾选完成项）

**Interfaces:**
- Consumes: Task 1 接口、Task 2 CLI、本地 MySQL 与 Outline 配置。
- Produces: 124 个状态为 `READY` 的功能 Spec 关联及一份终端验收报告。

- [x] **Step 1: 合并功能分支并重建后端**

```bash
git checkout main
git merge --ff-only codex/xbhg-real-feature-specs
docker compose up -d --build backend
```

Expected: 后端健康检查成功，`http://localhost:8082/actuator/health` 返回 `UP`。

- [x] **Step 2: 实际生成并写入 Outline**

```bash
ZHILU_USERNAME=admin ZHILU_PASSWORD='<local-password>' node scripts/xbhg-feature-specs.mjs --base-url=http://localhost:8082 --product-id=102
```

Expected: 退出码 0，报告为 `generated=124`、`updated=124`、`verified=124`、`failed=0`。

- [x] **Step 3: 核对数据库关联和同步状态**

执行 SQL：

```sql
select count(*) as linked
from product_feature f
join outline_document_link l on l.id=f.outline_link_id
where f.product_id=102 and f.status='ACTIVE'
  and l.sync_status='READY' and l.document_id is not null;
```

Expected: `linked=124`。

- [x] **Step 4: 通过系统 API 全量回读验收**

CLI 验证模式逐一读取 124 份文档，重新执行 `validateFeatureSpec`，并检查 Outline URL 非空、标题格式为 `<功能名称> · 设计 Spec`。

Run: `ZHILU_USERNAME=admin ZHILU_PASSWORD='<local-password>' node scripts/xbhg-feature-specs.mjs --base-url=http://localhost:8082 --product-id=102 --verify-only`

Expected: 退出码 0，`verified=124`、`failed=0`。

- [x] **Step 5: 运行完整相关回归**

```bash
cd backend
./mvnw -q -Dtest=ProductDocumentApiIT,ProductDocumentServiceTest,ProductDocumentAutoSyncTest test
cd ..
node --test scripts/xbhg-feature-specs.test.mjs
```

Expected: Java 和 Node 测试全部通过。

- [x] **Step 6: 记录执行结果**

将实际的关联数、READY 数、全量回读数和 7 个代表功能编码写入最终交付说明；不在仓库中保存 Outline token 或登录密码。
