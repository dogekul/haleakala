import { api } from '../../services/api'
import type { Assessment, Baseline, CostSummary, Deviation, FlywheelMetric, StandardizationDebt } from './types'

export const standardizationApi = {
  baselines: (versionId: number) => api<Baseline[]>(`/api/v1/standardization/baselines?productVersionId=${versionId}`),
  saveBaseline: (id: number | undefined, input: Record<string, unknown>) => api<Baseline>(`/api/v1/standardization/baselines${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
  assess: (versionId: number) => api<Assessment>(`/api/v1/standardization/assessments?productVersionId=${versionId}`, { method: 'POST' }),
  deviations: (versionId: number) => api<Deviation[]>(`/api/v1/standardization/deviations?productVersionId=${versionId}`),
  debts: (versionId: number) => api<StandardizationDebt[]>(`/api/v1/standardization/debts?productVersionId=${versionId}`),
  evaluateDebts: (versionId: number) => api<StandardizationDebt[]>(`/api/v1/standardization/debts/evaluate?productVersionId=${versionId}`, { method: 'POST' }),
  transitionDebt: (id: number, targetStatus: string, verificationNote?: string) => api<StandardizationDebt>(`/api/v1/standardization/debts/${id}/transition`, { method: 'PUT', body: JSON.stringify({ targetStatus, verificationNote }) }),
  convertToFeature: (id: number, input: Record<string, unknown>) => api<StandardizationDebt>(`/api/v1/standardization/debts/${id}/convert-to-feature`, {
    method: 'POST', body: JSON.stringify(input),
  }),
  costs: (versionId: number) => api<CostSummary>(`/api/v1/standardization/costs?productVersionId=${versionId}`),
  flywheel: (versionId: number) => api<FlywheelMetric>(`/api/v1/standardization/flywheel?productVersionId=${versionId}`, { method: 'POST' }),
}
