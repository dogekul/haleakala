import { api } from '../../services/api'
import type { DuplicateCandidate, Funnel, Requirement } from './types'

export const requirementApi = {
  list: () => api<Requirement[]>('/api/v1/requirements'),
  funnel: () => api<Funnel>('/api/v1/requirements/funnel'),
  create: (input: Record<string, unknown>) => api<Requirement>('/api/v1/requirements', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: number, input: Record<string, unknown>) => api<Requirement>(`/api/v1/requirements/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  classify: (id: number) => api<Requirement>(`/api/v1/requirements/${id}/classify`, { method: 'POST' }),
  confirm: (id: number, level: string, overrideReason?: string) => api<Requirement>(`/api/v1/requirements/${id}/confirm`, { method: 'POST', body: JSON.stringify({ level, overrideReason }) }),
  duplicates: (id: number) => api<DuplicateCandidate[]>(`/api/v1/requirements/${id}/duplicates`, { method: 'POST' }),
  merge: (source: number, target: number) => api<Requirement>(`/api/v1/requirements/${source}/merge/${target}`, { method: 'POST' }),
}
