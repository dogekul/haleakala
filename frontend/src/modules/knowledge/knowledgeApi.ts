import { api } from '../../services/api'
import type { KnowledgeItem } from './types'

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
}
