# Opportunity Research Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the LEAD-stage free-text artifact form with an Outline-backed report cloned from the published knowledge template and submitted as the opportunity's research report while advancing the opportunity.

**Architecture:** Reuse `document_template_config.stage_code` with the new `OPPORTUNITY_RESEARCH` scene, reuse `DocumentCenterService` for deterministic Outline folders/documents, and add a focused `OpportunityResearchReportService` between the opportunity and document domains. The report is prepared lazily on advance, drafts stay in Outline, and submit saves the document before a local transaction links the artifact and advances the opportunity.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, Flyway/MySQL, React 18, TypeScript, React Query, Ant Design, Vitest, JUnit 5/MockMvc.

## Global Constraints

- Do not change the requirement workshop.
- Do not create a report when an opportunity is created; prepare it only when LEAD advance is requested.
- Outline is the source of truth for new report bodies; MySQL stores mappings and legacy `content_markdown` only.
- Identify templates by `stage_code = OPPORTUNITY_RESEARCH`, never by title or local filename.
- Keep Java source compatible with Java 8 and add no dependencies.
- Preserve all non-LEAD artifact and handoff behavior.
- Preserve the user's untracked `需求调研报告模板.md` and `合规审查系统需求调研报告.md` files.
- Follow TDD for every behavior: red, minimal green, refactor, focused verification, commit.

---

### Task 1: Persist report mappings and separate opportunity templates from project templates

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__opportunity_research_report.sql`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentInitializationTest.java`

**Interfaces:**
- Produces: `KnowledgeService.OPPORTUNITY_RESEARCH = "OPPORTUNITY_RESEARCH"`.
- Produces: nullable artifact columns `outline_link_id`, `source_template_id`, `source_template_revision`.
- Produces: project initialization query limited to the seven `DeliveryStage` values.

- [ ] **Step 1: Write failing schema and service tests**

Add assertions that V17 creates all three columns, foreign keys, and a unique index on `outline_link_id`. Add a template service test that creates a `TEMPLATE` with `stageCode="OPPORTUNITY_RESEARCH"` and expects it to persist. Add a project initialization test with one project-stage template and one opportunity template and assert only the project-stage template becomes a `project_document`.

```java
Map<String, Object> template = knowledge.create(user, "TEMPLATE", "需求调研报告",
    "商机调研", "# {{项目名称}}需求调研报告", "商机", null, null,
    "ORGANIZATION", null, null, null, null, null,
    "OPPORTUNITY_RESEARCH", "REQUIRED", true);
assertEquals("OPPORTUNITY_RESEARCH", template.get("stageCode"));
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
cd backend
mvn -q -Dtest=SchemaBaselineTest,KnowledgeServiceTest,ProjectDocumentInitializationTest test
```

Expected: failure because the migration columns and scene code do not exist and project initialization attempts to copy the opportunity template.

- [ ] **Step 3: Add the migration and minimal scene filtering**

Create V17:

```sql
ALTER TABLE opportunity_artifact
  ADD COLUMN outline_link_id BIGINT NULL,
  ADD COLUMN source_template_id BIGINT NULL,
  ADD COLUMN source_template_revision BIGINT NULL,
  ADD CONSTRAINT fk_opportunity_artifact_outline
    FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id),
  ADD CONSTRAINT fk_opportunity_artifact_template
    FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id),
  ADD CONSTRAINT uk_opportunity_artifact_outline UNIQUE (outline_link_id);
```

In `KnowledgeService.validateTemplate`, accept either `OPPORTUNITY_RESEARCH` or a valid `DeliveryStage`, and require `requirement=REQUIRED` for the opportunity scene. In `ProjectDocumentService.templates`, append:

```sql
and c.stage_code in ('START','REQUIREMENT','CUSTOM_DEV','GO_LIVE',
  'TRIAL_HANDOVER','STANDARDIZATION','CLOSE')
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same Maven command. Expected: all selected tests pass with zero failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V17__opportunity_research_report.sql \
  backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java \
  backend/src/main/java/com/zhilu/delivery/document/ProjectDocumentService.java \
  backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java \
  backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java \
  backend/src/test/java/com/zhilu/delivery/document/ProjectDocumentInitializationTest.java
git commit -m "feat: configure opportunity research templates"
```

### Task 2: Build the idempotent Outline report lifecycle

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityResearchReportService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityGate.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityResearchReportServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityLifecycleIT.java`

**Interfaces:**
- Consumes: V17 columns and `KnowledgeService.OPPORTUNITY_RESEARCH`.
- Produces: `PreparedReport prepare(long organizationId, long opportunityId, long version)`.
- Produces: `DocumentView read(long organizationId, long opportunityId)`.
- Produces: `DocumentView saveDraft(long organizationId, long opportunityId, String title, String markdown, long revision)`.
- Produces: `Map<String,Object> submit(long organizationId, long opportunityId, long actorId, long opportunityVersion, String title, String markdown, long revision)`.
- Produces: `DocumentView DocumentCenterService.readBusinessDocument(...)` and `updateBusinessDocument(...)` scoped by organization and business key.

- [ ] **Step 1: Write failing report lifecycle tests**

Cover one published template snapshot, no template, duplicate templates, repeated prepare, draft save, placeholder rejection, submit success, and retry after an Outline revision conflict. The success assertion must prove the artifact links the report instead of copying Markdown:

```java
assertEquals("OPPORTUNITY", opportunity.get("stage"));
Map<String, Object> artifact = jdbc.queryForMap(
    "select outline_link_id,source_template_id,source_template_revision,content_markdown "
        + "from opportunity_artifact where opportunity_id=? and artifact_type='RESEARCH_REPORT'",
    opportunityId);
assertNotNull(artifact.get("outline_link_id"));
assertEquals(templateId, ((Number) artifact.get("source_template_id")).longValue());
assertNull(artifact.get("content_markdown"));
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend
mvn -q -Dtest=OpportunityResearchReportServiceTest,OpportunityLifecycleIT test
```

Expected: compile failure because `OpportunityResearchReportService` and business-document methods do not exist.

- [ ] **Step 3: Expose narrowly-scoped business document operations**

Add methods to `DocumentCenterService` that resolve a link only by `(organizationId,businessKey)`, then delegate to existing `readLink` and `updateLink`. Do not expose raw API tokens or accept a caller-supplied link ID without organization validation.

```java
public DocumentView readBusinessDocument(long organizationId, String businessKey) {
  Long linkId = findLinkId(organizationId, businessKey);
  if (linkId == null) throw new NotFoundException("文档不存在");
  return readLink(linkId.longValue(), organizationId);
}
```

- [ ] **Step 4: Implement prepare and draft save**

`prepare` must:

1. Assert opportunity is current `LEAD / OPEN / version`.
2. Query exactly one enabled, published `OPPORTUNITY_RESEARCH` template with non-null published snapshots.
3. Ensure `OPPORTUNITY_ROOT`, `OPPORTUNITY:<id>`, and `OPPORTUNITY:<id>:RESEARCH_REPORT` with `ensureIndex`/`createDocument`.
4. Return the report `DocumentView` plus `sourceTemplateId` and `sourceTemplateRevision`.

`saveDraft` must reject non-LEAD or terminal opportunities and delegate revision checking to `DocumentCenterService.updateBusinessDocument`.

- [ ] **Step 5: Implement submit and gate hardening**

Before writing Outline, validate nonblank Markdown and reject unresolved placeholders with:

```java
private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}");
```

After Outline save, use a transactionally proxied local method to lock the opportunity, recheck the version, insert the artifact with `insert ... select ... where not exists`, require an artifact with non-null `outline_link_id`, advance to `OPPORTUNITY`, and record the audit. Reject direct `LEAD + RESEARCH_REPORT` Markdown creation in `OpportunityService.addArtifact`.

- [ ] **Step 6: Run tests and verify GREEN**

Run the focused Maven command. Expected: selected tests pass and legacy report reads in `OpportunityLifecycleIT` remain green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityResearchReportService.java \
  backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java \
  backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityGate.java \
  backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java \
  backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityResearchReportServiceTest.java \
  backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityLifecycleIT.java
git commit -m "feat: manage opportunity research reports in Outline"
```

### Task 3: Add secured report APIs and export support

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentExportService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityResearchReportApiIT.java`
- Test: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`

**Interfaces:**
- Consumes: Task 2 service signatures.
- Produces: prepare/read/save/submit/export HTTP endpoints from the design.
- Produces: JSON document shape compatible with frontend `DocumentContent`, plus `sourceTemplateId` and `sourceTemplateRevision` on prepare.

- [ ] **Step 1: Write failing MockMvc API and permission tests**

Test `crm:read` for GET/export, `crm:write` for prepare/save/submit, organization isolation as 404, submit body validation, and response fields. Use CSRF on mutations.

```java
mvc.perform(post("/api/v1/opportunities/{id}/research-report/prepare", id)
    .with(actor(writer, "crm:write")).with(csrf())
    .contentType(APPLICATION_JSON)
    .content("{\"version\":0}"))
  .andExpect(status().isOk())
  .andExpect(jsonPath("$.sourceTemplateId").isNumber())
  .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("需求调研")));
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend
mvn -q -Dtest=OpportunityResearchReportApiIT,SecurityAccessTest test
```

Expected: 404 for missing routes.

- [ ] **Step 3: Implement controller contracts**

Add nested request classes:

```java
public static final class PrepareResearchReportRequest { @NotNull public Long version; }
public static class SaveResearchReportRequest {
  @NotBlank public String title;
  @NotBlank public String markdown;
  @NotNull public Long revision;
}
public static final class SubmitResearchReportRequest extends SaveResearchReportRequest {
  @NotNull public Long opportunityVersion;
}
```

Map `DocumentView` through one response helper. Export must load the opportunity-scoped report and delegate to existing Markdown/HTML/PDF/DOCX generation.

- [ ] **Step 4: Add exact security matchers**

Place report GET/export routes under `crm:read` and all report POST/PUT routes under `crm:write`, before broader opportunity matchers. Do not grant anonymous access.

- [ ] **Step 5: Run tests and verify GREEN**

Run the focused Maven command. Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java \
  backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java \
  backend/src/main/java/com/zhilu/delivery/document/DocumentExportService.java \
  backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityResearchReportApiIT.java \
  backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java
git commit -m "feat: expose opportunity research report APIs"
```

### Task 4: Add reusable document submit action and knowledge scene UI

**Files:**
- Modify: `frontend/src/modules/document/DocumentWorkspace.tsx`
- Modify: `frontend/src/modules/document/DocumentWorkspace.test.tsx`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.tsx`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.test.tsx`

**Interfaces:**
- Produces: optional `submit?: (input: SaveDocumentInput) => Promise<DocumentContent>`.
- Produces: optional `submitLabel?: string` and `onSubmitted?: (document: DocumentContent) => void`.
- Produces: knowledge stage option and display label `OPPORTUNITY_RESEARCH -> 商机 · 需求调研`.

- [ ] **Step 1: Write failing frontend tests**

Add a `DocumentWorkspace` test that edits Markdown, clicks “提交报告并推进”, and verifies `submit` receives the loaded revision while ordinary `save` is not called. Add a knowledge editor test that selects “商机 · 需求调研” and submits `stageCode: "OPPORTUNITY_RESEARCH"`.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd frontend
npm test -- --run src/modules/document/DocumentWorkspace.test.tsx src/modules/knowledge/KnowledgePage.test.tsx
```

Expected: missing submit button and scene option assertions fail.

- [ ] **Step 3: Add the optional submit action**

Reuse the existing persistence state and error handling. Render the second primary button only when `submit` exists and edit mode is active. A successful submit updates local document state, switches to preview, and calls `onSubmitted(document)`; a 409 keeps local Markdown exactly like normal save.

- [ ] **Step 4: Add scene labels and selector option**

Use one local mapping layered over project `stageNames`:

```ts
const templateSceneNames = {
  ...stageNames,
  OPPORTUNITY_RESEARCH: '商机 · 需求调研',
}
```

Keep the current `requirement` field required and default the opportunity report to `REQUIRED`.

- [ ] **Step 5: Run tests and verify GREEN**

Run the focused npm command. Expected: both test files pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/modules/document/DocumentWorkspace.tsx \
  frontend/src/modules/document/DocumentWorkspace.test.tsx \
  frontend/src/modules/knowledge/KnowledgePage.tsx \
  frontend/src/modules/knowledge/KnowledgePage.test.tsx
git commit -m "feat: submit Outline documents from business flows"
```

### Task 5: Replace LEAD advance with the report workspace

**Files:**
- Modify: `frontend/src/modules/customer-center/crmApi.ts`
- Modify: `frontend/src/modules/customer-center/types.ts`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityDetailPage.tsx`
- Test: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`

**Interfaces:**
- Consumes: Task 3 endpoints and Task 4 submit props.
- Produces: `PreparedResearchReport extends DocumentContent` with `sourceTemplateId` and `sourceTemplateRevision`.
- Produces: report API methods `prepareResearchReport`, `researchReport`, `saveResearchReport`, `submitResearchReport`, `researchReportExportUrl`.

- [ ] **Step 1: Write failing LEAD workflow test**

Mock prepare to return the template Markdown, click the LEAD “推进” button, assert the old “报告正文” field does not exist, edit the document, submit, and verify:

```ts
expect(requests).toContainEqual(expect.objectContaining({
  path: '/api/v1/opportunities/12/research-report/submit',
  body: JSON.stringify({
    title: '银行需求调研报告',
    markdown: '# 已完成调研',
    revision: 4,
    opportunityVersion: 0,
  }),
}))
```

Also test prepare failure/retry, direct “产出物” for LEAD not offering `RESEARCH_REPORT`, and OPPORTUNITY/POC artifact behavior unchanged.

- [ ] **Step 2: Run test and verify RED**

```bash
cd frontend
npm test -- --run src/modules/customer-center/OpportunityPages.test.tsx
```

Expected: LEAD advance still calls `/advance` and opens the old artifact drawer.

- [ ] **Step 3: Add API types and methods**

Use the shared document types from `frontend/src/modules/document/types.ts`. `prepareResearchReport` sends the opportunity version; submit sends title, Markdown, revision, and opportunity version.

- [ ] **Step 4: Implement `ResearchReportDrawer`**

In `PresaleBoardPage.tsx`, intercept only `item.stage === 'LEAD'`. Prepare on click, open a `min(1180px, 94vw)` drawer, and render `DocumentWorkspace`. Wire normal save to draft PUT and optional submit to report submit. On submit success, invalidate `opportunities`, `opportunity`, and `opportunity-artifacts`, show “需求调研报告已提交，商机阶段已推进”, then close.

Remove `RESEARCH_REPORT` from `artifactTypes.LEAD`; leave the generic artifact drawer available only for stages with remaining types.

- [ ] **Step 5: Render linked report artifacts in opportunity detail**

Extend `OpportunityArtifact` with `outlineLinkId`, `sourceTemplateId`, and `sourceTemplateRevision`. For linked research reports show a “预览报告” action that opens the same read-only workspace; legacy `contentMarkdown` remains visible.

- [ ] **Step 6: Run test and verify GREEN**

Run the focused npm command. Expected: all opportunity page tests pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/modules/customer-center/crmApi.ts \
  frontend/src/modules/customer-center/types.ts \
  frontend/src/modules/customer-center/PresaleBoardPage.tsx \
  frontend/src/modules/customer-center/OpportunityDetailPage.tsx \
  frontend/src/modules/customer-center/OpportunityPages.test.tsx
git commit -m "feat: fill research reports while advancing opportunities"
```

### Task 6: Initialize the live template, verify, and launch

**Files:**
- Modify only if required by verified failures: files already listed in Tasks 1-5.
- Preserve without committing: `需求调研报告模板.md`, `合规审查系统需求调研报告.md`.

**Interfaces:**
- Consumes: complete backend/frontend feature.
- Produces: one published enabled knowledge template with `stageCode=OPPORTUNITY_RESEARCH` in the local organization and a running Docker stack.

- [ ] **Step 1: Run focused automated verification**

```bash
cd backend
mvn -q -Dtest=SchemaBaselineTest,KnowledgeServiceTest,ProjectDocumentInitializationTest,OpportunityResearchReportServiceTest,OpportunityResearchReportApiIT,OpportunityLifecycleIT,SecurityAccessTest test
cd ../frontend
npm test -- --run src/modules/document/DocumentWorkspace.test.tsx src/modules/knowledge/KnowledgePage.test.tsx src/modules/customer-center/OpportunityPages.test.tsx
npm run build
```

Expected: zero backend failures, all selected frontend tests pass, and Vite build exits 0. The user will perform final browser acceptance; do not add extra end-to-end automation unless a focused failure requires it.

- [ ] **Step 2: Rebuild and start the local services**

```bash
docker compose -p zhilu-delivery-main up -d --build backend frontend
docker compose -p zhilu-delivery-main ps
curl -fsS http://localhost:8082/actuator/health
```

Expected: backend and frontend healthy; actuator returns `{"status":"UP"}`.

- [ ] **Step 3: Initialize the knowledge template from the supplied Markdown**

Use authenticated local application APIs, not direct SQL for business rows:

1. Check whether exactly one published enabled `OPPORTUNITY_RESEARCH` template already exists.
2. If none exists, create a knowledge `TEMPLATE` using the contents of `需求调研报告模板.md`, set `stageCode=OPPORTUNITY_RESEARCH`, `requirement=REQUIRED`, `enabled=true`, then publish it.
3. If one exists, leave it unchanged.
4. If multiple exist, report the configuration conflict instead of silently choosing one.

- [ ] **Step 4: Perform a non-destructive readiness check**

Confirm the template appears in knowledge search, the document center connection is READY, and the application loads `/customers/presale`. Do not create a throwaway customer or opportunity because the user requested to perform browser acceptance.

- [ ] **Step 5: Review the branch diff and commit any final focused fix**

```bash
git diff --check
git status --short
git log --oneline --decorate -8
```

Expected: only intentional commits plus the two preserved untracked Markdown files. If no fix is needed, do not create an empty commit.

---

## Completion Evidence

- V17 schema assertions pass.
- Knowledge template scene and project-template exclusion tests pass.
- Report prepare/draft/submit/idempotency/tenant tests pass.
- LEAD UI workflow and legacy artifact compatibility tests pass.
- Frontend build succeeds.
- Docker backend/frontend are healthy.
- Local organization has exactly one published enabled `OPPORTUNITY_RESEARCH` template or a clearly reported configuration blocker.
