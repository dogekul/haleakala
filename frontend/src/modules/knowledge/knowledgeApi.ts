import { api } from '../../services/api'
import { documentApi } from '../document/documentApi'
import type { DocumentFormat, SaveDocumentInput } from '../document/types'
import type { KnowledgeItem, UploadedFile } from './types'

function fileBody(file: File) {
  const body = new FormData()
  body.append('file', file)
  return body
}

export const knowledgeApi = {
  search: (keyword: string, type: string) => {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (type !== 'ALL') params.set('type', type)
    return api<KnowledgeItem[]>(`/api/v1/knowledge?${params}`)
  },
  get: (id: number) => api<KnowledgeItem>(`/api/v1/knowledge/${id}`),
  save: (id: number | undefined, input: Record<string, unknown>) => api<KnowledgeItem>(`/api/v1/knowledge${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
  publish: (id: number) => api<KnowledgeItem>(`/api/v1/knowledge/${id}/publish`, { method: 'POST' }),
  retryDocument: (id: number) => api<KnowledgeItem>(
    `/api/v1/knowledge/${id}/document/retry`, { method: 'POST' },
  ),
  loadDocument: (id: number) => documentApi.load(`/api/v1/knowledge/${id}/document`),
  saveDocument: (id: number, input: SaveDocumentInput) =>
    documentApi.save(`/api/v1/knowledge/${id}/document`, input),
  exportUrl: (id: number, format: DocumentFormat) =>
    documentApi.exportUrl(`/api/v1/knowledge/${id}/document`, format),
  upload: (file: File) => api<UploadedFile>('/api/v1/files', { method: 'POST', body: fileBody(file) }),
  addFileVersion: (id: number, file: File) => api<UploadedFile>(`/api/v1/files/${id}/versions`, { method: 'POST', body: fileBody(file) }),
}
