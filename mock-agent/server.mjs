import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto'
import { createServer, request as httpRequest } from 'node:http'
import { request as httpsRequest } from 'node:https'

const port = Number(process.env.PORT ?? 8090)
const secret = process.env.AGENT_SHARED_SECRET ?? 'change-me'
const jobs = new Map()
const skills = new Set(['deliver-init', 'deliver-require', 'deliver-dev', 'deliver-transition', 'deliver-standardize', 'deliver-close'])

const server = createServer(async (request, response) => {
  try {
    const body = await readBody(request)
    if (request.url !== '/health' && !verify(request.headers, body)) return send(response, 401, { code: 'INVALID_SIGNATURE' })
    if (request.method === 'GET' && request.url === '/health') return send(response, 200, { status: 'UP' })
    if (request.method === 'POST' && request.url === '/v1/jobs') return createJob(body, response)
    const match = request.url?.match(/^\/v1\/jobs\/([^/]+)(\/cancel)?$/)
    if (!match) return send(response, 404, { code: 'NOT_FOUND' })
    const job = jobs.get(match[1])
    if (!job) return send(response, 404, { code: 'JOB_NOT_FOUND' })
    if (request.method === 'GET' && !match[2]) return send(response, 200, publicJob(job))
    if (request.method === 'POST' && match[2]) {
      if (!terminal(job.status)) transition(job, 'CANCELLED', job.progress, '任务由平台取消')
      return send(response, 202, publicJob(job))
    }
    return send(response, 405, { code: 'METHOD_NOT_ALLOWED' })
  } catch (error) {
    send(response, 400, { code: 'BAD_REQUEST', message: error.message })
  }
})

function createJob(raw, response) {
  const input = JSON.parse(raw || '{}')
  if (!skills.has(input.skill)) return send(response, 422, { code: 'UNSUPPORTED_SKILL' })
  const id = randomUUID()
  const job = { id, skill: input.skill, scenario: input.scenario ?? 'normal', callbackUrl: input.callbackUrl, status: 'QUEUED', progress: 0, artifacts: [], error: null }
  jobs.set(id, job)
  setTimeout(() => transition(job, 'RUNNING', 25), 250)
  if (job.scenario === 'normal') {
    setTimeout(() => transition(job, 'RUNNING', 70), 900)
    setTimeout(() => {
      job.artifacts = [{ name: `${job.skill}-result.md`, mimeType: 'text/markdown', artifactType: 'AGENT_OUTPUT', content: artifact(job, input.context) }]
      transition(job, 'SUCCEEDED', 100)
    }, 1600)
  } else if (job.scenario === 'failure') {
    setTimeout(() => transition(job, 'FAILED', 60, 'Mock Agent 按场景返回失败'), 1200)
  }
  send(response, 202, { externalJobId: id, status: 'QUEUED' })
}

function transition(job, status, progress, error = null) {
  if (terminal(job.status)) return
  job.status = status; job.progress = progress; job.error = error
  if (job.callbackUrl) void callback(job)
}

async function callback(job) {
  const body = JSON.stringify({ eventId: randomUUID(), externalJobId: job.id, status: job.status, progress: job.progress, error: job.error, artifacts: job.status === 'SUCCEEDED' ? job.artifacts : [] })
  const url = new URL(job.callbackUrl)
  const timestamp = Math.floor(Date.now() / 1000).toString()
  const signature = sign(`${timestamp}.${body}`)
  const transport = url.protocol === 'https:' ? httpsRequest : httpRequest
  await new Promise(resolve => {
    const outgoing = transport(url, { method: 'POST', headers: { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body), 'x-agent-timestamp': timestamp, 'x-agent-signature': signature } }, response => { response.resume(); response.on('end', resolve) })
    outgoing.on('error', resolve); outgoing.end(body)
  })
}

function verify(headers, body) {
  const timestamp = headers['x-agent-timestamp']
  const provided = headers['x-agent-signature']
  if (typeof timestamp !== 'string' || typeof provided !== 'string') return false
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) return false
  const expected = sign(`${timestamp}.${body}`)
  if (expected.length !== provided.length) return false
  return timingSafeEqual(Buffer.from(expected), Buffer.from(provided.toLowerCase()))
}

function sign(value) { return createHmac('sha256', secret).update(value).digest('hex') }
function publicJob(job) { return { externalJobId: job.id, status: job.status, progress: job.progress, error: job.error, artifacts: job.artifacts } }
function terminal(status) { return ['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'].includes(status) }
function artifact(job, context = {}) { return `# ${job.skill} 执行结果\n\n- 项目：${context.name ?? '未命名项目'}\n- 客户：${context.customer_name ?? '-'}\n- 状态：执行成功\n\n> 此文件由 Mock Agent 按稳定契约生成，可无缝替换为外部团队 Agent。\n` }
function send(response, status, value) { const body = JSON.stringify(value); response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(body) }); response.end(body) }
function readBody(request) { return new Promise((resolve, reject) => { const chunks = []; request.on('data', chunk => chunks.push(chunk)); request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8'))); request.on('error', reject) }) }

server.listen(port, '0.0.0.0', () => console.log(`Mock Agent listening on ${port}`))
