# Opportunity Stage Documents and Product Spec Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Outline-backed research report flow to the remaining approved opportunity documents and create an Outline product/module/feature Spec tree that supplies AI generation context.

**Architecture:** Reuse `DocumentCenterService` and `opportunity_artifact` as the document source-of-truth bridge. Add one product document service for product tree synchronization and one definition-driven opportunity stage document service for all five template documents; keep binary artifacts and POC score on the existing path. Use the installed `AiClient` synchronously with a template-first fallback so AI failure never blocks manual completion.

**Tech Stack:** Java 1.8, Spring Boot 2.7, JdbcTemplate, Flyway, MySQL 8, Outline API, React 18, TypeScript, Ant Design, TanStack Query, Vitest.

## Global Constraints

- Java source must remain compatible with Java 1.8.
- Outline is the only source of truth for document bodies.
- Do not add frontend or backend dependencies.
- Product business writes must survive Outline synchronization failures.
- AI failure must leave an editable template document and expose a retry action.
- Only `DECISION_MINUTES`, `CLIENT_REQUESTS`, `GAP_ANALYSIS`, and `REVIEW_MINUTES` join the existing `RESEARCH_REPORT` template flow.
- Binary artifacts and `POC_SCORE` retain their existing storage and UI.
- Existing `/research-report` APIs remain compatible.
- Template republishing never overwrites an existing opportunity document or feature Spec.

---

### Task 1: Schema, template scenes, and linked-document gates

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__opportunity_stage_documents_product_specs.sql`
- Modify: `backend/src/main/java/com/zhilu/delivery/knowledge/KnowledgeService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityGate.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityLifecycleIT.java`

**Interfaces:**
- Produces template scene constants `OPPORTUNITY_DECISION`, `OPPORTUNITY_CLIENT_REQUESTS`, `OPPORTUNITY_GAP_ANALYSIS`, `OPPORTUNITY_REVIEW`, and `PRODUCT_FEATURE_SPEC`.
- Produces `OpportunityGate.isTemplateDocument(String)` for generic service validation.
- Adds `product_feature.outline_link_id`, `source_template_id`, and `source_template_revision`.

- [ ] **Step 1: Write failing migration and gate tests**

Assert V18 adds nullable columns with foreign keys and a unique Outline link. Assert all six non-project template scenes accept only `TEMPLATE + REQUIRED + enabled`. Assert manual `addArtifact` rejects the five template document types and that a legacy Markdown row without `outline_link_id` does not satisfy a gate.

```java
assertThrows(IllegalArgumentException.class,
    () -> opportunities.addArtifact(3100, opportunityId, 101,
        artifact("DECISION_MINUTES", "旧正文")));
assertEquals(Arrays.asList("决策评审纪要"),
    gate.missingArtifacts(opportunityId, OpportunityStage.OPPORTUNITY, "PASS"));
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
cd backend
mvn -q -Dtest=SchemaBaselineTest,KnowledgeServiceTest,OpportunityLifecycleIT test
```

Expected: failures mention missing V18 columns, unsupported scene codes, and legacy artifacts incorrectly satisfying gates.

- [ ] **Step 3: Implement minimal schema and mappings**

Create V18:

```sql
ALTER TABLE product_feature ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE product_feature ADD COLUMN source_template_id BIGINT NULL;
ALTER TABLE product_feature ADD COLUMN source_template_revision BIGINT NULL;
ALTER TABLE product_feature ADD CONSTRAINT fk_product_feature_outline
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);
ALTER TABLE product_feature ADD CONSTRAINT fk_product_feature_spec_template
  FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id);
ALTER TABLE product_feature ADD CONSTRAINT uk_product_feature_outline UNIQUE (outline_link_id);
```

Replace the single opportunity-scene special case with a fixed set in `KnowledgeService`. In `OpportunityGate`, use:

```java
private static final Set<String> TEMPLATE_DOCUMENT_TYPES =
    Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "RESEARCH_REPORT", "DECISION_MINUTES", "CLIENT_REQUESTS",
        "GAP_ANALYSIS", "REVIEW_MINUTES")));
```

Append `and outline_link_id is not null` for every type in this set. Reject these types from `OpportunityService.addArtifact` with “请通过商机推进材料填写并提交”.

- [ ] **Step 4: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: define opportunity document template scenes"
```

### Task 2: Outline product tree and feature Spec backend

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/document/OutlineClient.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/HttpOutlineClient.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentCenterService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductDocumentController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/document/DocumentAdminController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/zhilu/delivery/document/HttpOutlineClientTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/catalog/ProductDocumentApiIT.java`

**Interfaces:**
- Adds `OutlineClient.move(OutlineConnection, String documentId, String parentDocumentId)`.
- Adds `DocumentCenterService.moveBusinessDocument(long organizationId, String businessKey, Long parentLinkId)`.
- Produces `ProductDocumentService.syncProduct`, `syncModule`, `syncFeature`, `initializeAll`, `tree`, `readSpec`, and `saveSpec`.
- Produces product document and admin initialization APIs from the approved design.

- [ ] **Step 1: Write failing Outline move and product tree tests**

Use the existing mocked Outline client and H2 schema. Cover root/product/module hierarchy, nested modules, template snapshot creation, rename/move, missing-template failure state, existing Spec preservation, and idempotent all-product initialization.

```java
ProductDocumentService.SyncResult result = documents.syncFeature(3100, featureId);
assertEquals("READY", result.getStatus());
assertEquals("PRODUCT:" + productId + ":FEATURE:" + featureId + ":SPEC",
    jdbc.queryForObject("select business_key from outline_document_link where id=?",
        String.class, result.getLinkId()));
verify(outline).move(any(OutlineConnection.class), eq(FEATURE_DOCUMENT_ID), eq(NEW_MODULE_ID));
```

For `HttpOutlineClientTest`, assert `/api/documents.move` receives `id` and `parentDocumentId`.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend
mvn -q -Dtest=HttpOutlineClientTest,ProductDocumentServiceTest,ProductDocumentApiIT test
```

Expected: compilation fails because product document types and move methods do not exist.

- [ ] **Step 3: Implement Outline move and product document synchronization**

Add to `OutlineClient`:

```java
OutlineDocument move(
    OutlineConnection connection, String documentId, String parentDocumentId);
```

Post `documents.move` in `HttpOutlineClient`. Extend `DocumentCenterService` to lock the link, resolve the parent link, skip the remote move when the parent is unchanged, call Outline otherwise, and sync the returned revision.

Implement `ProductDocumentService` with these exact public methods:

```java
public SyncResult syncProduct(long organizationId, long productId);
public SyncResult syncModule(long organizationId, long productId, long moduleId);
public SyncResult syncFeature(long organizationId, long productId, long featureId);
public InitializeResult initializeAll(long organizationId);
public List<Map<String, Object>> tree(long organizationId, long productId);
public DocumentView readSpec(long organizationId, long productId, long featureId);
public DocumentView saveSpec(long organizationId, long productId, long featureId,
    String title, String markdown, long revision);
```

Use recursive module synchronization with the existing maximum depth of three. Load the unique published `PRODUCT_FEATURE_SPEC` snapshot and replace known placeholders. If Outline or template work fails after a product write, catch the failure in `trySync*` controller helpers and return the saved product/module/feature unchanged; `outline_document_link` retains failure state for retry.

- [ ] **Step 4: Add APIs and post-commit write hooks**

Expose:

```text
GET  /api/v1/products/{productId}/documents
POST /api/v1/products/{productId}/documents/sync
GET  /api/v1/products/{productId}/features/{featureId}/spec
PUT  /api/v1/products/{productId}/features/{featureId}/spec
GET  /api/v1/products/{productId}/features/{featureId}/spec/export
POST /api/v1/admin/documents/products/initialize
```

Remove controller-level `@Transactional` from product create/update so the catalog service transaction commits before the controller calls `trySyncProduct`. After module/feature service calls return, invoke the corresponding post-commit sync helper.

- [ ] **Step 5: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: manage product feature specs in Outline"
```

### Task 3: Generic opportunity stage document service and AI generation

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityDocumentDefinition.java`
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityResearchReportService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityResearchReportServiceTest.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentServiceTest.java`

**Interfaces:**
- `OpportunityDocumentDefinition.forType(String artifactType)` validates stage/type/template/generation policy.
- `OpportunityStageDocumentService` produces prepare/read/saveDraft/generate/submit operations.
- Existing `OpportunityResearchReportService` remains a compatibility facade.

- [ ] **Step 1: Write failing service tests**

Cover all five mappings, stage mismatch, unique template snapshot, placeholder rejection, single-document automatic advance, POC non-advancing submit, repeated submit, and AI context selection.

```java
PreparedDocument generated = service.prepare(3100, pocId, "CLIENT_REQUESTS", version);
assertEquals("AI", generated.getGenerationStatus());
verify(ai).completeJson(contains("甲方诉求"),
    allOf(contains("需求调研报告正文"), contains("功能 A"), contains("INCLUDED"),
        contains("功能 A Spec")), any(JsonNode.class));
```

Add separate tests for a selected version (`INCLUDED` and `PLANNED` only), product without version (all non-deprecated features), no product, missing Spec, AI not configured, invalid JSON fields, and manual content overwrite protection.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend
mvn -q -Dtest=OpportunityStageDocumentServiceTest,OpportunityResearchReportServiceTest test
```

Expected: compilation fails for the new definition and service.

- [ ] **Step 3: Implement the fixed definition map**

Create immutable definitions:

```java
new Definition("RESEARCH_REPORT", OpportunityStage.LEAD,
    KnowledgeService.OPPORTUNITY_RESEARCH, false, true);
new Definition("DECISION_MINUTES", OpportunityStage.OPPORTUNITY,
    KnowledgeService.OPPORTUNITY_DECISION, false, true);
new Definition("CLIENT_REQUESTS", OpportunityStage.POC,
    KnowledgeService.OPPORTUNITY_CLIENT_REQUESTS, true, false);
new Definition("GAP_ANALYSIS", OpportunityStage.POC,
    KnowledgeService.OPPORTUNITY_GAP_ANALYSIS, true, false);
new Definition("REVIEW_MINUTES", OpportunityStage.CONTRACT,
    KnowledgeService.OPPORTUNITY_REVIEW, false, false);
```

The final boolean is `advanceOnSubmit`.

- [ ] **Step 4: Implement template-first documents and AI generation**

Implement:

```java
public PreparedDocument prepare(long organizationId, long opportunityId,
    String artifactType, long opportunityVersion);
public DocumentView read(long organizationId, long opportunityId, String artifactType);
public DocumentView saveDraft(long organizationId, long opportunityId, String artifactType,
    String title, String markdown, long revision);
public PreparedDocument generate(long organizationId, long opportunityId, String artifactType,
    long revision, boolean confirmOverwrite);
public SubmitResult submit(long organizationId, long opportunityId, long actorId,
    String artifactType, long opportunityVersion, String title, String markdown, long revision);
```

`prepare` always creates the template copy first. For AI definitions it attempts generation and catches `AiNotConfiguredException` or other runtime generation failures into `generationStatus="FAILED"` and `generationError`, while returning the editable document.

Build a strict JSON schema with required string properties `title` and `markdown`. Concatenate the research report and selected product Spec context using explicit headings and availability labels. Never invent missing context; include a warning list in the prompt and response object.

Add one generic `OpportunityService.submitDocumentArtifact(...)` that validates the business key and inserts an Outline-linked artifact idempotently. Call existing `advance` only when the definition has `advanceOnSubmit=true`.

- [ ] **Step 5: Make research report a compatibility facade**

Delegate current methods to `OpportunityStageDocumentService` using `RESEARCH_REPORT`. Preserve existing return types and behavior so existing API tests remain unchanged.

- [ ] **Step 6: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: generate opportunity stage documents"
```

### Task 4: Generic opportunity document APIs and permissions

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityStageDocumentApiIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/config/SecurityAccessTest.java`

**Interfaces:**
- Produces all six generic document endpoints and preserves research endpoints.
- GET/export require `crm:read`; prepare/save/generate/submit require `crm:write`.

- [ ] **Step 1: Write failing controller and security tests**

Test prepare, read, save, generate fallback, submit responses, export filename, invalid artifact type, cross-organization access, read-only access, and write denial.

```java
mockMvc.perform(post("/api/v1/opportunities/{id}/documents/DECISION_MINUTES/prepare", id)
    .session(admin()).with(csrf())
    .contentType(APPLICATION_JSON).content("{\"version\":1}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.sourceTemplateRevision").isNumber());
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend
mvn -q -Dtest=OpportunityStageDocumentApiIT,SecurityAccessTest test
```

Expected: 404 for generic endpoints.

- [ ] **Step 3: Add generic endpoints and DTOs**

Reuse current document response flattening and export helper. Add `GenerateRequest { revision, confirmOverwrite }`; return `generationStatus`, `generationError`, and `warnings` from prepare/generate. Submit returns the flattened document plus `opportunity` when automatic advancement occurred.

- [ ] **Step 4: Add explicit security matchers**

Place generic matcher rules before broad `/api/v1/opportunities/**` matchers.

- [ ] **Step 5: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: expose opportunity stage document APIs"
```

### Task 5: Product design document frontend

**Files:**
- Modify: `frontend/src/modules/knowledge/KnowledgePage.tsx`
- Modify: `frontend/src/modules/knowledge/KnowledgePage.test.tsx`
- Modify: `frontend/src/modules/product/types.ts`
- Modify: `frontend/src/modules/product/productApi.ts`
- Create: `frontend/src/modules/product/ProductDocumentsTab.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.tsx`
- Modify: `frontend/src/modules/product/ProductStructureTab.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.test.tsx`
- Modify: `frontend/src/modules/admin/DocumentCenterPage.tsx`
- Modify: `frontend/src/modules/admin/DocumentCenterConfiguration.test.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Displays all six new template scene labels.
- Adds product document tree/read/save/sync APIs and types.
- Adds a product “设计文档” tab and admin initialization action.

- [ ] **Step 1: Write failing frontend tests**

Assert the knowledge editor lists all scene names, product detail loads `/documents`, selecting a feature loads its Spec, write users can save/sync, read-only users cannot, and admin initialization displays returned counts.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd frontend
pnpm exec vitest run src/modules/knowledge/KnowledgePage.test.tsx \
  src/modules/product/ProductDetailPage.test.tsx \
  src/modules/admin/DocumentCenterConfiguration.test.tsx
```

Expected: missing scene options and product document UI assertions fail.

- [ ] **Step 3: Implement the product documents tab**

Reuse `DocumentWorkspace`; do not add an editor. Render a left Ant Design `Tree` and right document panel. Status tags are `待初始化`, `已同步`, and `同步失败`. On narrow screens, CSS changes the two-column grid to one column.

- [ ] **Step 4: Add structure and admin shortcuts**

Add “查看设计 Spec” on each feature row, switching the product page to the documents tab with that feature selected. Add “初始化产品资料” to the Outline admin page and invalidate its status queries after completion.

- [ ] **Step 5: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "feat: manage product design specs"
```

### Task 6: Opportunity stage materials frontend

**Files:**
- Modify: `frontend/src/modules/customer-center/types.ts`
- Modify: `frontend/src/modules/customer-center/crmApi.ts`
- Create: `frontend/src/modules/customer-center/OpportunityDocumentDrawer.tsx`
- Create: `frontend/src/modules/customer-center/OpportunityMaterialsDrawer.tsx`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityDetailPage.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Generic document API methods accept the approved artifact types.
- Single-document stages submit and advance; POC and contract documents submit without advancing.
- POC workspace combines existing file/text artifact UI with the two AI documents.

- [ ] **Step 1: Write failing interaction tests**

Cover OPPORTUNITY PASS opening decision minutes instead of direct advance, POC opening a four-item workspace, AI failure/manual fallback, regenerate confirmation, POC document submission without advance, final POC advance after all artifacts, and contract review minutes in handoff materials.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd frontend
pnpm exec vitest run src/modules/customer-center/OpportunityPages.test.tsx
```

Expected: current direct PASS/advance and generic artifact drawer behavior fail the new assertions.

- [ ] **Step 3: Implement generic document API and drawer**

Add:

```ts
prepareOpportunityDocument(id, artifactType, version)
opportunityDocument(id, artifactType)
saveOpportunityDocument(id, artifactType, input)
generateOpportunityDocument(id, artifactType, revision, confirmOverwrite)
submitOpportunityDocument(id, artifactType, opportunityVersion, input)
opportunityDocumentExportUrl(id, artifactType, format)
```

`OpportunityDocumentDrawer` reuses `DocumentWorkspace`, renders generation warnings, and exposes “生成初稿/重新生成”, “保存草稿”, and the context-specific submit label.

- [ ] **Step 4: Implement POC and contract material workspaces**

Use the artifact list query as the completion source. The POC drawer shows four material cards and calls existing `advanceOpportunity` only when every required artifact is present. The contract handoff drawer shows five completion cards and opens `REVIEW_MINUTES` through the generic document drawer; handoff remains the final action.

- [ ] **Step 5: Preserve opportunity detail previews**

For every artifact with `outlineLinkId`, open the generic document endpoint by artifact type. Continue rendering legacy Markdown and files as before.

- [ ] **Step 6: Run tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "feat: complete opportunity stage material flows"
```

### Task 7: Full verification, local initialization, and runtime handoff

**Files:**
- Modify only files required by defects discovered during verification.

**Interfaces:**
- Produces a clean branch, healthy local containers, published local templates, and initialized local product documents.

- [ ] **Step 1: Run focused backend verification**

```bash
cd backend
mvn -q -Dtest=SchemaBaselineTest,KnowledgeServiceTest,DocumentCenterServiceTest,\
HttpOutlineClientTest,ProductDocumentServiceTest,ProductDocumentApiIT,\
OpportunityStageDocumentServiceTest,OpportunityResearchReportServiceTest,\
OpportunityStageDocumentApiIT,OpportunityLifecycleIT,OpportunityHandoffIT,\
SecurityAccessTest test
```

Expected: exit 0.

- [ ] **Step 2: Run focused frontend verification and production build**

```bash
cd frontend
pnpm exec vitest run src/modules/document/DocumentWorkspace.test.tsx \
  src/modules/knowledge/KnowledgePage.test.tsx \
  src/modules/product/ProductDetailPage.test.tsx \
  src/modules/admin/DocumentCenterConfiguration.test.tsx \
  src/modules/customer-center/OpportunityPages.test.tsx
pnpm run build
```

Expected: all tests pass and Vite production build exits 0.

- [ ] **Step 3: Rebuild local application**

```bash
docker compose -p zhilu-delivery-main up -d --build backend frontend
curl -fsS http://localhost:8082/actuator/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:53990/customers/presale
```

Expected: backend reports `UP`, frontend returns 200, and all Compose services are healthy.

- [ ] **Step 4: Initialize local templates and product documents**

Using the existing authenticated local admin session, create or update and publish exactly one enabled required template for each new scene, then run “初始化产品资料”. Do not overwrite any existing user-authored document. Confirm admin status reports no failed jobs.

- [ ] **Step 5: Verify git state and commit any verification fixes**

```bash
git diff --check
git status --short
```

Expected: clean worktree after any necessary fix commits.
