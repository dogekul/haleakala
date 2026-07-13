import { describe, expect, it, vi } from 'vitest'
import { ensureChromium } from './playwright-browser.mjs'

describe('ensureChromium', () => {
  it('uses an existing Chromium without installing again', () => {
    const install = vi.fn()

    expect(ensureChromium({
      executablePath: () => '/cache/chromium',
      existsSync: () => true,
      install,
    })).toBe('/cache/chromium')
    expect(install).not.toHaveBeenCalled()
  })

  it('installs the pinned Chromium when the executable is missing', () => {
    const install = vi.fn(() => ({ status: 0 }))
    const existsSync = vi.fn()
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)

    expect(ensureChromium({
      executablePath: () => '/cache/chromium',
      existsSync,
      install,
    })).toBe('/cache/chromium')
    expect(install).toHaveBeenCalledOnce()
  })

  it('reports a clear error when Chromium installation fails', () => {
    expect(() => ensureChromium({
      executablePath: () => '/missing/chromium',
      existsSync: () => false,
      install: () => ({ status: 1 }),
    })).toThrow(/Chromium.*install.*failed/i)
  })
})
