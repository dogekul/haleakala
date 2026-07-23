# Project Seven-Stage Document Gates Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按原始交付范式补齐七阶段项目文档模板、条件门禁、既有项目模板同步和最终项目关闭能力。

**Architecture:** 继续使用知识模板作为源、`project_document` 作为项目快照、Outline 作为实际文档空间。通过单一 `condition_code` 支持二开条件门禁，通过幂等异步任务同步既有项目；通过专用关闭接口完成最后一道文档门禁。

**Tech Stack:** Java 17、Spring Boot、JdbcTemplate、Flyway、React、TypeScript、Vitest、Ant Design、MySQL、Outline。

---

### Task 1: 扩展模板与快照模型

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__project_document_gate_conditions.sql`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`

1. 先写断言证明两张表必须包含 `condition_code`。
2. 运行定向测试并确认因字段缺失失败。
3. 新增迁移，字段默认值为 `ALWAYS`。
4. 在模板写入、详情、发布快照和项目快照中传递条件码。
5. 重跑定向测试。

### Task 2: 实现条件门禁

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/project/ProjectDocumentLifecycleIT.java`

1. 写出“无二开不拦截、有二开才拦截”的失败测试。
2. 查询项目是否存在 `custom_dev_task`。
3. 在 `incompleteRequired` 中只校验已触发的必需模板，并在项目文档视图中返回 `gateRequired`。
4. 重跑测试确认通过。

### Task 3: 为既有项目同步新模板

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentJobService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentMigrationService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectController.java`
- Test: `backend/src/test/java/com/zhilu/delivery/project/ProjectDocumentLifecycleIT.java`

1. 写出同步缺失模板且不覆盖现有文档的失败测试。
2. 新增 `PROJECT_TEMPLATE_SYNC` 任务类型和可重复触发方法。
3. 新增快照补齐方法，按模板 ID 幂等插入。
4. 暴露 `POST /api/v1/projects/{id}/documents/sync`。
5. 重跑测试并验证重复同步结果一致。

### Task 4: 补齐项目关闭门禁

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectController.java`
- Test: `backend/src/test/java/com/zhilu/delivery/project/ProjectDocumentLifecycleIT.java`

1. 写出进入关闭阶段变为 `CLOSING`、缺文档不能关闭、补齐后变为 `CLOSED` 的失败测试。
2. 推进至 `CLOSE` 时写入 `CLOSING`。
3. 新增专用关闭服务和接口，复用门禁模式与缺失清单。
4. 限制设置接口仅切换 `ACTIVE/SUSPENDED`，禁止绕过关闭门禁。
5. 重跑测试。

### Task 5: 注入七阶段演示模板

**Files:**
- Create: `backend/src/main/resources/db/demo/R__zz_project_gate_templates.sql`

1. 为七阶段插入知识条目、模板配置和发布快照。
2. 为既有项目补充缺失快照，为知识模板和项目创建/重置异步任务。
3. 内容采用可直接填写的 Markdown 清单，不覆盖已有管理员内容。
4. 在 MySQL demo profile 中执行 Flyway 并查询阶段、必需项和条件项数量。

### Task 6: 补齐前端模板管理与项目操作

**Files:**
- Modify: `frontend/src/modules/knowledge/KnowledgePage.tsx`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.test.tsx`
- Modify: `frontend/src/modules/project/types.ts`
- Modify: `frontend/src/modules/project/projectApi.ts`
- Modify: `frontend/src/modules/project/ProjectDocuments.tsx`
- Modify: `frontend/src/modules/project/ProjectDocuments.test.tsx`
- Modify: `frontend/src/modules/project/ProjectDetail.tsx`
- Test: corresponding Vitest files

1. 先补失败测试：条件码编辑/显示、模板同步、关闭项目。
2. 知识模板编辑器增加“适用条件”。
3. 项目文档展示实际门禁状态，并提供“同步门禁模板”。
4. 关闭阶段展示“完成并关闭项目”，设置页移除可绕过门禁的关闭状态选项。
5. 跑定向测试并修复类型检查。

### Task 7: 验证和本地验收

**Files:**
- Verify only

1. 运行后端全量测试。
2. 运行前端全量测试和生产构建。
3. 重建 Docker 服务，等待健康检查和异步文档任务完成。
4. 查询数据库确认模板与项目快照，登录本地页面验证各阶段、同步和关闭入口。
5. 检查 `git diff`，确认此前按钮颜色修复未被覆盖，并报告任何已存在的无关测试失败。
