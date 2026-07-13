import { api } from '../../services/api'
import type { DashboardProject, DashboardSummary, MatrixRow, RiskHeatmapRow } from './types'

export interface DashboardFilters { keyword?: string; riskLevel?: string; status?: string; productId?: number }

function query(filters: DashboardFilters) {
  const values = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') values.set(key, String(value)) })
  const result = values.toString()
  return result ? `?${result}` : ''
}

export const dashboardApi = {
  summary: (filters: DashboardFilters) => api<DashboardSummary>(`/api/v1/dashboard/summary${query(filters)}`),
  projects: (filters: DashboardFilters) => api<DashboardProject[]>(`/api/v1/dashboard/projects${query(filters)}`),
  risks: () => api<RiskHeatmapRow[]>('/api/v1/dashboard/risk-heatmap'),
  matrix: () => api<MatrixRow[]>('/api/v1/dashboard/matrix'),
}
