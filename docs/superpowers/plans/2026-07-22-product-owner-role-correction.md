# Product Owner Role Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make product, module, and feature owner selection accept only active current-organization users with role code `PRODUCT_OWNER`.

**Architecture:** Keep the existing shared `ProductOwnerService` and selector API. Correct its single role predicate, update integration fixtures to model `PRODUCT_OWNER` versus `PRODUCT_MANAGER`, and change the existing empty-state copy.

**Tech Stack:** Java 8, Spring Boot, JdbcTemplate, JUnit 5, React, TypeScript, Ant Design, Vitest, Docker Compose.

## Global Constraints

- Match roles by exact code `PRODUCT_OWNER`, never by display name.
- Do not accept `PRODUCT_MANAGER` as an owner role.
- Do not add a migration or change the product permission model.
- Preserve historical owner display and the existing `ownerUserId` API contract.

---

### Task 1: Correct backend owner eligibility

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductStructureIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductOwnerService.java`

**Interfaces:**
- Consumes: existing `ProductOwnerService.options(long)` and `validate(long, Long)`.
- Produces: unchanged candidate response `{id, displayName}` and unchanged validation error.

- [ ] **Step 1: Write failing integration fixtures and assertions**

In catalog and structure setup, create the custom test role idempotently:

```java
jdbc.update("merge into role(id,code,name,description,built_in,version) key(id) "
    + "values (9,'PRODUCT_OWNER','产品负责人','产品负责人',false,0)");
```

Assign eligible, disabled, and cross-organization fixtures to `PRODUCT_OWNER`. Assign a separate active fixture to `PRODUCT_MANAGER` and assert that it is absent from `/api/v1/products/owner-options` and rejected on save. Update expected eligible names to “产品负责人”. In `StandardizationServiceTest`, assign its converted feature owner to `PRODUCT_OWNER` so the fixture reflects the new rule.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd backend
mvn -q -Dtest=ProductCatalogIT,ProductStructureIT,StandardizationServiceTest test
```

Expected: failures show the candidate endpoint still returns `PRODUCT_MANAGER` users and rejects `PRODUCT_OWNER` users.

- [ ] **Step 3: Implement the minimal shared fix**

Change both SQL predicates in `ProductOwnerService`:

```java
r.code='PRODUCT_OWNER'
```

Do not change controller, DTO, service callers, or permissions.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the same Maven command. Expected: all focused tests pass.

- [ ] **Step 5: Commit backend correction**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog/ProductOwnerService.java \
  backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java \
  backend/src/test/java/com/zhilu/delivery/catalog/ProductStructureIT.java \
  backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java
git commit -m "fix: use product owner role for ownership"
```

### Task 2: Correct selector guidance and deploy locally

**Files:**
- Modify: `frontend/src/modules/product/ProductListPage.test.tsx`
- Modify: `frontend/src/modules/product/ProductListPage.tsx`
- Modify: `frontend/src/modules/product/ProductStructureTab.tsx`

**Interfaces:**
- Consumes: existing `productApi.ownerOptions()`.
- Produces: unchanged selectors with corrected empty-state guidance.

- [ ] **Step 1: Write a failing copy assertion**

Make the owner-options mock return an empty list, open the product editor, and assert:

```tsx
expect(await screen.findByText('暂无产品负责人，请先在系统管理中配置产品负责人角色')).toBeInTheDocument()
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd frontend
pnpm test:run src/modules/product/ProductListPage.test.tsx --maxWorkers=1
```

Expected: the assertion fails because the page still says “配置产品经理角色”.

- [ ] **Step 3: Update all three selector messages**

Use this exact text in product, module, and feature selectors:

```tsx
notFoundContent="暂无产品负责人，请先在系统管理中配置产品负责人角色"
```

- [ ] **Step 4: Verify frontend and commit**

Run the focused test, then:

```bash
git add frontend/src/modules/product/ProductListPage.test.tsx \
  frontend/src/modules/product/ProductListPage.tsx \
  frontend/src/modules/product/ProductStructureTab.tsx
git commit -m "fix: clarify product owner role selection"
```

- [ ] **Step 5: Full verification and local restart**

```bash
cd backend && mvn -q test
cd ../frontend && pnpm test:run --maxWorkers=1 && pnpm build
cd .. && docker compose -p zhilu-delivery-main up -d --build backend frontend
docker compose -p zhilu-delivery-main ps
```

Expected: backend and frontend suites pass, production build succeeds, and both application containers are healthy.

