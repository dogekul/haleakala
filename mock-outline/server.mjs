import http from 'node:http'
import { randomUUID } from 'node:crypto'

const port = Number(process.env.PORT ?? 3000)
const token = process.env.OUTLINE_API_TOKEN ?? 'ol_api_e2e'
const documents = new Map()
let available = true

function send(response, status, value) {
  const body = JSON.stringify(value)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
  })
  response.end(body)
}

function read(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', chunk => chunks.push(chunk))
    request.on('end', () => {
      if (!chunks.length) return resolve({})
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch (error) {
        reject(error)
      }
    })
    request.on('error', reject)
  })
}

function view(document) {
  return {
    id: document.id,
    collectionId: document.collectionId,
    parentDocumentId: document.parentDocumentId ?? null,
    title: document.title,
    text: document.text,
    url: `/doc/${document.urlId}`,
    urlId: document.urlId,
    revision: document.revision,
    updatedAt: document.updatedAt,
  }
}

function findDocument(identifier) {
  return documents.get(identifier)
    ?? [...documents.values()].find(document => document.urlId === identifier)
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://mock-outline')
  if (request.method === 'GET' && url.pathname === '/health') {
    return send(response, 200, { status: 'UP', available })
  }
  if (request.method === 'POST' && url.pathname === '/__test__/availability') {
    const body = await read(request).catch(() => ({}))
    available = body.available !== false
    return send(response, 200, { available })
  }
  const external = url.pathname.match(/^\/__test__\/documents\/([^/]+)\/external-update$/)
  if (request.method === 'POST' && external) {
    const document = findDocument(decodeURIComponent(external[1]))
    if (!document) return send(response, 404, { error: 'document_not_found' })
    const body = await read(request).catch(() => ({}))
    if (typeof body.title === 'string') document.title = body.title
    document.text = typeof body.text === 'string'
      ? body.text
      : `${document.text}\n\n外部补充 ${document.revision + 1}`
    document.revision += 1
    document.updatedAt = new Date().toISOString()
    return send(response, 200, { data: view(document) })
  }
  if (!url.pathname.startsWith('/api/') || request.method !== 'POST') {
    return send(response, 404, { error: 'not_found' })
  }
  if (request.headers.authorization !== `Bearer ${token}`) {
    return send(response, 401, { error: 'authentication_required' })
  }
  if (!available) return send(response, 503, { error: 'outline_unavailable' })

  const body = await read(request).catch(() => null)
  if (!body) return send(response, 400, { error: 'invalid_json' })

  if (url.pathname === '/api/documents.create') {
    if (!body.title || !body.collectionId) {
      return send(response, 400, { error: 'title_and_collection_required' })
    }
    const id = randomUUID()
    const document = {
      id,
      urlId: `mock-${id}`,
      collectionId: body.collectionId,
      parentDocumentId: body.parentDocumentId ?? null,
      title: String(body.title),
      text: String(body.text ?? ''),
      revision: 1,
      updatedAt: new Date().toISOString(),
    }
    documents.set(id, document)
    return send(response, 200, { data: view(document) })
  }
  if (url.pathname === '/api/documents.info') {
    const document = findDocument(String(body.id ?? ''))
    return document
      ? send(response, 200, { data: view(document) })
      : send(response, 404, { error: 'document_not_found' })
  }
  if (url.pathname === '/api/documents.update') {
    const document = findDocument(String(body.id ?? ''))
    if (!document) return send(response, 404, { error: 'document_not_found' })
    document.title = String(body.title ?? document.title)
    document.text = String(body.text ?? '')
    document.revision += 1
    document.updatedAt = new Date().toISOString()
    return send(response, 200, { data: view(document) })
  }
  if (url.pathname === '/api/documents.list') {
    const parent = body.parentDocumentId ?? null
    const data = [...documents.values()]
      .filter(document => (document.parentDocumentId ?? null) === parent)
      .map(view)
    return send(response, 200, { data })
  }
  if (url.pathname === '/api/documents.export') {
    const document = findDocument(String(body.id ?? ''))
    return document
      ? send(response, 200, { data: document.text })
      : send(response, 404, { error: 'document_not_found' })
  }
  return send(response, 404, { error: 'method_not_found' })
})

server.listen(port, '0.0.0.0', () => {
  console.log(`mock Outline listening on ${port}`)
})
