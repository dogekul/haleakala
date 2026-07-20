# Requirement AI Report, Edit and Abandon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate complete requirement research reports with the configured LLM, and let users edit or abandon collected requirements safely.

**Architecture:** `RequirementDocumentService` owns prompt construction, AI response validation and Outline create/update. `RequirementService` owns requirement state transitions and audit. The existing React workshop reuses one drawer for create/edit and adds an optimistic-lock abandon action.

**Tech Stack:** Java 8, Spring Boot/JdbcTemplate, Jackson JSON Schema prompts, MySQL/H2, React, TypeScript, Ant Design, TanStack Query, Vitest/Testing Library.

## Global Constraints

- Reuse the existing organization AI configuration and `AiClient`; do not add a queue or dependency.
- Missing facts must be rendered as “待确认”; generated reports must not retain `{{...}}` placeholders.
- Regeneration must be explicit because it overwrites the latest Outline body.
- `ABANDONED` retains the requirement, document and audit history and is not actionable.
- Preserve the user-owned uncommitted DeepSeek compatibility changes on local `main`.

---

### Task 1: Generate and regenerate intelligent reports

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementDocumentService.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementDocumentServiceTest.java`

**Interfaces:**
- Consumes: `AiClient.completeJson(long, String, String, JsonNode)` and `DocumentCenterService.updateLink(...)`.
- Produces: `void attach(long requirementId, long actorUserId)` and `void regenerate(long requirementId, long actorUserId)`.

- [ ] **Step 1: Write failing AI generation tests**

Add an `@MockBean AiClient`, return `{"title":"...","markdown":"# 完整报告..."}`, and verify the user prompt contains the published template, requirement facts, customer, product and version. Verify `createDocument` receives the AI markdown rather than static replacement.

- [ ] **Step 2: Write failing validation and regeneration tests**

Test that a response with a blank field or `{{未填写}}` throws `AiServiceException(INCOMPATIBLE_RESPONSE)` and rolls back the new requirement. Test that `regenerate(...)` updates the existing `outline_link_id` at its current Outline revision and that a legacy requirement without a link delegates to `attach(...)`.

- [ ] **Step 3: Run focused tests and observe failure**

Run: `cd backend && ./mvnw -q -Dtest=RequirementDocumentServiceTest test`

Expected: FAIL because `attach` does not call `AiClient` and `regenerate` is absent.

- [ ] **Step 4: Implement the minimal generator**

Inject `AiClient` and `ObjectMapper`; construct an object schema with exactly `title` and `markdown`; add `generate(...)`, `responseSchema()`, `systemPrompt()` and `userPrompt(...)`. Validate both fields and reject `PLACEHOLDER.matcher(markdown).find()`. Extend the context query with product/version fields. In `regenerate`, call `documents.readLink(...)` then `documents.updateLink(linkId, organizationId, title, markdown, current.getRevision())`; call `attach` when the link is absent.

- [ ] **Step 5: Run focused tests and commit**

Run: `cd backend && ./mvnw -q -Dtest=RequirementDocumentServiceTest test`

Expected: PASS.

Commit: `git commit -am "feat: generate intelligent requirement reports"`

---

### Task 2: Add edit regeneration and abandon state transitions

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementFeatureService.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java`

**Interfaces:**
- Consumes: `RequirementDocumentService.regenerate(long, long)`.
- Produces: `update(..., long expectedVersion, boolean regenerateReport, long actorUserId)` and `abandon(long id, long expectedVersion, long actorUserId)`; HTTP `POST /api/v1/requirements/{id}/abandon` with `{version}`.

- [ ] **Step 1: Write failing API tests**

Cover `PUT` with `regenerateReport=false`, `PUT` with `regenerateReport=true`, and `POST /{id}/abandon`. Assert the latter returns `status=ABANDONED`, increments `version`, creates `REQUIREMENT_ABANDONED` audit, and leaves `outline_link_id` intact.

- [ ] **Step 2: Write terminal-state tests**

Assert `ABANDONED` rejects update, classify, confirm, coverage replacement, duplicate scan and merge with HTTP 409 while `GET /{id}/document` remains readable.

- [ ] **Step 3: Run focused tests and observe failure**

Run: `cd backend && ./mvnw -q -Dtest=RequirementApiIT test`

Expected: FAIL because abandon and regeneration request handling do not exist.

- [ ] **Step 4: Implement state and controller changes**

Add `regenerateReport` to `SaveRequest`, `AbandonRequest { long version; }`, and the abandon controller method. Change the update SQL predicate to `status not in ('MERGED','ABANDONED')`, invoke regeneration only after the optimistic update, and record edit/abandon audit events. Add a shared actionable-state check before classify, confirm, duplicate and merge operations. Reject coverage writes when the joined requirement status is `ABANDONED`.

- [ ] **Step 5: Run focused tests and commit**

Run: `cd backend && ./mvnw -q -Dtest=RequirementApiIT,RequirementDocumentServiceTest test`

Expected: PASS.

Commit: `git commit -am "feat: edit and abandon collected requirements"`

---

### Task 3: Add workshop edit and abandon interactions

**Files:**
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.tsx`
- Modify: `frontend/src/modules/requirement/requirementApi.ts`
- Modify: `frontend/src/modules/requirement/types.ts`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.test.tsx`

**Interfaces:**
- Consumes: `PUT /api/v1/requirements/{id}` with `regenerateReport`; `POST /api/v1/requirements/{id}/abandon` with `version`.
- Produces: edit drawer actions, abandon confirmation, Chinese status rendering and `ABANDONED` board column.

- [ ] **Step 1: Write failing interaction tests**

Open “编辑”, assert fields are prefilled and project is disabled; submit “仅保存需求” and assert `regenerateReport:false`; submit “保存并重新生成报告”, confirm overwrite, and assert `regenerateReport:true`. Confirm “废弃” sends the current version and refreshes list/funnel.

- [ ] **Step 2: Write failing terminal UI tests**

Render an abandoned requirement and assert “已废弃” appears, “查看文档” remains, and edit/classify/coverage/merge/abandon actions are absent or disabled. Switch to the board and assert the “已废弃” column contains the card.

- [ ] **Step 3: Run focused tests and observe failure**

Run: `cd frontend && npm test -- --run src/modules/requirement/RequirementWorkshop.test.tsx`

Expected: FAIL because the edit/abandon UI and status are absent.

- [ ] **Step 4: Implement the minimal UI**

Add `EditOutlined` and `StopOutlined` actions, reuse `CollectionDrawer` with an optional `requirement`, and use `Modal.confirm` before destructive regeneration or abandon. Invalidate `requirements` and `requirement-funnel` after mutations. Extend the status union and render labels: `DRAFT=草稿`, `SUBMITTED=待确认`, `CONFIRMED=已确认`, `MERGED=已合并`, `ABANDONED=已废弃`. Render five board columns with flex sizing rather than fixed Ant grid spans.

- [ ] **Step 5: Run focused tests and commit**

Run: `cd frontend && npm test -- --run src/modules/requirement/RequirementWorkshop.test.tsx`

Expected: PASS.

Commit: `git commit -am "feat: expose requirement edit and abandon actions"`

---

### Task 4: Verify, integrate and run locally

**Files:**
- Verify only; no planned code changes.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: verified local `main` and healthy local application.

- [ ] **Step 1: Run backend suite**

Run: `cd backend && ./mvnw test`

Expected: all tests PASS with zero failures/errors.

- [ ] **Step 2: Run frontend suite and build**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: all tests PASS and Vite build exits 0.

- [ ] **Step 3: Merge into local main without touching user changes**

Merge the isolated feature branch with `git merge --no-ff`; do not stage the two modified `OpenAiCompatibleClient` files or the two untracked Markdown files.

- [ ] **Step 4: Rebuild and start services**

Run the repository's existing Docker Compose command for project `zhilu-delivery-main`, then verify container health and HTTP 200 for `/requirements`.

