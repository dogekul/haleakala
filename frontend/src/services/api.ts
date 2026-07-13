export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly traceId?: string,
  ) {
    super(message)
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  const csrf = typeof document === 'undefined' ? undefined : document.cookie
    .split('; ')
    .find(item => item.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length)
  if (csrf && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf))
  }
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new ApiError(response.status, body.code ?? 'REQUEST_FAILED', body.message ?? '请求失败', body.traceId)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
