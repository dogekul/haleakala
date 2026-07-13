export function ensureChromium({ executablePath, existsSync, install }) {
  let path = executablePath()
  if (existsSync(path)) return path

  const result = install()
  if (result?.status !== 0) {
    throw new Error('Chromium is missing and the pinned Playwright Chromium install failed')
  }

  path = executablePath()
  if (!existsSync(path)) {
    throw new Error(`Chromium install completed but the executable is still missing: ${path}`)
  }
  return path
}
