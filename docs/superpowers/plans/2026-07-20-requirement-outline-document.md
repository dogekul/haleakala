# Requirement Outline Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completing the requirement collection form creates a requirement research report from the published knowledge template in Outline and links it back to the requirement.

**Architecture:** Keep the existing requirement POST contract and add an orchestration method to `RequirementService` that creates the row and delegates document generation to a focused `RequirementDocumentService` inside one Spring transaction. The document service reuses `DocumentCenterService`, deterministic business keys, and the existing `OPPORTUNITY_RESEARCH` published template; the frontend only opens the resulting Outline URL.

**Tech Stack:** Java 8, Spring Boot, JdbcTemplate, Flyway, MySQL/H2, React, TypeScript, Ant Design, TanStack Query, JUnit 5, Mockito, Vitest, Testing Library.

## Global Constraints

- Preserve existing direct `RequirementService.create(...)` behavior for internal tests and flows; only HTTP collection uses `collect(...)`.
- Store generated documents under `项目文档 / {项目} / 需求文档` with business key `REQUIREMENT:{id}:RESEARCH_REPORT`.
- Use the enabled, published `OPPORTUNITY_RESEARCH` knowledge template and persist its ID and revision.
- Generated Markdown must contain the submitted requirement details and no `{{...}}` placeholders.
- Do not add a second Markdown editor or new dependency.
- Preserve unrelated working-tree changes in the OpenAI-compatible client and local Markdown files.

---

### Task 1: Persist and generate the requirement document

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__requirement_outline_document.sql`
- Create: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementDocumentServiceTest.java`

**Interfaces:**
- Consumes: `DocumentCenterService.ensureIndex(...)`, `createDocument(...)`, and `readLink(...)`.
- Produces: `RequirementDocumentService.attach(long requirementId, long actorUserId)` and `read(long requirementId, long organizationId)`; `RequirementService.collect(...)` returns the fully linked requirement map.

- [ ] **Step 1: Write the failing document-generation test**

Create an H2-backed Spring test with `@MockBean DocumentCenterService`. Seed organization, user, product, project, one `TEMPLATE/PUBLISHED` knowledge item and one enabled `OPPORTUNITY_RESEARCH` config with revision `7`. Stub directory IDs and `createDocument` to return link ID `9203`; call `requirements.collect(920, "自动核验客户证件", "开户时自动核验证件有效期，校验结果可追溯", "客户访谈", "P1", 920)` and assert:

```java
assertEquals(Long.valueOf(9203L), result.get("outlineLinkId"));
Map<String, Object> link = jdbc.queryForMap(
    "select outline_link_id,source_template_id,source_template_revision "
        + "from requirement_item where id=?", result.get("id"));
assertEquals(9203L, ((Number) link.get("outline_link_id")).longValue());
assertEquals(9201L, ((Number) link.get("source_template_id")).longValue());
assertEquals(7L, ((Number) link.get("source_template_revision")).longValue());
verify(documents).createDocument(eq(920L),
    eq("REQUIREMENT:" + result.get("id") + ":RESEARCH_REPORT"),
    eq("REQUIREMENT_RESEARCH"), contains("自动核验客户证件"),
    argThat(markdown -> markdown.contains("开户时自动核验证件有效期")
        && !markdown.contains("{{")), eq(9202L));
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -Dtest=RequirementDocumentServiceTest test`

Expected: compilation fails because `RequirementService.collect(...)` and `RequirementDocumentService` do not exist.

- [ ] **Step 3: Add the migration**

Create `V21__requirement_outline_document.sql` with nullable compatibility columns and foreign keys:

```sql
ALTER TABLE requirement_item ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE requirement_item ADD COLUMN source_template_id BIGINT NULL;
ALTER TABLE requirement_item ADD COLUMN source_template_revision BIGINT NULL;
ALTER TABLE requirement_item ADD CONSTRAINT fk_requirement_outline_link
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);
ALTER TABLE requirement_item ADD CONSTRAINT fk_requirement_source_template
  FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id);
CREATE UNIQUE INDEX uk_requirement_outline_link ON requirement_item(outline_link_id);
```

- [ ] **Step 4: Implement focused document generation**

Create `RequirementDocumentService` with a `Pattern.compile("\\{\\{[^{}]+}}")` fallback. `attach(...)` loads the requirement joined with `delivery_project` and `app_user`, loads exactly one published template using:

```sql
select k.id,c.published_revision,c.published_title_snapshot,
       c.published_markdown_snapshot
from knowledge_item k
join document_template_config c on c.knowledge_item_id=k.id
where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED'
  and c.stage_code='OPPORTUNITY_RESEARCH' and c.enabled=true
  and c.published_revision is not null
order by k.id
```

Reject zero templates with `ConflictException("请先在知识库发布并启用需求调研报告模版")` and more than one with `ConflictException("需求调研报告模版只能启用一个")`. Ensure the three directories with keys `PROJECT_ROOT`, `PROJECT:{projectId}`, and `PROJECT:{projectId}:REQUIREMENTS`, then create the document. Prepend this traceable requirement section before the filled template and replace remaining placeholders with `待确认`:

```markdown
# {项目名称}需求调研报告

> 需求编号：{需求编号}  
> 需求标题：{标题}  
> 来源：{来源}  
> 优先级：{优先级}  
> 采集人：{提交人}  
> 采集日期：{yyyy-MM-dd}

## 本次采集需求

{完整描述}

---
```

Update `requirement_item.outline_link_id`, `source_template_id`, `source_template_revision`, `updated_at`, and increment `version` after `createDocument(...)` returns.

- [ ] **Step 5: Add transactional collection orchestration and response mapping**

Inject `RequirementDocumentService` into `RequirementService`, add:

```java
@Transactional
public Map<String, Object> collect(long projectId, String title, String description,
    String source, String priority, long actorUserId) {
  Map<String, Object> created = create(projectId, title, description, source, priority, actorUserId);
  documents.attach(((Number) created.get("id")).longValue(), actorUserId);
  return get(((Number) created.get("id")).longValue());
}
```

Add `outlineLinkId`, `sourceTemplateId`, and `sourceTemplateRevision` to `map(ResultSet)` using nullable getters. Keep `create(...)` unchanged.

- [ ] **Step 6: Run the focused backend test**

Run: `cd backend && mvn -Dtest=RequirementDocumentServiceTest test`

Expected: `BUILD SUCCESS` and all test methods pass.

- [ ] **Step 7: Commit the backend generation slice**

```bash
git add backend/src/main/resources/db/migration/V21__requirement_outline_document.sql \
  backend/src/main/java/com/zhilu/delivery/requirement/RequirementDocumentService.java \
  backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java \
  backend/src/test/java/com/zhilu/delivery/requirement/RequirementDocumentServiceTest.java
git commit -m "feat: generate Outline document for collected requirement"
```

### Task 2: Expose the linked document safely through the requirement API

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java`
- Test: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementCollectionApiIT.java`

**Interfaces:**
- Consumes: `RequirementService.collect(...)` and `RequirementDocumentService.read(...)` from Task 1.
- Produces: `POST /api/v1/requirements` with linked fields and `GET /api/v1/requirements/{id}/document` returning `DocumentView`.

- [ ] **Step 1: Write the failing API test**

Seed the same project and published template, mock `DocumentCenterService`, then submit:

```java
mvc.perform(post("/api/v1/requirements").with(writer()).with(csrf())
    .contentType("application/json")
    .content("{\"projectId\":930,\"title\":\"交易限额校验\","
        + "\"description\":\"付款前校验客户交易限额并保留结果\","
        + "\"source\":\"需求调研\",\"priority\":\"P1\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.outlineLinkId").value(9303));
```

Stub `documents.readLink(9303, 930)` with a `DocumentView` and assert `GET /api/v1/requirements/{id}/document` returns its `outlineUrl`. Add a non-member request asserting `404` so document access cannot bypass project scope.

- [ ] **Step 2: Run the API test to verify it fails**

Run: `cd backend && mvn -Dtest=RequirementCollectionApiIT test`

Expected: POST response has no `outlineLinkId` and document GET is `404` because the endpoint does not exist.

- [ ] **Step 3: Route collection and document reads**

Change controller POST to call `requirements.collect(...)`. Inject `RequirementDocumentService` and add:

```java
@GetMapping("/{id}/document")
public DocumentView document(@PathVariable long id,
    @AuthenticationPrincipal CurrentUser user) {
  Map<String, Object> requirement = get(id, user);
  return documents.read(id, ((Number) requirement.get("organizationId")).longValue());
}
```

`RequirementDocumentService.read(...)` must verify the requirement belongs to the supplied organization and has a non-null `outline_link_id`, then call `DocumentCenterService.readLink(...)`; otherwise throw `NotFoundException("需求文档不存在")`.

- [ ] **Step 4: Run API and existing requirement tests**

Run: `cd backend && mvn -Dtest=RequirementCollectionApiIT,RequirementApiIT,ClassificationServiceTest,RequirementAiOrganizationTest test`

Expected: `BUILD SUCCESS`; the new POST and document GET pass while existing direct `create(...)` tests remain unchanged.

- [ ] **Step 5: Commit the API slice**

```bash
git add backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java \
  backend/src/main/java/com/zhilu/delivery/requirement/RequirementDocumentService.java \
  backend/src/test/java/com/zhilu/delivery/requirement/RequirementCollectionApiIT.java
git commit -m "feat: expose collected requirement documents"
```

### Task 3: Update the requirement collection UI and document action

**Files:**
- Modify: `frontend/src/modules/requirement/types.ts`
- Modify: `frontend/src/modules/requirement/requirementApi.ts`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.tsx`
- Test: `frontend/src/modules/requirement/RequirementWorkshop.test.tsx`

**Interfaces:**
- Consumes: Task 2 response property `outlineLinkId` and endpoint `GET /api/v1/requirements/{id}/document`.
- Produces: `requirementApi.document(id)`, collection completion copy, and a conditional “查看文档” action.

- [ ] **Step 1: Write the failing collection and document-action tests**

Add a test that opens “采集需求”, fills all fields, clicks “完成采集并生成文档”, and asserts the POST body contains the form values and the success message is visible. Add a list item with `outlineLinkId: 9403`, mock document GET as `{ outlineUrl: 'http://localhost:3000/doc/req-9403' }`, click “查看文档”, and assert:

```ts
expect(window.open).toHaveBeenCalledWith(
  'http://localhost:3000/doc/req-9403', '_blank', 'noopener,noreferrer')
```

Also assert rows without `outlineLinkId` do not render the action.

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `cd frontend && npm test -- --run src/modules/requirement/RequirementWorkshop.test.tsx`

Expected: the new button and document action cannot be found.

- [ ] **Step 3: Implement typed API and UI behavior**

Extend `Requirement` with optional numeric `outlineLinkId`, `sourceTemplateId`, and `sourceTemplateRevision`. Add:

```ts
export interface RequirementDocument { outlineUrl: string; title: string; revision: number }

document: (id: number) =>
  api<RequirementDocument>(`/api/v1/requirements/${id}/document`),
```

Import `FileTextOutlined`. Change the drawer button to “完成采集并生成文档”, the warning text to explain that submission generates the formal report, and the success message to “需求已创建，调研文档已保存到 Outline”. In `RequirementList`, conditionally render “查看文档”; on click, call `requirementApi.document(row.id)`, open `outlineUrl`, and show `message.error` if reading fails. Increase the operation column width just enough to keep the four link buttons on one line.

- [ ] **Step 4: Run focused frontend tests**

Run: `cd frontend && npm test -- --run src/modules/requirement/RequirementWorkshop.test.tsx`

Expected: all `RequirementWorkshop` tests pass.

- [ ] **Step 5: Commit the frontend slice**

```bash
git add frontend/src/modules/requirement/types.ts \
  frontend/src/modules/requirement/requirementApi.ts \
  frontend/src/modules/requirement/RequirementWorkshop.tsx \
  frontend/src/modules/requirement/RequirementWorkshop.test.tsx
git commit -m "feat: complete requirement collection with Outline report"
```

### Task 4: Verify the integrated feature

**Files:**
- Modify: `docs/superpowers/2026-07-20-requirement-outline-document-design.md` only if verification reveals a behavior difference that must be documented.

**Interfaces:**
- Consumes: all backend and frontend behavior from Tasks 1–3.
- Produces: a verified working tree ready for the user to test against the local Outline configuration.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn test`

Expected: `BUILD SUCCESS` with zero failures and errors.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: all Vitest files pass and Vite production build completes successfully.

- [ ] **Step 3: Inspect the diff for unrelated files**

Run: `git status --short && git diff --check && git diff --stat HEAD~3..HEAD`

Expected: no whitespace errors; the pre-existing OpenAI client edits and local Markdown files remain unstaged and unchanged by this feature.

- [ ] **Step 4: Start the updated application if it is not already running**

Run the repository's existing local start command, confirm `http://localhost:53990/requirements` responds, and leave the process running for user validation. Do not create a test requirement against the user's real Outline collection without their explicit submission.


