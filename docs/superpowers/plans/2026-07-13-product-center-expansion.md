# Product Center Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first-class product center that manages organization-scoped products, three-level module trees, reusable features, version manifests, requirement coverage, and conversion of standardization candidates into product features.

**Architecture:** Extend the existing `catalog` module inside the Java 8 modular monolith and keep MySQL as the only source of truth. Add focused services for product structure and version manifests, expose nested REST resources, then build a lazy-loaded React product module using the existing Ant Design and TanStack Query patterns. Requirement and standardization integrations call catalog application services so product ownership and lifecycle rules stay in one place.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring JDBC, Flyway, MySQL 8/H2 MySQL mode, JUnit 5, MockMvc, React 18, TypeScript, Vite, Ant Design 5, TanStack Query 5, Vitest, Testing Library, Playwright.

## Global Constraints

- Keep Java source compatible with Java 8: no records, `List.of`, `Map.of`, streams-only APIs added after Java 8, or text blocks.
- Keep the existing modular monolith; do not add a service, queue, workflow engine, or front-end state library.
- MySQL remains the source of truth. Redis must not contain unique product business data.
- Product module depth is at most three; reject cycles, cross-product parents, and cross-product feature/version links in the backend.
- Use product lifecycle `PLANNING`, `ACTIVE`, `SUNSET`, `ARCHIVED`; version lifecycle `PLANNING`, `RELEASED`, `SUNSET`, `ARCHIVED`; feature/module lifecycle `PLANNING`, `ACTIVE`, `DEPRECATED`.
- New projects may bind only `ACTIVE` products and `RELEASED` versions. Existing project, baseline, and knowledge foreign keys must remain unchanged.
- Reads require `product:read`; writes require `product:write`; every query is scoped to the current `organization_id`.
- Use optimistic locking for mutable rows. Return 400 for validation, 403 for authorization, 404 for missing/cross-organization resources, and 409 for version/unique conflicts.
- Preserve unrelated changes already present in the shared worktree. Stage and commit only files listed by the current task.
- Follow TDD: prove each new test fails for the expected reason before writing production code.

---

## File Structure

### Backend

- `backend/src/main/resources/db/migration/V11__product_center.sql`: schema expansion, lifecycle migration, new permissions, and built-in role grants.
- `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogService.java`: organization-scoped product/version lifecycle and bindable catalog queries.
- `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`: product/version REST requests and audit calls.
- `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureService.java`: module-tree and feature invariants plus CRUD.
- `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java`: nested module/feature endpoints.
- `backend/src/main/java/com/zhilu/delivery/catalog/ProductVersionFeatureService.java`: replace/read version manifests and validate same-product links.
- `backend/src/main/java/com/zhilu/delivery/requirement/RequirementFeatureService.java`: requirement-feature coverage and uncovered state.
- `backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java`: coverage endpoints.
- `backend/src/main/java/com/zhilu/delivery/standardization/StandardizationService.java`: candidate-to-feature transaction and traceability.
- `backend/src/main/java/com/zhilu/delivery/standardization/StandardizationController.java`: conversion endpoint.
- `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`: released-version binding check.
- `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`: product read/write endpoint policy.

### Frontend

- `frontend/src/modules/product/types.ts`: product, module, feature, version, manifest, and coverage contracts.
- `frontend/src/modules/product/productApi.ts`: product-center REST client.
- `frontend/src/modules/product/ProductCenterRoutes.tsx`: list/detail route boundary.
- `frontend/src/modules/product/ProductListPage.tsx`: table/card view, filters, product editor.
- `frontend/src/modules/product/ProductDetailPage.tsx`: product header and four tabs.
- `frontend/src/modules/product/ProductStructureTab.tsx`: module tree and feature list/editors.
- `frontend/src/modules/product/ProductVersionsTab.tsx`: versions and manifest editor.
- `frontend/src/modules/product/ProductCoverageTab.tsx`: requirement coverage summary.
- `frontend/src/modules/requirement/FeatureCoverageDrawer.tsx`: edit a requirement's feature coverage.
- `frontend/src/modules/standardization/ConvertToFeatureDrawer.tsx`: convert a candidate to a feature.
- `frontend/src/styles/global.css`: product-center responsive layout and truncation rules.

---

### Task 1: Database Migration and Permission Baseline

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__product_center.sql`
- Modify: `backend/src/main/resources/db/demo/R__demo_data.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Modify fixture inserts in: `backend/src/test/java/com/zhilu/delivery/automation/AgentJobServiceTest.java`, `backend/src/test/java/com/zhilu/delivery/dashboard/DashboardQueryIT.java`, `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java`, `backend/src/test/java/com/zhilu/delivery/project/ProjectApiIT.java`, `backend/src/test/java/com/zhilu/delivery/project/ProjectAuthorizationIT.java`, `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`, `backend/src/test/java/com/zhilu/delivery/requirement/ClassificationServiceTest.java`, `backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java`, `backend/src/test/java/com/zhilu/delivery/resource/ResourceServiceTest.java`, `backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java`, `backend/src/test/java/com/zhilu/delivery/storage/FileServiceTest.java`

**Interfaces:**
- Consumes: existing `product`, `product_version`, `requirement_item`, and `standardization_debt` tables.
- Produces: `product_module`, `product_feature`, `product_version_feature`, `requirement_product_feature`, `standardization_debt_requirement`; `product.organization_id/description`; `standardization_debt.converted_feature_id`; permissions `product:read` and `product:write`.

- [ ] **Step 1: Write the failing schema contract**

Add this test to `SchemaBaselineTest`:

```java
@Test
void flywayCreatesProductCenterTablesAndPermissions() {
  Integer tables = jdbc.queryForObject(
      "select count(*) from information_schema.tables where table_schema='public' "
          + "and table_name in ('product_module','product_feature','product_version_feature',"
          + "'requirement_product_feature','standardization_debt_requirement')",
      Integer.class);
  Integer permissions = jdbc.queryForObject(
      "select count(*) from permission where code in ('product:read','product:write')",
      Integer.class);
  assertEquals(5, tables);
  assertEquals(2, permissions);
}
```

- [ ] **Step 2: Run the schema test and verify it fails**

Run: `cd backend && mvn -Dtest=SchemaBaselineTest test`

Expected: FAIL because the five product-center tables and two permissions do not exist.

- [ ] **Step 3: Add the migration**

Create `V11__product_center.sql` with the following concrete schema and migration sequence:

```sql
ALTER TABLE product ADD COLUMN organization_id BIGINT NULL;
ALTER TABLE product ADD COLUMN description TEXT NULL;
UPDATE product SET organization_id=(SELECT MIN(id) FROM organization) WHERE organization_id IS NULL;
ALTER TABLE product MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE product DROP INDEX uk_product_code;
ALTER TABLE product ADD CONSTRAINT fk_product_organization FOREIGN KEY (organization_id) REFERENCES organization(id);
ALTER TABLE product ADD CONSTRAINT uk_product_org_code UNIQUE (organization_id,code);
UPDATE product SET status='ARCHIVED' WHERE status='DISABLED';
UPDATE product_version SET status='RELEASED' WHERE status='ACTIVE';
UPDATE product_version SET status='ARCHIVED' WHERE status='DISABLED';

CREATE TABLE product_module (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  owner_user_id BIGINT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNING',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_module_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_module_parent FOREIGN KEY (parent_id) REFERENCES product_module(id),
  CONSTRAINT fk_product_module_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT uk_product_module_code UNIQUE (product_id,code)
);

CREATE TABLE product_feature (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  owner_user_id BIGINT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(180) NOT NULL,
  description TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNING',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_feature_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_feature_module FOREIGN KEY (module_id) REFERENCES product_module(id),
  CONSTRAINT fk_product_feature_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT uk_product_feature_code UNIQUE (product_id,code)
);

CREATE TABLE product_version_feature (
  product_version_id BIGINT NOT NULL,
  product_feature_id BIGINT NOT NULL,
  availability VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (product_version_id,product_feature_id),
  CONSTRAINT fk_version_feature_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_version_feature_feature FOREIGN KEY (product_feature_id) REFERENCES product_feature(id)
);

CREATE TABLE requirement_product_feature (
  requirement_id BIGINT NOT NULL,
  product_feature_id BIGINT NOT NULL,
  coverage_type VARCHAR(16) NOT NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (requirement_id,product_feature_id),
  CONSTRAINT fk_requirement_feature_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id),
  CONSTRAINT fk_requirement_feature_feature FOREIGN KEY (product_feature_id) REFERENCES product_feature(id),
  CONSTRAINT fk_requirement_feature_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE standardization_debt_requirement (
  standardization_debt_id BIGINT NOT NULL,
  requirement_id BIGINT NOT NULL,
  PRIMARY KEY (standardization_debt_id,requirement_id),
  CONSTRAINT fk_debt_requirement_debt FOREIGN KEY (standardization_debt_id) REFERENCES standardization_debt(id),
  CONSTRAINT fk_debt_requirement_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id)
);

ALTER TABLE standardization_debt ADD COLUMN converted_feature_id BIGINT NULL;
ALTER TABLE standardization_debt ADD CONSTRAINT fk_debt_converted_feature
  FOREIGN KEY (converted_feature_id) REFERENCES product_feature(id);

INSERT INTO permission(code,name,module)
SELECT 'product:read','查看产品中心','product'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='product:read');
INSERT INTO permission(code,name,module)
SELECT 'product:write','维护产品中心','product'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='product:write');
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='product:read'
WHERE r.code IN ('ADMIN','PMO','DELIVERY_MANAGER','DELIVERY_ENGINEER','TECH_MANAGER','PRODUCT_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='product:write'
WHERE r.code IN ('ADMIN','PRODUCT_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
```

- [ ] **Step 4: Update test fixtures for the new non-null organization and version status**

In every listed fixture, change product inserts to include that test's already-seeded organization ID and change active version inserts to `RELEASED`. Example for organization `600`:

```java
jdbc.update("insert into product(id,organization_id,code,name,status) "
    + "values (600,600,'ERP','智鹿 ERP','ACTIVE')");
jdbc.update("insert into product_version(id,product_id,version_name,status) "
    + "values (600,600,'V5.2','RELEASED')");
```

Use the existing IDs in each test: `700`, `800`, `1100`, `610`, `620`, `600`, `900`, `910`, `1200`, `1000`, and `500` respectively.

Update the demo seed in the same step so repeatable demo migration still runs after V11:

```sql
INSERT IGNORE INTO product(id,organization_id,owner_user_id,code,name,category,status) VALUES
  (100,100,105,'FIN-CLOUD','企业财务云','财务管理','ACTIVE'),
  (101,100,105,'SCM-CLOUD','智能供应链','供应链','ACTIVE');
INSERT IGNORE INTO product_version(id,product_id,version_name,release_date,status) VALUES
  (100,100,'V5.0','2026-03-31','RELEASED'),
  (101,100,'V4.8','2025-11-30','SUNSET'),
  (102,101,'V3.2','2026-05-15','RELEASED');
```

- [ ] **Step 5: Run migration and regression tests**

Run: `cd backend && mvn -Dtest=SchemaBaselineTest,AgentJobServiceTest,DashboardQueryIT,KnowledgeServiceTest,ProjectApiIT,ProjectAuthorizationIT,ProjectLifecycleTest,ClassificationServiceTest,RequirementApiIT,ResourceServiceTest,StandardizationServiceTest,FileServiceTest test`

Expected: PASS with no Flyway migration error and no old `ACTIVE` version fixture failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V11__product_center.sql backend/src/main/resources/db/demo/R__demo_data.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java backend/src/test/java/com/zhilu/delivery/automation/AgentJobServiceTest.java backend/src/test/java/com/zhilu/delivery/dashboard/DashboardQueryIT.java backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeServiceTest.java backend/src/test/java/com/zhilu/delivery/project/ProjectApiIT.java backend/src/test/java/com/zhilu/delivery/project/ProjectAuthorizationIT.java backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java backend/src/test/java/com/zhilu/delivery/requirement/ClassificationServiceTest.java backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java backend/src/test/java/com/zhilu/delivery/resource/ResourceServiceTest.java backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java backend/src/test/java/com/zhilu/delivery/storage/FileServiceTest.java
git commit -m "feat: add product center schema"
```

---

### Task 2: Organization-Scoped Product and Version Lifecycle

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`

**Interfaces:**
- Consumes: Task 1 schema and `CurrentUser.getOrganizationId()`.
- Produces: `products(long organizationId, boolean bindable)`, `product(long organizationId, long id)`, `versions(long organizationId, long productId, boolean bindable)` and lifecycle-aware create/update methods.

- [ ] **Step 1: Write failing catalog and security tests**

Add assertions that a user in organization `350` cannot read organization `351`, planning products are excluded by `bindable=true`, and permissions are separated:

```java
@Test
void scopesProductsAndReturnsOnlyBindableCatalog() throws Exception {
  long active = createProduct("ERP", "ERP");
  mvc.perform(put("/api/v1/products/{id}", active).with(writer()).with(csrf())
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"status\":\"ACTIVE\",\"version\":0}"))
      .andExpect(status().isOk());
  mvc.perform(post("/api/v1/products").with(actor(351L, "product:write")).with(csrf())
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"code\":\"OTHER\",\"name\":\"Other\"}"))
      .andExpect(status().isCreated());
  mvc.perform(get("/api/v1/products").param("bindable", "true").with(reader()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(active))
      .andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/v1/products/{id}", active + 1).with(reader()))
      .andExpect(status().isNotFound());
}
```

Update `SecurityAccessTest` so `product:read` can GET but cannot POST, `product:write` can POST, and `project:read` alone receives 403 for GET.

- [ ] **Step 2: Run tests and verify the expected failures**

Run: `cd backend && mvn -Dtest=ProductCatalogIT,SecurityAccessTest test`

Expected: FAIL because catalog reads are global, lifecycle is still `ACTIVE/DISABLED`, and GET currently accepts any authenticated user.

- [ ] **Step 3: Implement organization and lifecycle methods**

Change service signatures and SQL to require organization IDs. Use these exact status sets and optimistic update shape:

```java
private static final Set<String> PRODUCT_STATUSES = new HashSet<String>(
    Arrays.asList("PLANNING", "ACTIVE", "SUNSET", "ARCHIVED"));
private static final Set<String> VERSION_STATUSES = new HashSet<String>(
    Arrays.asList("PLANNING", "RELEASED", "SUNSET", "ARCHIVED"));

public List<Map<String, Object>> products(long organizationId, boolean bindable) {
  String sql = "select p.id,p.organization_id,p.owner_user_id,p.code,p.name,p.category,p.description,p.status,"
      + "p.updated_at,p.version,(select count(*) from product_module m where m.product_id=p.id) module_count,"
      + "(select count(*) from product_feature f where f.product_id=p.id) feature_count,"
      + "(select pv.version_name from product_version pv where pv.product_id=p.id order by pv.release_date desc,pv.id desc limit 1) latest_version_name "
      + "from product p where p.organization_id=?" + (bindable ? " and p.status='ACTIVE'" : "")
      + " order by name";
  return jdbc.query(sql, (row, index) -> productRow(row), organizationId);
}

public Map<String, Object> product(long organizationId, long id) {
  List<Map<String, Object>> values = jdbc.query(
      "select id,organization_id,owner_user_id,code,name,category,description,status,version "
          + "from product where id=? and organization_id=?",
      (row, index) -> productRow(row), id, organizationId);
  if (values.isEmpty()) throw new NotFoundException("产品不存在");
  return values.get(0);
}

private void requireReleaseable(LocalDate releaseDate, String status) {
  if ("RELEASED".equals(status) && releaseDate == null) {
    throw new IllegalArgumentException("已发布版本必须填写发布日期");
  }
}
```

For updates, include `and organization_id=? and version=?`, increment `version`, and throw `ConflictException("数据已被更新，请刷新后重试")` when the row exists but the update count is zero. Default new products and versions to `PLANNING`.

Enforce forward-only transitions with exact maps:

```java
private static final Map<String, String> PRODUCT_NEXT = transitions(
    "PLANNING", "ACTIVE", "ACTIVE", "SUNSET", "SUNSET", "ARCHIVED");
private static final Map<String, String> VERSION_NEXT = transitions(
    "PLANNING", "RELEASED", "RELEASED", "SUNSET", "SUNSET", "ARCHIVED");

private static Map<String, String> transitions(String... pairs) {
  Map<String, String> values = new HashMap<String, String>();
  for (int index = 0; index < pairs.length; index += 2) {
    values.put(pairs[index], pairs[index + 1]);
  }
  return Collections.unmodifiableMap(values);
}
```

Allow keeping the same status during ordinary edits, allow `PLANNING -> ARCHIVED` to discard unused drafts, and reject every other skip or backward transition. Any `ARCHIVED` row is read-only.

- [ ] **Step 4: Wire current user, request versions, audit, and security**

Controller reads must pass `user.getOrganizationId()`. Add `description` and `version` to requests. Replace product security rules with:

```java
.antMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("product:read")
.antMatchers("/api/v1/products/**").hasAuthority("product:write")
```

The list endpoints accept `@RequestParam(defaultValue = "false") boolean bindable`. Keep existing audit resource types.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=ProductCatalogIT,SecurityAccessTest test`

Expected: PASS; cross-organization IDs are 404, permission mismatches are 403, and bindable lists contain only `ACTIVE/RELEASED` values.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogService.java backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java
git commit -m "feat: enforce product lifecycle and scope"
```

---

### Task 3: Product Module Tree and Feature Library

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/catalog/ProductStructureIT.java`

**Interfaces:**
- Consumes: `ProductCatalogService.product(long organizationId, long id)` and Task 1 tables.
- Produces: `modules`, `saveModule`, `features`, `saveFeature`; REST endpoints under `/api/v1/products/{productId}/modules|features`.

- [ ] **Step 1: Write failing tree and feature tests**

Cover creation, ordered tree reads, three-level maximum, cycle rejection, cross-product parent rejection, cross-product feature module rejection, duplicate codes, archived product read-only behavior, and optimistic conflicts. The depth assertion is:

```java
mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, rootId)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentId\":" + levelThreeId
            + ",\"code\":\"ROOT\",\"name\":\"Root\",\"status\":\"ACTIVE\",\"version\":0}"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.message").value("模块树最多三级且不能成环"));
```

- [ ] **Step 2: Run the new test and verify it fails**

Run: `cd backend && mvn -Dtest=ProductStructureIT test`

Expected: FAIL with 404 because module and feature endpoints do not exist.

- [ ] **Step 3: Implement module-tree invariants**

Create `ProductStructureService` with public signatures:

```java
public List<Map<String, Object>> modules(long organizationId, long productId)
public Map<String, Object> saveModule(long organizationId, long actorId, long productId,
    Long id, Long parentId, Long ownerUserId, String code, String name,
    String description, String status, int sortOrder, long version)
public List<Map<String, Object>> features(long organizationId, long productId, Long moduleId)
public Map<String, Object> saveFeature(long organizationId, long actorId, long productId,
    Long id, long moduleId, Long ownerUserId, String code, String name,
    String description, String status, long version)
```

Validate depth and cycles before insert/update by walking parents with a bounded loop:

```java
private void validateParent(long productId, Long moduleId, Long parentId) {
  Long cursor = parentId;
  int depth = 1;
  while (cursor != null) {
    if (moduleId != null && moduleId.equals(cursor)) {
      throw new IllegalArgumentException("模块树最多三级且不能成环");
    }
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select parent_id from product_module where id=? and product_id=?", cursor, productId);
    if (rows.isEmpty()) throw new IllegalArgumentException("父模块不属于当前产品");
    cursor = rows.get(0).get("parent_id") == null
        ? null : ((Number) rows.get(0).get("parent_id")).longValue();
    if (++depth > 3) throw new IllegalArgumentException("模块树最多三级且不能成环");
  }
}
```

Also calculate the moved node's descendant height and reject a move when `newParentDepth + subtreeHeight > 3`; this prevents moving an existing subtree below level three. Module and feature statuses are forward-only `PLANNING -> ACTIVE -> DEPRECATED`, with same-status edits allowed and no edits after `DEPRECATED`.

- [ ] **Step 4: Implement feature ownership and controller requests**

Before feature writes, query `product_module where id=? and product_id=?`; reject zero rows. Validate owner organization and product status not `ARCHIVED`. Map duplicate keys to `ConflictException`. Expose POST for create and PUT for update, and use `AuditService` resource types `PRODUCT_MODULE` and `PRODUCT_FEATURE`.

Request DTO fields must be exact:

```java
public static final class ModuleRequest {
  public Long parentId; public Long ownerUserId;
  @NotBlank public String code; @NotBlank public String name;
  public String description; public String status = "PLANNING";
  public int sortOrder; public long version;
}
public static final class FeatureRequest {
  @NotNull public Long moduleId; public Long ownerUserId;
  @NotBlank public String code; @NotBlank public String name;
  public String description; public String status = "PLANNING"; public long version;
}
```

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -Dtest=ProductStructureIT test`

Expected: PASS for valid three-level trees and all rejected ownership/depth/cycle cases.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureService.java backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java backend/src/test/java/com/zhilu/delivery/catalog/ProductStructureIT.java
git commit -m "feat: add product modules and features"
```

---

### Task 4: Version Feature Manifests and Project Binding

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductVersionFeatureService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/catalog/ProductVersionFeatureIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`

**Interfaces:**
- Consumes: version and feature lifecycle services from Tasks 2-3.
- Produces: `manifest(long organizationId, long productId, long versionId)`, `replaceManifest(long organizationId, long productId, long versionId, long expectedVersion, List<ManifestEntry> entries)`, and `appendPlannedFeature(long organizationId, long productId, long versionId, long featureId)`; bindable project validation.

- [ ] **Step 1: Write failing manifest and project-binding tests**

The tests must prove: a manifest accepts same-product features; rejects cross-product features and invalid availability; publishing requires a date and at least one `INCLUDED`; project creation accepts only active/released pairs; replacing a manifest with a stale version returns 409.

```java
mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features", productId, versionId)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":0,\"entries\":[{\"featureId\":" + featureId
            + ",\"availability\":\"INCLUDED\"}]}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.entries[0].featureId").value(featureId));
```

- [ ] **Step 2: Run tests and verify failures**

Run: `cd backend && mvn -Dtest=ProductVersionFeatureIT,ProjectLifecycleTest test`

Expected: FAIL because the manifest endpoint does not exist and project creation still requires status `ACTIVE` on the version.

- [ ] **Step 3: Implement atomic manifest replacement**

Create the service with an immutable Java 8 entry DTO and one transaction:

```java
public static final class ManifestEntry {
  private final long featureId;
  private final String availability;
  public ManifestEntry(long featureId, String availability) {
    this.featureId = featureId; this.availability = availability;
  }
  public long getFeatureId() { return featureId; }
  public String getAvailability() { return availability; }
}

@Transactional
public Map<String, Object> replaceManifest(long organizationId, long productId,
    long versionId, long expectedVersion, List<ManifestEntry> entries) {
  catalog.version(organizationId, productId, versionId);
  validateEntries(productId, entries);
  int changed = jdbc.update("update product_version set version=version+1,"
      + "updated_at=current_timestamp where id=? and product_id=? and version=?",
      versionId, productId, expectedVersion);
  if (changed == 0) throw new ConflictException("版本清单已被更新，请刷新后重试");
  jdbc.update("delete from product_version_feature where product_version_id=?", versionId);
  for (ManifestEntry entry : entries) {
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,availability) values (?,?,?)",
        versionId, entry.getFeatureId(), entry.getAvailability());
  }
  return manifest(organizationId, productId, versionId);
}
```

Use a `HashSet<Long>` to reject duplicate feature IDs and only allow `INCLUDED`, `PLANNED`, `REMOVED`.

Add the transaction-aware helper used by standardization conversion. It validates the version and feature product IDs, requires version status `PLANNING`, keeps existing manifest rows, and inserts only when absent:

```java
public void appendPlannedFeature(long organizationId, long productId,
    long versionId, long featureId) {
  Map<String, Object> version = catalog.version(organizationId, productId, versionId);
  if (!"PLANNING".equals(version.get("status"))) {
    throw new ConflictException("只能向规划中版本加入候选功能");
  }
  Integer valid = jdbc.queryForObject(
      "select count(*) from product_feature where id=? and product_id=?",
      Integer.class, featureId, productId);
  if (valid == null || valid != 1) throw new NotFoundException("产品功能不存在");
  Integer exists = jdbc.queryForObject(
      "select count(*) from product_version_feature where product_version_id=? and product_feature_id=?",
      Integer.class, versionId, featureId);
  if (exists != null && exists == 0) {
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,availability) values (?,?,'PLANNED')",
        versionId, featureId);
  }
}
```

- [ ] **Step 4: Enforce release and project binding rules**

Before updating a version to `RELEASED`, count its `INCLUDED` manifest entries and require at least one. Change `ProjectService.create` validation SQL to:

```java
"select count(*) from product_version pv join product p on p.id=pv.product_id "
    + "where pv.id=? and pv.product_id=? and pv.status='RELEASED' "
    + "and p.status='ACTIVE' and p.organization_id=?"
```

Pass command organization ID as the third argument and change the error to `产品或版本不可用于新项目`.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=ProductVersionFeatureIT,ProjectLifecycleTest test`

Expected: PASS; released manifests and new project bindings obey all lifecycle constraints.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductVersionFeatureService.java backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureController.java backend/src/main/java/com/zhilu/delivery/project/ProjectService.java backend/src/test/java/com/zhilu/delivery/catalog/ProductVersionFeatureIT.java backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java
git commit -m "feat: manage version feature manifests"
```

---

### Task 5: Requirement-to-Feature Coverage API

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementFeatureService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/requirement/ProductCoverageController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java`

**Interfaces:**
- Consumes: `requirement_item -> delivery_project.product_id` and `product_feature`.
- Produces: `coverage(long requirementId, CurrentUser user)`, `replaceCoverage(long requirementId, CurrentUser user, List<CoverageEntry>)`, and `productCoverage(long organizationId, long productId)` exposed as `GET /api/v1/products/{productId}/coverage`.

- [ ] **Step 1: Write failing coverage tests**

Prove multiple links are accepted, each keeps `FULL/PARTIAL`, cross-product and cross-organization links are rejected, duplicate feature IDs are rejected, `fullyCovered` is true when any entry is `FULL`, and the product coverage endpoint returns full/partial counts grouped by feature.

```java
mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"entries\":[{\"featureId\":" + firstFeature
            + ",\"coverageType\":\"PARTIAL\"},{\"featureId\":" + secondFeature
            + ",\"coverageType\":\"FULL\"}]}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.fullyCovered").value(true))
    .andExpect(jsonPath("$.entries.length()").value(2));
```

- [ ] **Step 2: Run and verify failure**

Run: `cd backend && mvn -Dtest=RequirementApiIT test`

Expected: FAIL with 404 for the new nested endpoint.

- [ ] **Step 3: Implement coverage replacement and reads**

Create DTO and transactional service methods:

```java
public static final class CoverageEntry {
  private final long featureId; private final String coverageType;
  public CoverageEntry(long featureId, String coverageType) {
    this.featureId = featureId; this.coverageType = coverageType;
  }
  public long getFeatureId() { return featureId; }
  public String getCoverageType() { return coverageType; }
}

@Transactional
public Map<String, Object> replaceCoverage(long requirementId, CurrentUser user,
    List<CoverageEntry> entries) {
  Map<String, Object> context = requirementContext(requirementId, user);
  validateFeatures(((Number) context.get("product_id")).longValue(), entries);
  jdbc.update("delete from requirement_product_feature where requirement_id=?", requirementId);
  for (CoverageEntry entry : entries) {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,coverage_type,source,created_by) values (?,?,?,'MANUAL',?)",
        requirementId, entry.getFeatureId(), entry.getCoverageType(), user.getId());
  }
  return coverage(requirementId, user);
}
```

The requirement read query joins module and feature names and returns `{ requirementId, fullyCovered, entries }`. Include `p.product_id` as `productId` in requirement list/detail mapping so the front end can load only the bound product's feature library. Use `RequirementController.get(id,user)` before reads/writes so existing project data-scope rules remain authoritative.

`productCoverage` first checks `product where id=? and organization_id=?`, then groups by feature and returns `featureId`, `featureCode`, `featureName`, `moduleName`, `fullCount`, and `partialCount`. It also returns `uncoveredRequirements`: requirements for projects bound to the product that have no `FULL` link, including `requirementId`, `requirementCode`, `title`, `projectCode`, and whether a debt link already exists. `ProductCoverageController` is in the requirement module to avoid making catalog depend on requirement internals, but maps the read endpoint under `/api/v1/products/{productId}/coverage`; existing product security applies.

- [ ] **Step 4: Add controller endpoints**

Add `GET` and `PUT /{id}/product-features`. Request shape:

```java
public static final class CoverageRequest {
  @NotNull public List<CoverageItem> entries;
}
public static final class CoverageItem {
  @NotNull public Long featureId;
  @NotBlank public String coverageType;
}
```

The existing requirement security rules already require `requirement:read` for GET and `requirement:write` for PUT.

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=RequirementApiIT test`

Expected: PASS with multiple coverage rows and full traceable names.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/requirement/RequirementFeatureService.java backend/src/main/java/com/zhilu/delivery/requirement/ProductCoverageController.java backend/src/main/java/com/zhilu/delivery/requirement/RequirementController.java backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java
git commit -m "feat: link requirements to product features"
```

---

### Task 6: Convert Standardization Candidates to Product Features

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/standardization/StandardizationService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/standardization/StandardizationController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`

**Interfaces:**
- Consumes: `ProductStructureService.saveFeature`, `ProductVersionFeatureService.replaceManifest`, requirement coverage tables, and `standardization_debt`.
- Produces: `createCandidateFromRequirement(long requirementId, CurrentUser user)` and `convertToFeature(long debtId, CurrentUser user, ConvertFeatureCommand command)` returning debt and created feature IDs.

- [ ] **Step 1: Write failing conversion and rollback tests**

First prove a requirement with no `FULL` coverage can create one `CANDIDATE` debt and one `standardization_debt_requirement` row, while a fully covered requirement is rejected. In `SecurityAccessTest`, prove candidate creation accepts `requirement:write` or `standardization:write` and rejects read-only roles. Then seed a CANDIDATE debt linked to two requirements. Verify conversion creates one feature, adds an explicitly selected `PLANNED` manifest membership, creates `PARTIAL` coverage links for both requirements, sets debt status `INCLUDED` and `converted_feature_id`, and writes audit. Add a second conversion test with a cross-product module and assert no rows were created. Assert the debt JSON includes both `convertedFeatureId` and optimistic-lock `version`.

```java
assertEquals(Integer.valueOf(0), jdbc.queryForObject(
    "select count(*) from product_feature where code='BROKEN-CONVERT'", Integer.class));
assertEquals("CANDIDATE", jdbc.queryForObject(
    "select status from standardization_debt where id=?", String.class, debtId));
```

- [ ] **Step 2: Run the standardization test and verify failure**

Run: `cd backend && mvn -Dtest=StandardizationServiceTest,SecurityAccessTest test`

Expected: FAIL because conversion and traceability do not exist.

- [ ] **Step 3: Implement one transaction for conversion**

Add candidate creation before conversion. It derives the product version from the requirement's project, rejects any existing `FULL` coverage, creates an idempotent `pattern_key` of `REQUIREMENT:<requirementId>`, and inserts the trace row in one transaction:

```java
@Transactional
public Map<String, Object> createCandidateFromRequirement(long requirementId, CurrentUser user) {
  Map<String, Object> context = jdbc.queryForMap(
      "select r.title,p.product_version_id from requirement_item r join delivery_project p on p.id=r.project_id "
          + "where r.id=? and r.organization_id=?",
      requirementId, user.getOrganizationId());
  Integer full = jdbc.queryForObject(
      "select count(*) from requirement_product_feature where requirement_id=? and coverage_type='FULL'",
      Integer.class, requirementId);
  if (full != null && full > 0) throw new ConflictException("需求已被产品功能完全覆盖");
  long versionId = ((Number) context.get("product_version_id")).longValue();
  String pattern = "REQUIREMENT:" + requirementId;
  try {
    jdbc.update("insert into standardization_debt(product_version_id,pattern_key,title,occurrence_count,distinct_projects,status) values (?,?,?,1,1,'CANDIDATE')",
        versionId, pattern, String.valueOf(context.get("title")));
  } catch (DuplicateKeyException duplicate) {
    throw new ConflictException("该需求已进入标准化候选");
  }
  Long debtId = jdbc.queryForObject(
      "select id from standardization_debt where product_version_id=? and pattern_key=?",
      Long.class, versionId, pattern);
  jdbc.update("insert into standardization_debt_requirement(standardization_debt_id,requirement_id) values (?,?)",
      debtId, requirementId);
  return debt(debtId, user.getOrganizationId());
}
```

Add a Java 8 command class containing `productId`, `moduleId`, optional `productVersionId`, `code`, `name`, `description`, `ownerUserId`, and expected debt `version`. Implement:

```java
@Transactional
public Map<String, Object> convertToFeature(long debtId, CurrentUser user,
    ConvertFeatureCommand command) {
  Map<String, Object> debt = debt(debtId, user.getOrganizationId());
  if (!"CANDIDATE".equals(debt.get("status")) && !"PENDING".equals(debt.get("status"))) {
    throw new ConflictException("只有候选或待处理债务可转为产品功能");
  }
  Map<String, Object> feature = structures.saveFeature(user.getOrganizationId(), user.getId(),
      command.getProductId(), null, command.getModuleId(), command.getOwnerUserId(),
      command.getCode(), command.getName(), command.getDescription(), "PLANNING", 0L);
  long featureId = ((Number) feature.get("id")).longValue();
  linkDebtRequirements(debtId, featureId, user.getId(), command.getProductId());
  if (command.getProductVersionId() != null) {
    manifests.appendPlannedFeature(user.getOrganizationId(), command.getProductId(),
        command.getProductVersionId(), featureId);
  }
  String targetVersion = command.getProductVersionId() == null ? null
      : String.valueOf(catalog.version(user.getOrganizationId(), command.getProductId(),
          command.getProductVersionId()).get("versionName"));
  int changed = jdbc.update("update standardization_debt set status='INCLUDED',converted_feature_id=?,"
      + "target_version=?,version=version+1,updated_at=current_timestamp where id=? and version=?",
      featureId, targetVersion, debtId, command.getVersion());
  if (changed == 0) throw new ConflictException("标准化债务已被更新，请刷新后重试");
  audit.record(user.getOrganizationId(), user.getId(), "CONVERT_TO_FEATURE",
      "STANDARDIZATION_DEBT", String.valueOf(debtId), command.getCode());
  return debt(debtId, user.getOrganizationId());
}
```

`linkDebtRequirements` must first confirm every linked requirement belongs to a project using `command.productId`; otherwise throw before inserting coverage rows.

- [ ] **Step 4: Expose candidate and conversion endpoints**

Add `POST /api/v1/standardization/debts/from-requirement` with `{ "requirementId": number }`, plus `POST /api/v1/standardization/debts/{id}/convert-to-feature` with validated request fields and `@AuthenticationPrincipal CurrentUser`. Return 201 for candidate creation and 200 for conversion, with the updated debt including `convertedFeatureId`.

In `SecurityConfig`, place this rule before the general standardization write matcher so delivery engineers can submit uncovered requirements while product managers/PMO can do the same:

```java
.antMatchers(HttpMethod.POST, "/api/v1/standardization/debts/from-requirement")
    .access("hasAuthority('requirement:write') or hasAuthority('standardization:write')")
```

- [ ] **Step 5: Run focused tests**

Run: `cd backend && mvn -Dtest=StandardizationServiceTest,SecurityAccessTest test`

Expected: PASS; success is fully traceable and invalid conversion leaves all tables unchanged.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/standardization/StandardizationService.java backend/src/main/java/com/zhilu/delivery/standardization/StandardizationController.java backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java
git commit -m "feat: convert standardization debt to features"
```

---

### Task 7: Product Center Navigation, Contracts, and List Page

**Files:**
- Create: `frontend/src/modules/product/types.ts`
- Create: `frontend/src/modules/product/productApi.ts`
- Create: `frontend/src/modules/product/ProductCenterRoutes.tsx`
- Create: `frontend/src/modules/product/ProductListPage.tsx`
- Create: `frontend/src/modules/product/ProductDetailPage.tsx`
- Create: `frontend/src/modules/product/ProductListPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/homeRoute.ts`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/AppShell.test.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.test.tsx`
- Modify: `frontend/src/modules/admin/AdminFlows.test.tsx`
- Modify: `frontend/src/modules/admin/adminApi.ts`
- Modify: `frontend/src/modules/admin/adminApi.test.ts`
- Modify: `frontend/src/modules/admin/types.ts`
- Delete: `frontend/src/modules/admin/ProductsPage.tsx`

**Interfaces:**
- Consumes: Tasks 2-4 product APIs.
- Produces: `/products` list/card UI, product editor, `/products/:productId` routing boundary, and `/admin/products -> /products` redirect.

- [ ] **Step 1: Write failing navigation and list tests**

Mock `/api/v1/products` with one active and one planning product. Assert the rail order, filter, table/card switch, editor payload, and admin redirect:

```tsx
expect(screen.getByRole('link', { name: /产品中心/ })).toBeVisible()
const links = screen.getAllByRole('link').map(link => link.textContent)
expect(links.indexOf('产品中心')).toBeGreaterThan(links.indexOf('项目空间'))
expect(links.indexOf('产品中心')).toBeLessThan(links.indexOf('需求工坊'))
await user.click(screen.getByRole('radio', { name: '卡片' }))
expect(screen.getByTestId('product-card-grid')).toBeVisible()
```

- [ ] **Step 2: Run tests and verify failures**

Run: `cd frontend && pnpm test:run src/components/AppShell.test.tsx src/modules/admin/AdminPage.test.tsx src/modules/product/ProductListPage.test.tsx`

Expected: FAIL because the product module, route, and navigation item do not exist.

- [ ] **Step 3: Add typed API contracts**

Define exact unions and objects in `types.ts`:

```ts
export type ProductStatus = 'PLANNING' | 'ACTIVE' | 'SUNSET' | 'ARCHIVED'
export type VersionStatus = 'PLANNING' | 'RELEASED' | 'SUNSET' | 'ARCHIVED'
export type StructureStatus = 'PLANNING' | 'ACTIVE' | 'DEPRECATED'
export type Availability = 'INCLUDED' | 'PLANNED' | 'REMOVED'
export interface Product { id: number; organizationId: number; ownerUserId?: number; code: string; name: string; category?: string; description?: string; status: ProductStatus; moduleCount: number; featureCount: number; latestVersionName?: string; updatedAt: string; version: number }
export interface ProductVersion { id: number; productId: number; versionName: string; releaseDate?: string; status: VersionStatus; version: number }
export interface ProductModule { id: number; productId: number; parentId?: number; ownerUserId?: number; code: string; name: string; description?: string; status: StructureStatus; sortOrder: number; version: number }
export interface ProductFeature { id: number; productId: number; moduleId: number; ownerUserId?: number; code: string; name: string; description?: string; status: StructureStatus; version: number }
```

`productApi.ts` uses the shared `api` helper and exposes `products`, `product`, `saveProduct`, `versions`, `saveVersion`, `modules`, `saveModule`, `features`, `saveFeature`, `manifest`, `replaceManifest`, and `coverage`. Task 2's product list query must return `moduleCount`, `featureCount`, `latestVersionName`, and `updatedAt` using correlated counts/latest-version subqueries so the page never issues one request per product.

- [ ] **Step 4: Build routes and list/card page**

`ProductCenterRoutes` must be:

```tsx
export function ProductCenterRoutes() {
  return <Routes>
    <Route index element={<ProductListPage />} />
    <Route path=":productId" element={<ProductDetailPage />} />
    <Route path="*" element={<Navigate to="/products" replace />} />
  </Routes>
}
```

In this task, create `ProductDetailPage` as a working overview boundary: load `/api/v1/products/{productId}`, show back navigation, product name/code/status, module/feature/version counts, last update time, loading/error states, and an archived read-only alert. Task 8 expands this same file with the three management tabs and coverage tab; there is no temporary placeholder route.

`ProductListPage` owns keyword/category/status/owner filters, a `Segmented` table/card control, and a drawer. Save payload always includes `version: value?.version ?? 0`; product code is disabled while editing; archived products open read-only.

- [ ] **Step 5: Wire application and admin redirect**

Lazy-load `ProductCenterRoutes` in `App.tsx` behind `product:read`. Add the rail entry between projects and requirements. Add `{ path: '/products', permission: 'product:read' }` after projects in `homeRoute.ts`. In `AdminPage`, replace the products page element with `<Navigate to="/products" replace />`; do not navigate `/products` back to admin. Delete the old `ProductsPage`, its `AdminFlows` test case, and product/version functions and types from `adminApi`; all product traffic now goes through `modules/product/productApi.ts`.

- [ ] **Step 6: Run tests and build**

Run: `cd frontend && pnpm test:run src/components/AppShell.test.tsx src/modules/admin/AdminPage.test.tsx src/modules/product/ProductListPage.test.tsx && pnpm build`

Expected: all tests PASS and TypeScript/Vite build exits 0.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/App.tsx frontend/src/app/homeRoute.ts frontend/src/components/AppShell.tsx frontend/src/components/AppShell.test.tsx frontend/src/modules/admin/AdminPage.tsx frontend/src/modules/admin/AdminPage.test.tsx frontend/src/modules/admin/AdminFlows.test.tsx frontend/src/modules/admin/adminApi.ts frontend/src/modules/admin/adminApi.test.ts frontend/src/modules/admin/types.ts frontend/src/modules/admin/ProductsPage.tsx frontend/src/modules/product/types.ts frontend/src/modules/product/productApi.ts frontend/src/modules/product/ProductCenterRoutes.tsx frontend/src/modules/product/ProductListPage.tsx frontend/src/modules/product/ProductDetailPage.tsx frontend/src/modules/product/ProductListPage.test.tsx
git commit -m "feat: add product center list and navigation"
```

---

### Task 8: Product Detail, Structure, Version, and Coverage Tabs

**Files:**
- Modify: `frontend/src/modules/product/ProductDetailPage.tsx`
- Create: `frontend/src/modules/product/ProductStructureTab.tsx`
- Create: `frontend/src/modules/product/ProductVersionsTab.tsx`
- Create: `frontend/src/modules/product/ProductCoverageTab.tsx`
- Create: `frontend/src/modules/product/ProductDetailPage.test.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: `productApi` and types from Task 7.
- Produces: four-tab product detail UI with module tree, feature CRUD, version manifest CRUD, coverage summary, and archived read-only behavior.

- [ ] **Step 1: Write failing detail interaction tests**

Mock product, modules, features, versions, and manifest endpoints. Test tab labels, tree selection, three-level rendering, feature editor, version editor, manifest replacement, error retry, and archived disabled controls:

```tsx
expect(await screen.findByRole('tab', { name: '模块与功能' })).toBeVisible()
await user.click(screen.getByText('财务管理'))
expect(await screen.findByText('应收对账')).toBeVisible()
await user.click(screen.getByRole('tab', { name: '版本' }))
expect(await screen.findByText('V5.2')).toBeVisible()
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx`

Expected: FAIL because the detail components do not exist.

- [ ] **Step 3: Build product header and tab query boundaries**

`ProductDetailPage` reads `productId` with `Number(useParams().productId)`, rejects non-positive/NaN IDs with the existing error state, and uses these tab keys: `overview`, `structure`, `versions`, `coverage`. Each heavy tab owns its own queries so changing tabs does not refetch unrelated data. Archived status sets `readOnly=true` for all editors.

- [ ] **Step 4: Build module and feature interaction**

Convert the flat module result into Ant Tree nodes without mutating query data:

```ts
export function buildModuleTree(values: ProductModule[], parentId?: number): DataNode[] {
  return values.filter(item => item.parentId === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'zh-CN'))
    .map(item => ({ key: item.id, title: `${item.code} · ${item.name}`, children: buildModuleTree(values, item.id) }))
}
```

Selecting a node filters the right-side feature table. Module and feature drawers send optimistic `version`. Hide the parent selector options that would exceed depth or place a node below its descendant; backend errors remain authoritative and display through `message.error`.

- [ ] **Step 5: Build version and manifest interaction**

Use a version table on the left and searchable feature manifest on the right. `replaceManifest` sends the selected version's current `version` and all rows:

```ts
await productApi.replaceManifest(productId, version.id, {
  version: version.version,
  entries: rows.map(row => ({ featureId: row.featureId, availability: row.availability })),
})
```

Disable transition to `RELEASED` when release date is empty or no row is `INCLUDED`, while still showing backend validation messages.

- [ ] **Step 6: Build coverage summary and CSS**

`ProductCoverageTab` groups coverage rows by feature and shows full/partial counts with links back to requirement context. Add `.product-list-*`, `.product-detail-*`, `.product-structure-*`, and `.version-manifest-*` rules. Every flexible grid child uses `min-width: 0`; long names/descriptions use line clamp or ellipsis so cards never overflow.

- [ ] **Step 7: Run tests and build**

Run: `cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx src/modules/product/ProductListPage.test.tsx && pnpm build`

Expected: PASS and build exits 0.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/product/ProductDetailPage.tsx frontend/src/modules/product/ProductStructureTab.tsx frontend/src/modules/product/ProductVersionsTab.tsx frontend/src/modules/product/ProductCoverageTab.tsx frontend/src/modules/product/ProductDetailPage.test.tsx frontend/src/styles/global.css
git commit -m "feat: add product detail management"
```

---

### Task 9: Requirement Coverage and Standardization Conversion UI

**Files:**
- Create: `frontend/src/modules/requirement/FeatureCoverageDrawer.tsx`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.tsx`
- Modify: `frontend/src/modules/requirement/requirementApi.ts`
- Modify: `frontend/src/modules/requirement/types.ts`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.test.tsx`
- Create: `frontend/src/modules/standardization/ConvertToFeatureDrawer.tsx`
- Modify: `frontend/src/modules/standardization/StandardizationPage.tsx`
- Modify: `frontend/src/modules/standardization/standardizationApi.ts`
- Modify: `frontend/src/modules/standardization/types.ts`
- Modify: `frontend/src/modules/standardization/StandardizationPage.test.tsx`

**Interfaces:**
- Consumes: Task 5 coverage endpoint, Task 6 conversion endpoint, and Task 7 product API.
- Produces: requirement list action `功能覆盖` and debt action `转为产品功能`.

- [ ] **Step 1: Write failing integration UI tests**

Requirement test: open coverage drawer, select two features, set `PARTIAL/FULL`, save, and assert PUT body; then leave only partial coverage, click `加入标准化候选`, and assert the candidate POST. Standardization test: click conversion, choose module and a planning version, save, and assert POST path/body.

```tsx
expect(requests.find(request => request.path === '/api/v1/requirements/1/product-features' && request.init?.method === 'PUT')).toMatchObject({
  body: JSON.stringify({ entries: [{ featureId: 11, coverageType: 'FULL' }] }),
})
```

- [ ] **Step 2: Run tests and verify failures**

Run: `cd frontend && pnpm test:run src/modules/requirement/RequirementWorkshop.test.tsx src/modules/standardization/StandardizationPage.test.tsx`

Expected: FAIL because the actions, drawers, and API methods do not exist.

- [ ] **Step 3: Implement requirement coverage editor**

Add types:

```ts
export interface FeatureCoverageEntry { featureId: number; featureCode: string; featureName: string; moduleName: string; coverageType: 'FULL' | 'PARTIAL' }
export interface RequirementCoverage { requirementId: number; fullyCovered: boolean; entries: FeatureCoverageEntry[] }
```

Add `productId: number` to `Requirement`, then add `requirementApi.coverage(id)`, `replaceCoverage(id, entries)`, and `createStandardizationCandidate(id)`. The drawer loads product features using `requirement.productId`, uses a repeatable `Form.List`, and invalidates `requirements`, `requirement-coverage`, `standardization-debts`, and `product-coverage` query keys after save/candidate creation. Show `加入标准化候选` only when no entry is `FULL`; disable it after the API reports an existing candidate.

- [ ] **Step 4: Implement candidate conversion editor**

Extend `StandardizationDebt` with `convertedFeatureId?: number` and `version: number`, and add:

```ts
convertToFeature: (id: number, input: Record<string, unknown>) =>
  api<StandardizationDebt>(`/api/v1/standardization/debts/${id}/convert-to-feature`, {
    method: 'POST', body: JSON.stringify(input),
  })
```

The drawer loads products, then modules and `PLANNING` versions for the selected product. Prefill name from debt title, require code and module, keep owner optional, include debt `version`, and invalidate debts, product features, product manifest, and product coverage after success.

- [ ] **Step 5: Run tests and build**

Run: `cd frontend && pnpm test:run src/modules/requirement/RequirementWorkshop.test.tsx src/modules/standardization/StandardizationPage.test.tsx && pnpm build`

Expected: PASS; request payloads match backend contracts and TypeScript build exits 0.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/modules/requirement/FeatureCoverageDrawer.tsx frontend/src/modules/requirement/RequirementWorkshop.tsx frontend/src/modules/requirement/requirementApi.ts frontend/src/modules/requirement/types.ts frontend/src/modules/requirement/RequirementWorkshop.test.tsx frontend/src/modules/standardization/ConvertToFeatureDrawer.tsx frontend/src/modules/standardization/StandardizationPage.tsx frontend/src/modules/standardization/standardizationApi.ts frontend/src/modules/standardization/types.ts frontend/src/modules/standardization/StandardizationPage.test.tsx
git commit -m "feat: connect product features to delivery flow"
```

---

### Task 10: Full Regression, End-to-End Flow, and Documentation Status

**Files:**
- Create: `frontend/e2e/product-center.e2e.ts`
- Modify: `docs/superpowers/specs/2026-07-13-product-center-expansion-design.md`
- Modify only if failures prove necessary: files changed in Tasks 1-9.

**Interfaces:**
- Consumes: all product-center backend and frontend work.
- Produces: browser-level proof of the complete product -> version -> project -> requirement -> standardization -> feature traceability flow.

- [ ] **Step 1: Write the failing Playwright scenario**

Add a serial test that logs in with the seeded administrator, opens Product Center, creates a uniquely suffixed product, creates three module levels and a feature, creates a planning version, includes the feature, releases it, and verifies the product detail tabs. Then open seeded product `FIN-CLOUD`, create a feature under a new E2E module, link that feature to seeded requirement `REQ-260001`, and convert the seeded `reconciliation.retry` debt candidate into a second feature under the same product. This uses deterministic demo data and never conditionally skips a business step.

```ts
test('product capability flows from catalog to delivery and back', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号').fill('admin')
  await page.getByLabel('密码').fill('Admin@123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('link', { name: '产品中心' }).click()
  await expect(page.getByRole('heading', { name: '产品中心' })).toBeVisible()
  await page.getByRole('button', { name: '新建产品' }).click()
  await page.getByLabel('产品编码').fill(`E2E-${Date.now()}`)
  await page.getByLabel('产品名称').fill('产品中心 E2E')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('产品中心 E2E')).toBeVisible()
})
```

- [ ] **Step 2: Run backend full regression**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS; no migration, security, project, requirement, standardization, storage, resource, knowledge, or agent regression.

- [ ] **Step 3: Run frontend full regression and build**

Run: `cd frontend && pnpm test:run && pnpm build`

Expected: all Vitest suites PASS and production build exits 0.

- [ ] **Step 4: Run end-to-end test**

Start the verified local stack first: `docker compose up -d --build`

Then run: `cd frontend && pnpm e2e -- product-center.e2e.ts`

Expected: the Product Center scenario PASSes without console errors or page crashes.

- [ ] **Step 5: Perform visual browser verification**

At desktop width 1440 and compact width 1024, inspect `/products`, product structure, version manifest, requirement coverage drawer, and conversion drawer. Verify no horizontal text overflow, table/card toggle stability, module tree scrolling, archived read-only controls, and no white-screen/redirect loop. Capture screenshots in Playwright output only; do not commit binary screenshots.

- [ ] **Step 6: Mark the approved spec implemented**

Change the spec header from:

```markdown
> 状态：待书面复核
```

to:

```markdown
> 状态：已实现并验证
```

Only do this after Steps 2-5 pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/e2e/product-center.e2e.ts docs/superpowers/specs/2026-07-13-product-center-expansion-design.md
git commit -m "test: verify product center workflow"
```

---

## Final Verification Checklist

- [ ] `cd backend && mvn test` reports BUILD SUCCESS.
- [ ] `cd frontend && pnpm test:run` reports all tests passed.
- [ ] `cd frontend && pnpm build` exits 0.
- [ ] `cd frontend && pnpm e2e -- product-center.e2e.ts` passes.
- [ ] `git diff --check` returns no output.
- [ ] `git status --short` contains no unintended staged files.
- [ ] Existing project, standardization, knowledge, resource, file, security, and agent tests remain green.
- [ ] Product Center has loading, empty, error, forbidden, conflict, and archived read-only states.
