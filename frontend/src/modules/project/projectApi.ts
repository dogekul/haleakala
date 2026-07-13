import { api } from '../../services/api'
import type { Product, ProductVersion, Project } from './types'

export const projectApi = {
  list: () => api<Project[]>('/api/v1/projects'),
  get: (id: number) => api<Project>(`/api/v1/projects/${id}`),
  create: (input: Record<string, unknown>) => api<Project>('/api/v1/projects', {
    method: 'POST', body: JSON.stringify(input),
  }),
  advance: (id: number, targetStage: string, mode: string) => api<Project>(`/api/v1/projects/${id}/advance`, {
    method: 'POST', body: JSON.stringify({ targetStage, mode }),
  }),
  addRisk: (id: number, input: Record<string, unknown>) => api<Record<string, unknown>>(`/api/v1/projects/${id}/risks`, {
    method: 'POST', body: JSON.stringify(input),
  }),
  updateRisk: (projectId: number, riskId: number, input: Record<string, unknown>) =>
    api<Record<string, unknown>>(`/api/v1/projects/${projectId}/risks/${riskId}`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  addMilestone: (id: number, input: Record<string, unknown>) => api<Record<string, unknown>>(`/api/v1/projects/${id}/milestones`, {
    method: 'POST', body: JSON.stringify(input),
  }),
  saveTemplate: (projectId: number, templateId: number | null, input: Record<string, unknown>) =>
    api<Record<string, unknown>>(`/api/v1/projects/${projectId}/templates${templateId ? `/${templateId}` : ''}`, {
      method: templateId ? 'PUT' : 'POST', body: JSON.stringify(input),
    }),
  settings: (id: number, input: Record<string, unknown>) => api<Project>(`/api/v1/projects/${id}/settings`, {
    method: 'PUT', body: JSON.stringify(input),
  }),
  products: () => api<Product[]>('/api/v1/products'),
  versions: (productId: number) => api<ProductVersion[]>(`/api/v1/products/${productId}/versions`),
}
