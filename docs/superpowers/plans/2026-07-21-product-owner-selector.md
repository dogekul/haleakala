# Product Owner Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every product-center owner ID input with a searchable selector limited to active `PRODUCT_MANAGER` users in the current organization, and display owner names throughout the product UI.

**Architecture:** Add a small `ProductOwnerService` as the single backend source for candidate lookup and eligibility validation. Product catalog and structure services delegate validation to it and join `app_user` for historical display names; React Query loads the new option endpoint and shares the cached options across product, module, and feature editors.

**Tech Stack:** Java 1.8, Spring Boot 2.7, JdbcTemplate, MockMvc, React 18, TypeScript 5.7, Ant Design 5, TanStack Query 5, Vitest and Testing Library.

## Global Constraints

- Candidate users must belong to the current organization, have status `ACTIVE`, and hold role code `PRODUCT_MANAGER`.
- Do not add a `PRODUCT_OWNER` role or change existing permissions.
- Keep `ownerUserId` and the existing database schema unchanged.
- Owner selection remains optional.
- Do not add dependencies or build a cross-module generic people-picker.
- Product-center screens must display names rather than raw user IDs.

---

### Task 1: Backend owner policy, endpoint, validation, and names

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/ProductOwnerService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductStructureService.java`
- Test: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Test: `backend/src/test/java/com/zhilu/delivery/catalog/ProductStructureIT.java`

**Interfaces:**
- Produces: `ProductOwnerService.options(long organizationId): List<Map<String,Object>>`.
- Produces: `ProductOwnerService.validate(long organizationId, Long ownerUserId): void`.
- Produces: `GET /api/v1/products/owner-options` returning `{id, displayName}[]`.
- Produces: optional `ownerName` on product, module, and feature response maps.

- [ ] **Step 1: Write failing backend integration tests**

Add tests that create active product managers, inactive product managers, ordinary users, and users in another organization. Assert the option endpoint returns only the current organization's active product manager, valid saves return `ownerName`, and product/module/feature saves reject every ineligible owner with:

```java
.andExpect(status().isBadRequest())
.andExpect(jsonPath("$.message")
    .value("请选择当前组织内启用的产品负责人"));
```

- [ ] **Step 2: Run focused backend tests and verify red state**

Run:

```bash
cd backend && ./mvnw -q -Dtest=ProductCatalogIT,ProductStructureIT test
```

Expected: FAIL because `/api/v1/products/owner-options` does not exist, `ownerName` is absent, and current validation accepts same-organization ordinary users.

- [ ] **Step 3: Add the shared owner policy service**

Create the service with the exact eligibility predicate shared by lookup and validation:

```java
@Service
public class ProductOwnerService {
  private final JdbcTemplate jdbc;

  public ProductOwnerService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> options(long organizationId) {
    return jdbc.query("select distinct u.id,u.display_name from app_user u "
            + "join user_role ur on ur.user_id=u.id join role r on r.id=ur.role_id "
            + "where u.organization_id=? and u.status='ACTIVE' and r.code='PRODUCT_MANAGER' "
            + "order by u.display_name,u.id",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("displayName", row.getString("display_name"));
          return value;
        }, organizationId);
  }

  public void validate(long organizationId, Long ownerUserId) {
    if (ownerUserId == null) return;
    Integer count = jdbc.queryForObject("select count(*) from app_user u "
            + "join user_role ur on ur.user_id=u.id join role r on r.id=ur.role_id "
            + "where u.id=? and u.organization_id=? and u.status='ACTIVE' "
            + "and r.code='PRODUCT_MANAGER'",
        Integer.class, ownerUserId, organizationId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("请选择当前组织内启用的产品负责人");
    }
  }
}
```

- [ ] **Step 4: Wire the endpoint and delegate both write services**

Inject `ProductOwnerService` into both services, replace their local `validateOwner` methods with `owners.validate(...)`, and add:

```java
@GetMapping("/owner-options")
public List<Map<String, Object>> ownerOptions(
    @AuthenticationPrincipal CurrentUser user) {
  return owners.options(user.getOrganizationId());
}
```

- [ ] **Step 5: Add owner display names to response queries**

Join `app_user owner on owner.id=<table>.owner_user_id`, select `owner.display_name owner_name`, and add the optional response field:

```java
value.put("ownerName", row.getString("owner_name"));
```

Apply this consistently to product list/detail/write-lock queries and module/feature list/detail queries.

- [ ] **Step 6: Run focused backend tests and verify green state**

Run:

```bash
cd backend && ./mvnw -q -Dtest=ProductCatalogIT,ProductStructureIT test
```

Expected: PASS with no failures or errors.

- [ ] **Step 7: Commit backend work**

```bash
git add backend/src/main/java/com/zhilu/delivery/catalog backend/src/test/java/com/zhilu/delivery/catalog
git commit -m "feat: restrict product owners to product managers"
```

---

### Task 2: Product list, filter, editor, and detail names

**Files:**
- Modify: `frontend/src/modules/product/types.ts`
- Modify: `frontend/src/modules/product/productApi.ts`
- Modify: `frontend/src/modules/product/ProductListPage.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.tsx`
- Test: `frontend/src/modules/product/ProductListPage.test.tsx`
- Test: `frontend/src/modules/product/ProductDetailPage.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/products/owner-options` and product `ownerName` from Task 1.
- Produces: `ProductOwnerOption { id: number; displayName: string }`.
- Produces: `productApi.ownerOptions(): Promise<ProductOwnerOption[]>`.

- [ ] **Step 1: Write failing product UI tests**

Update fixtures with `ownerName: '张产品'`, route `/api/v1/products/owner-options` to `[{ id: 20, displayName: '张产品' }]`, then assert:

```tsx
expect(within(drawer).queryByLabelText('负责人 ID')).not.toBeInTheDocument()
await user.click(within(drawer).getByRole('combobox', { name: '负责人' }))
await user.click(await screen.findByRole('option', { name: '张产品' }))
expect(screen.getByRole('option', { name: '张产品' })).toBeInTheDocument()
```

Also assert the table, filter, and detail display `张产品` and never display `#20`.

- [ ] **Step 2: Run focused product list/detail tests and verify red state**

Run:

```bash
cd frontend && pnpm test:run src/modules/product/ProductListPage.test.tsx src/modules/product/ProductDetailPage.test.tsx
```

Expected: FAIL because the owner option API and name selector do not exist.

- [ ] **Step 3: Add API and response types**

Add:

```ts
export interface ProductOwnerOption { id: number; displayName: string }
```

Add `ownerName?: string` to `Product`, `ProductModule`, and `ProductFeature`, then expose:

```ts
ownerOptions: () => api<ProductOwnerOption[]>('/api/v1/products/owner-options'),
```

- [ ] **Step 4: Replace product ID UI with name UI**

Load options with query key `['product-owner-options']`. Use searchable Ant Design options:

```tsx
<Form.Item label="负责人" name="ownerUserId">
  <Select allowClear showSearch optionFilterProp="label" virtual={false}
    loading={owners.isLoading}
    notFoundContent="暂无产品负责人，请先在系统管理中配置产品经理角色"
    options={(owners.data ?? []).map(item => ({ value: item.id, label: item.displayName }))} />
</Form.Item>
```

Use `ownerName || '未指定'` for product list and detail, and build filter options by assigned product owners so historical owners remain filterable by name.

- [ ] **Step 5: Run focused product list/detail tests and verify green state**

Run:

```bash
cd frontend && pnpm test:run src/modules/product/ProductListPage.test.tsx src/modules/product/ProductDetailPage.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit product UI work**

```bash
git add frontend/src/modules/product
git commit -m "feat: select product owners by name"
```

---

### Task 3: Module and feature owner selectors

**Files:**
- Modify: `frontend/src/modules/product/ProductStructureTab.tsx`
- Test: `frontend/src/modules/product/ProductDetailPage.test.tsx`

**Interfaces:**
- Consumes: `productApi.ownerOptions()` and `ProductOwnerOption` from Task 2.
- Produces: name-based optional owner selectors in `ModuleEditor` and `FeatureEditor`.

- [ ] **Step 1: Write failing module and feature editor tests**

In the product detail test, open a new module and an existing feature and assert each dialog has a “负责人” combobox, has no “负责人 ID” input, shows `张产品`, and submits `ownerUserId: 20` after selection.

- [ ] **Step 2: Run the focused structure UI test and verify red state**

Run:

```bash
cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx
```

Expected: FAIL because both editors still use `InputNumber`.

- [ ] **Step 3: Share the cached options in the structure tab**

Load the same query key once in `ProductStructureTab` and pass the query result to both editors. Replace both `InputNumber` owner controls with:

```tsx
<Form.Item label="负责人" name="ownerUserId">
  <Select allowClear showSearch optionFilterProp="label" virtual={false}
    loading={ownerOptionsLoading}
    notFoundContent="暂无产品负责人，请先在系统管理中配置产品经理角色"
    options={ownerOptions} />
</Form.Item>
```

Keep the module sort `InputNumber` unchanged.

- [ ] **Step 4: Run the focused structure UI test and verify green state**

Run:

```bash
cd frontend && pnpm test:run src/modules/product/ProductDetailPage.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Run complete backend and frontend verification**

Run:

```bash
cd backend && ./mvnw -q test
cd ../frontend && pnpm test:run && pnpm build
```

Expected: all backend tests and frontend tests pass; TypeScript and Vite production build succeed.

- [ ] **Step 6: Commit final structure UI and test updates**

```bash
git add frontend/src/modules/product
git commit -m "feat: select structure owners by name"
```

