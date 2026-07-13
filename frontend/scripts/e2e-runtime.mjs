import { randomBytes } from 'node:crypto'

export function createComposeProjectName({
  pid = process.pid,
  timestamp = Date.now(),
  randomSuffix = () => randomBytes(8).toString('hex'),
} = {}) {
  return `zhilu-delivery-e2e-${pid}-${timestamp}-${randomSuffix()}`
}

export function finishDisposableRun({ exitCode, cleanup, report = console.error }) {
  let result
  try {
    result = cleanup()
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    report(`Disposable E2E cleanup failed: docker compose down -v threw: ${detail}`)
    return exitCode === 0 ? 1 : exitCode
  }

  const failure = cleanupFailure(result)
  if (!failure) return exitCode
  report(`Disposable E2E cleanup failed: ${failure}`)
  return exitCode === 0 ? 1 : exitCode
}

function cleanupFailure(result) {
  if (result?.error) {
    const detail = result.error instanceof Error ? result.error.message : String(result.error)
    return `docker compose down -v spawn error: ${detail}`
  }
  if (result?.signal) return `docker compose down -v terminated by signal ${result.signal}`
  if (result?.status !== 0) {
    const status = result?.status == null ? 'unavailable' : result.status
    return `docker compose down -v exited with status ${status}`
  }
  return undefined
}
