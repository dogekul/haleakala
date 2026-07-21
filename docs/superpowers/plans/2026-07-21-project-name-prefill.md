# Project Name Prefill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorder every project creation form to customer, product/version, then project name, and prefill an editable name from those selections.

**Architecture:** Add one pure naming helper in the project module and reuse it from the project workspace, dashboard quick-create, and opportunity handoff forms. Keep selection state in each existing Ant Design form and derive the suggestion in a small effect; backend contracts remain unchanged.

**Tech Stack:** React 18, TypeScript, Ant Design Form, TanStack Query, Vitest, Testing Library.

## Global Constraints

- Default format is `客户名称 - 产品名称 版本名称 实施项目`.
- Do not generate a partial name until customer, product, and version names are all available.
- The generated name remains editable and the submitted value is the input's final value.
- Do not add dependencies or change backend APIs.

---

### Task 1: Shared project-name rule

**Files:**
- Create: `frontend/src/modules/project/projectName.ts`
- Create: `frontend/src/modules/project/projectName.test.ts`

**Interfaces:**
- Produces: `buildProjectName(customerName?: string, productName?: string, versionName?: string): string | undefined`

- [ ] **Step 1: Write the failing test**

```ts
import { buildProjectName } from './projectName'

it('builds a complete project name from customer product and version', () => {
  expect(buildProjectName('华东银行', '企业财务云', 'V5.0'))
    .toBe('华东银行 - 企业财务云 V5.0 实施项目')
})

it('does not build a partial project name', () => {
  expect(buildProjectName('华东银行', '企业财务云', undefined)).toBeUndefined()
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --run src/modules/project/projectName.test.ts`

Expected: FAIL because `./projectName` does not exist.

- [ ] **Step 3: Write minimal implementation**

```ts
export function buildProjectName(customerName?: string, productName?: string, versionName?: string) {
  if (!customerName || !productName || !versionName) return undefined
  return `${customerName} - ${productName} ${versionName} 实施项目`
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --run src/modules/project/projectName.test.ts`

Expected: 2 tests pass.

### Task 2: Project workspace and dashboard forms

**Files:**
- Modify: `frontend/src/modules/project/ProjectWorkspace.tsx`
- Modify: `frontend/src/modules/project/ProjectWorkspace.test.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.test.tsx`

**Interfaces:**
- Consumes: `buildProjectName(...)` from Task 1.
- Produces: customer-first and product/version-second form layouts with an editable third-row suggested name.

- [ ] **Step 1: Extend both component tests before production edits**

After selecting customer, product, and version, assert:

```ts
const name = within(drawer).getByRole('textbox', { name: '项目名称' })
expect(name).toHaveValue('华东银行 - 另一产品 V3 已发布 实施项目')
await user.clear(name)
await user.type(name, '人工项目名称')
```

After submitting, assert `projectBody` contains `name: '人工项目名称'`. Also read form labels in DOM order and assert the first four labels are customer, product, version, project name.

- [ ] **Step 2: Run focused tests to verify RED**

Run: `npm test -- --run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx`

Expected: FAIL because the current name input is first and remains empty.

- [ ] **Step 3: Implement the two form changes**

For each form, watch `customerId`, `productId`, and `productVersionId`, find their display values in the existing query data, and call:

```ts
const suggestedName = buildProjectName(customer?.name, product?.name, version?.versionName)
if (suggestedName) form.setFieldValue('name', suggestedName)
```

Move the existing name `Form.Item` below the product/version `Row`. Keep its required rule and automatic-number hint.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `npm test -- --run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx`

Expected: both test files pass.

### Task 3: Opportunity handoff and regression verification

**Files:**
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/e2e/customer-lifecycle.e2e.ts`
- Modify: `frontend/e2e/product-center.e2e.ts`
- Modify: `frontend/e2e/outline-document-center.e2e.ts`

**Interfaces:**
- Consumes: `buildProjectName(...)` from Task 1.
- Produces: a read-only customer first row and suggested project name for CREATE-mode handoff.

- [ ] **Step 1: Extend the handoff component test before production edits**

Assert the customer input is read-only with `华东银行`, the layout order is customer, product, version, project name, and the name input is prefilled with:

```ts
expect(within(handoff).getByLabelText('项目名称'))
  .toHaveValue('华东银行 - 企业财务云 V5.0 实施项目')
```

Clear it, enter `财务中台实施`, submit, and assert the request preserves that manual name.

- [ ] **Step 2: Run the focused test to verify RED**

Run: `npm test -- --run src/modules/customer-center/OpportunityPages.test.tsx`

Expected: FAIL because customer is not rendered and the name is not prefilled.

- [ ] **Step 3: Implement the handoff layout and suggestion**

Render a disabled `Input` containing `opportunity.customerName`, keep product/version on the second row, and move project name below it. Derive the suggestion from the opportunity customer and the selected product/version query data using `buildProjectName`.

- [ ] **Step 4: Update E2E flows**

Remove manual project-name typing where the test uses the generated default, or assert the default before overriding it when a stable custom name is required. No E2E flow should submit an empty project name.

- [ ] **Step 5: Run full frontend verification**

Run: `npm test -- --run --silent --testTimeout=10000`

Expected: 29 test files and all tests pass.

Run: `npm run build`

Expected: TypeScript and Vite build exit 0.

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "feat: prefill project names from selections"
```
