# Evidence-Based Requirement Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让需求分类基于正式需求报告、完整产品功能目录、人工覆盖和相关功能 Spec 生成可追溯建议，并同时保存和展示建设内容表与投产计划表。

**Architecture:** 新建 `RequirementClassificationAiService` 负责两阶段 AI 调用和上下文装配；`RequirementService` 保留状态机、持久化和人工确认职责。第二阶段完整结果以 JSON 保存在最新 `classification_suggestion`，已有摘要列继续服务查询；前端在现有决策抽屉中展示证据、资料缺口和两个表格。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、Flyway、Jackson、MySQL 8/H2、React 18、TypeScript、Ant Design、Vitest。

## Global Constraints

- 不引入向量数据库、Embedding 服务或新前端依赖。
- AI 分类仍然只是建议，必须人工确认后才进入漏斗。
- 第一阶段最多选择 12 个 AI 候选，人工覆盖功能强制纳入。
- 没有正式需求调研报告时拒绝智能分类；Spec 缺失记录为资料缺口。
- 两张表都至少一行，资料不足统一使用“待确认”，不得虚构。
- 不自动修改 `requirement_product_feature`，不额外创建 Outline 文档。

---

### Task 1: Persist complete classification details

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__classification_delivery_tables.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/requirement/ClassificationServiceTest.java`

**Interfaces:**
- Consumes: existing `classification_suggestion` latest-row join.
- Produces: nullable `details_json`; API fields `classificationEvidence`, `classificationWarnings`, `constructionContents`, `productionPlan`.

- [ ] **Step 1: Write failing schema and mapping tests**

Add a schema assertion:

```java
assertEquals(Integer.valueOf(1), jdbc.queryForObject(
    "select count(*) from information_schema.columns where table_schema='public' "
        + "and table_name='classification_suggestion' and column_name='details_json'",
    Integer.class));
```

Add a service assertion using a new overload:

```java
requirements.saveSuggestion(id, "L1", 0.91, "需要增强", "AI", details);
assertEquals("客户校验", ((Map<?, ?>) ((List<?>) requirements.get(id)
    .get("constructionContents")).get(0)).get("featureName"));
```

- [ ] **Step 2: Run tests and verify RED**

Run `mvn -q -Dtest=SchemaBaselineTest,ClassificationServiceTest test`.

Expected: FAIL because `details_json` and the six-argument `saveSuggestion` do not exist.

- [ ] **Step 3: Add migration and minimal JSON persistence**

Create:

```sql
ALTER TABLE classification_suggestion ADD COLUMN details_json LONGTEXT NULL;
```

Select `s.details_json` in list/detail queries. Preserve the five-argument overload and add:

```java
public Map<String, Object> saveSuggestion(long id, String level, double confidence,
    String reason, String source, JsonNode details) {
  jdbc.update("insert into classification_suggestion(requirement_id,suggested_level,"
      + "confidence,reason,source,details_json) values (?,?,?,?,?,?)",
      id, level, confidence, reason, source, details == null ? null : details.toString());
  return get(id);
}
```

Parse only valid object JSON in `map`; historical null or malformed payloads return empty lists without breaking legacy records.

- [ ] **Step 4: Run tests and verify GREEN**

Run the same Maven command and expect all selected tests to pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V22__classification_delivery_tables.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java backend/src/test/java/com/zhilu/delivery/requirement/ClassificationServiceTest.java
git commit -m "feat: persist requirement classification delivery tables"
```

### Task 2: Generate evidence-based classification and both tables

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementClassificationAiService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementClassificationAiServiceTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementAiOrganizationTest.java`

**Interfaces:**
- Consumes: `AiClient.completeJson`, `DocumentCenterService.readLink`, requirement/project/product tables, product version features, product document nodes and manual coverage.
- Produces: `JsonNode analyze(long requirementId)` with exact fields `level`, `confidence`, `reason`, `evidence`, `warnings`, `constructionContents`, `productionPlan`.

- [ ] **Step 1: Write failing two-stage context test**

Seed one formal requirement document, two version functions, manual `PARTIAL` coverage and one linked Spec. Mock ordered AI results `{ "featureIds": [501] }` and a complete final result. Capture prompts and assert:

```java
assertTrue(candidatePrompt.contains("客户校验"));
assertTrue(finalPrompt.contains("# 需求调研报告"));
assertTrue(finalPrompt.contains("# 客户校验 Spec"));
assertTrue(finalPrompt.contains("PARTIAL"));
assertTrue(finalPrompt.contains("计划开始"));
```

- [ ] **Step 2: Run test and verify RED**

Run `mvn -q -Dtest=RequirementClassificationAiServiceTest test`.

Expected: FAIL because `RequirementClassificationAiService` does not exist.

- [ ] **Step 3: Implement context loading and candidate selection**

Create one Spring service with `JdbcTemplate`, `DocumentCenterService`, `AiClient` and `ObjectMapper`. Require `requirement_item.outline_link_id`, read report Markdown and query the complete version catalog:

```sql
select f.id,m.name module_name,f.code,f.name,f.description,pvf.availability,
       coalesce(n.outline_link_id,f.outline_link_id) spec_outline_link_id
from product_version_feature pvf
join product_feature f on f.id=pvf.product_feature_id
join product_module m on m.id=f.module_id
left join product_document_node n on n.product_id=f.product_id
  and n.linked_feature_id=f.id and n.node_type='DOCUMENT'
where pvf.product_version_id=? and f.product_id=? and f.status<>'DEPRECATED'
order by m.sort_order,m.id,f.id
```

The first schema contains only integer array `featureIds`. Filter unknown IDs, preserve catalog order, take at most 12 AI candidates, then append all manual coverage IDs.

- [ ] **Step 4: Implement final schema, prompts and strict validation**

Build the nested schema from the exact fields in the design. The prompt states evidence rules, L0/L1/L2 definitions, minimum one row per table, date format and “待确认” fallback. Read candidate Specs individually and append warnings for missing or unavailable Specs.

Validate exact top-level size, enums, confidence range, non-empty arrays, exact row field counts and non-blank text. Throw `AiServiceException(INCOMPATIBLE_RESPONSE)` for any invalid response.

- [ ] **Step 5: Wire RequirementService and verify GREEN**

Replace its direct AI prompt with:

```java
JsonNode result = classifications.analyze(id);
return saveSuggestion(id, result.get("level").asText(),
    result.get("confidence").asDouble(), result.get("reason").asText(), "AI", result);
```

Update organization tests to seed required report/catalog and return two staged responses. Run `mvn -q -Dtest=RequirementClassificationAiServiceTest,RequirementAiOrganizationTest,ClassificationServiceTest test` and expect all selected tests to pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/requirement/RequirementClassificationAiService.java backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java backend/src/test/java/com/zhilu/delivery/requirement/RequirementClassificationAiServiceTest.java backend/src/test/java/com/zhilu/delivery/requirement/RequirementAiOrganizationTest.java
git commit -m "feat: classify requirements from reports and product specs"
```

### Task 3: Expose the two tables in the decision drawer

**Files:**
- Modify: `frontend/src/modules/requirement/types.ts`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.tsx`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.test.tsx`

**Interfaces:**
- Consumes: `classificationEvidence`, `classificationWarnings`, `constructionContents`, `productionPlan`.
- Produces: wide Chinese decision drawer with evidence area and two Ant Design tables.

- [ ] **Step 1: Write failing UI test**

Return a classified requirement with one row in each table, open “分类决策”, and assert:

```typescript
expect(await screen.findByText('建设内容表')).toBeVisible()
expect(screen.getByText('增强开发')).toBeVisible()
await user.click(screen.getByRole('tab', { name: '投产计划表' }))
expect(screen.getByText('灰度发布并验证回退')).toBeVisible()
expect(screen.getByText('资料缺口')).toBeVisible()
```

- [ ] **Step 2: Run test and verify RED**

Run `npm test -- --run src/modules/requirement/RequirementWorkshop.test.tsx`.

Expected: FAIL because the two table tabs are absent.

- [ ] **Step 3: Add types and minimal table UI**

Add `ConstructionContent` and `ProductionPlanItem` interfaces matching the design. Import `Tabs`, render both `Table` components when rich details exist, use Chinese change-type labels, `pagination={false}`, stable row keys and drawer width 1180. Show evidence as a compact list and warnings in a warning `Alert`. Preserve the historical compact suggestion card when details are absent.

- [ ] **Step 4: Run test and production build**

Run the focused test and `npm run build`.

Expected: existing tests plus the new test pass; TypeScript and Vite build succeed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/requirement/types.ts frontend/src/modules/requirement/RequirementWorkshop.tsx frontend/src/modules/requirement/RequirementWorkshop.test.tsx
git commit -m "feat: show requirement construction and production tables"
```

### Task 4: Full verification and local delivery

**Files:**
- Modify: `docs/superpowers/plans/2026-07-21-evidence-classification-delivery-tables.md` only to mark executed checkboxes.

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: verified branch merged into local `main`, rebuilt local services.

- [ ] **Step 1: Run backend full test suite**

Run `cd backend && mvn test` and require zero failures with `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend full tests and build**

Run `cd frontend && npm test -- --run && npm run build` and require zero test failures plus a successful Vite build.

- [ ] **Step 3: Check diff and commit plan progress**

Run `git diff --check`, inspect `git status --short`, then commit only the completed plan markers.

- [ ] **Step 4: Merge and rebuild**

Follow `superpowers:finishing-a-development-branch`, merge locally to `main`, preserve the existing DeepSeek client and Markdown working-tree changes, rebuild with `docker compose -p zhilu-delivery-main up -d --build`, and verify container health, `http://localhost:8082/actuator/health` and HTTP 200 from `http://localhost:53990/requirements`.
