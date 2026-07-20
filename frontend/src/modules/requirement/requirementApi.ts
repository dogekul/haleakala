import { api } from '../../services/api'
import type { DuplicateCandidate, Funnel, Requirement, RequirementCoverage, RequirementDocument } from './types'

export const requirementApi = {
  list: () => api<Requirement[]>('/api/v1/requirements'),
  funnel: () => api<Funnel>('/api/v1/requirements/funnel'),
  create: (input: Record<string, unknown>) => api<Requirement>('/api/v1/requirements', { method: 'POST', body: JSON.stringify(input) }),
  document: (id: number) => api<RequirementDocument>(`/api/v1/requirements/${id}/document`),
  update: (id: number, input: Record<string, unknown>) => api<Requirement>(`/api/v1/requirements/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  abandon: (id: number, version: number) => api<Requirement>(`/api/v1/requirements/${id}/abandon`, {
    method: 'POST', body: JSON.stringify({ version }),
  }),
  classify: (id: number) => api<Requirement>(`/api/v1/requirements/${id}/classify`, { method: 'POST' }),
  confirm: (id: number, level: string, overrideReason?: string) => api<Requirement>(`/api/v1/requirements/${id}/confirm`, { method: 'POST', body: JSON.stringify({ level, overrideReason }) }),
  duplicates: (id: number) => api<DuplicateCandidate[]>(`/api/v1/requirements/${id}/duplicates`, { method: 'POST' }),
  merge: (source: number, target: number) => api<Requirement>(`/api/v1/requirements/${source}/merge/${target}`, { method: 'POST' }),
  coverage: (id: number) => api<RequirementCoverage>(`/api/v1/requirements/${id}/product-features`),
  replaceCoverage: (id: number, entries: Array<{ featureId: number; coverageType: 'FULL' | 'PARTIAL' }>) =>
    api<RequirementCoverage>(`/api/v1/requirements/${id}/product-features`, {
      method: 'PUT', body: JSON.stringify({ entries: entries.map(({ featureId, coverageType }) => ({ featureId, coverageType })) }),
    }),
  createStandardizationCandidate: (id: number) => api<Record<string, unknown>>('/api/v1/standardization/debts/from-requirement', {
    method: 'POST', body: JSON.stringify({ requirementId: id }),
  }),
}
