# Task 1 Report: Shared project-name rule

## Implementation

Added `buildProjectName(customerName?, productName?, versionName?)` in
`frontend/src/modules/project/projectName.ts`. It returns `undefined` unless
all three inputs are supplied; otherwise it returns the required exact name:
`{customer} - {product} {version} 实施项目`.

## Tests and evidence

### RED

Command:

```sh
cd frontend && npm test -- --run src/modules/project/projectName.test.ts
```

Before implementation, Vitest failed as expected because it could not resolve
`./projectName` from `projectName.test.ts`.

### GREEN

Command:

```sh
cd frontend && npm test -- --run src/modules/project/projectName.test.ts
```

Result: exit code 0; 1 test file passed and 2 tests passed.

Additional verification:

```sh
cd frontend && npm run build
```

Result: exit code 0; TypeScript build and Vite production build succeeded.

## Changed files

- `frontend/src/modules/project/projectName.ts`
- `frontend/src/modules/project/projectName.test.ts`
- `.superpowers/sdd/task-1-report.md`

## Self-review and concerns

- Verified the output literal matches the brief exactly, including spaces and `实施项目`.
- Verified incomplete input returns `undefined` rather than a partial name.
- No concerns; the helper intentionally treats empty strings as incomplete input.
