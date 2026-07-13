import { describe, expect, it, vi } from 'vitest'
import { createComposeProjectName, finishDisposableRun } from './e2e-runtime.mjs'

describe('createComposeProjectName', () => {
  it('combines pid, timestamp, and a high-entropy random suffix', () => {
    expect(createComposeProjectName({
      pid: 123,
      timestamp: 1_783_966_052_351,
      randomSuffix: () => 'a1b2c3d4e5f60718',
    })).toBe('zhilu-delivery-e2e-123-1783966052351-a1b2c3d4e5f60718')
  })
})

describe('finishDisposableRun', () => {
  it('keeps a successful test run successful after successful cleanup', () => {
    const report = vi.fn()
    expect(finishDisposableRun({
      exitCode: 0,
      cleanup: () => ({ status: 0, signal: null }),
      report,
    })).toBe(0)
    expect(report).not.toHaveBeenCalled()
  })

  it('fails a successful test run when cleanup cannot spawn', () => {
    const report = vi.fn()
    expect(finishDisposableRun({
      exitCode: 0,
      cleanup: () => ({ status: null, signal: null, error: new Error('spawn docker ENOENT') }),
      report,
    })).toBe(1)
    expect(report).toHaveBeenCalledWith(expect.stringMatching(/cleanup failed.*spawn docker ENOENT/i))
  })

  it('fails a successful test run when cleanup exits nonzero', () => {
    const report = vi.fn()
    expect(finishDisposableRun({
      exitCode: 0,
      cleanup: () => ({ status: 17, signal: null }),
      report,
    })).toBe(1)
    expect(report).toHaveBeenCalledWith(expect.stringMatching(/cleanup failed.*status 17/i))
  })

  it('preserves the test failure code while also reporting cleanup failure', () => {
    const report = vi.fn()
    expect(finishDisposableRun({
      exitCode: 9,
      cleanup: () => ({ status: null, signal: 'SIGTERM' }),
      report,
    })).toBe(9)
    expect(report).toHaveBeenCalledWith(expect.stringMatching(/cleanup failed.*SIGTERM/i))
  })
})
