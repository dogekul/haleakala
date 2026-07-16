import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'
import { createComposeProjectName, finishDisposableRun } from './e2e-runtime.mjs'
import { ensureChromium } from './playwright-browser.mjs'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.resolve(frontend, '..')
const testArgs = process.argv.slice(2).filter((argument, index) =>
  !(index === 0 && argument === '--'))

try {
  ensureChromium({
    executablePath: () => chromium.executablePath(),
    existsSync,
    install: () => spawnSync('pnpm', ['exec', 'playwright', 'install', 'chromium'], {
      cwd: frontend,
      env: process.env,
      stdio: 'inherit',
    }),
  })
} catch (error) {
  console.error(error instanceof Error ? error.message : error)
  process.exit(1)
}

if (process.env.E2E_BASE_URL) {
  const test = spawnSync('pnpm', ['exec', 'playwright', 'test', ...testArgs], {
    cwd: frontend,
    env: process.env,
    stdio: 'inherit',
  })
  process.exit(test.status ?? 1)
}

const project = createComposeProjectName()
const composeEnv = {
  ...process.env,
  WEB_PORT: '0',
  BACKEND_PORT: '0',
  MYSQL_PORT: '0',
  REDIS_PORT: '0',
  MINIO_PORT: '0',
  MINIO_CONSOLE_PORT: '0',
  AGENT_PORT: '0',
  OUTLINE_MOCK_PORT: '0',
  OUTLINE_BASE_URL: 'http://mock-outline:3000',
  OUTLINE_API_TOKEN: 'ol_api_e2e',
  OUTLINE_COLLECTION_ID: 'a4296a54-2044-4529-ba86-d598a5322e06',
  MOCK_OUTLINE_TOKEN: 'ol_api_e2e',
  DELIVERY_OUTLINE_JOB_INITIAL_DELAY_MS: '1000',
  DELIVERY_OUTLINE_JOB_SCAN_MS: '500',
  OUTLINE_INITIAL_BACKOFF: '0s',
  OUTLINE_MAX_ATTEMPTS: '1',
}

function compose(args, options = {}) {
  return spawnSync('docker', ['compose', '-p', project, ...args], {
    cwd: root,
    env: composeEnv,
    stdio: 'inherit',
    ...options,
  })
}

function waitForHttp(url, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  return new Promise((resolve, reject) => {
    const probe = () => {
      const request = http.get(url, response => {
        response.resume()
        if (response.statusCode && response.statusCode < 500) return resolve()
        retry()
      })
      request.on('error', retry)
      request.setTimeout(1_000, () => request.destroy())
    }
    const retry = () => {
      if (Date.now() >= deadline) return reject(new Error(`Timed out waiting for ${url}`))
      setTimeout(probe, 500)
    }
    probe()
  })
}

let exitCode = 1
try {
  const started = compose(['up', '-d', '--build'])
  if (started.status !== 0) throw new Error('Failed to start disposable E2E stack')

  const published = compose(['port', 'frontend', '80'], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'inherit'],
  })
  if (published.status !== 0) throw new Error('Failed to resolve disposable frontend port')
  const match = published.stdout.trim().split(/\r?\n/).find(Boolean)?.match(/:(\d+)$/)
  if (!match) throw new Error(`Unexpected frontend port output: ${published.stdout}`)
  const baseURL = `http://127.0.0.1:${match[1]}`
  await waitForHttp(baseURL)

  const outlinePublished = compose(['port', 'mock-outline', '3000'], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'inherit'],
  })
  if (outlinePublished.status !== 0) throw new Error('Failed to resolve mock Outline port')
  const outlineMatch = outlinePublished.stdout.trim().split(/\r?\n/)
    .find(Boolean)?.match(/:(\d+)$/)
  if (!outlineMatch) {
    throw new Error(`Unexpected mock Outline port output: ${outlinePublished.stdout}`)
  }
  const outlineURL = `http://127.0.0.1:${outlineMatch[1]}`
  await waitForHttp(`${outlineURL}/health`)

  const test = spawnSync('pnpm', ['exec', 'playwright', 'test', ...testArgs], {
    cwd: frontend,
    env: {
      ...process.env,
      E2E_BASE_URL: baseURL,
      E2E_OUTLINE_URL: outlineURL,
    },
    stdio: 'inherit',
  })
  exitCode = test.status ?? 1
} catch (error) {
  console.error(error instanceof Error ? error.message : error)
} finally {
  exitCode = finishDisposableRun({
    exitCode,
    cleanup: compose,
  })
}

process.exit(exitCode)
