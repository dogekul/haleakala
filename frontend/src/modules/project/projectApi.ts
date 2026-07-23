import { api } from '../../services/api'
import { apiPath } from '../../services/apiPath'
import type { DocumentContent, DocumentFormat, SaveDocumentInput } from '../document/types'
import type { Product, ProductVersion, Project, ProjectDocument } from './types'

export const projectApi = {
  list: () => api<Project[]>('/api/v1/projects'),
  get: (id: number) => api<Project>(`/api/v1/projects/${id}`),
  create: (input: Record<string, unknown>) => api<Project>('/api/v1/projects', {
    method: 'POST', body: JSON.stringify(input),
  }),
  advance: (id: number, targetStage: string) => api<Project>(`/api/v1/projects/${id}/advance`, {
    method: 'POST', body: JSON.stringify({ targetStage }),
  }),
  documents: (id: number) => api<ProjectDocument[]>(`/api/v1/projects/${id}/documents`),
  retryDocuments: (id: number) => api<Project>(`/api/v1/projects/${id}/documents/retry`, {
    method: 'POST',
  }),
  syncDocuments: (id: number) => api<Project>(`/api/v1/projects/${id}/documents/sync`, {
    method: 'POST',
  }),
  close: (id: number) => api<Project>(`/api/v1/projects/${id}/close`, {
    method: 'POST',
  }),
  loadDocument: (projectId: number, documentId: number) =>
    api<DocumentContent>(`/api/v1/projects/${projectId}/documents/${documentId}`),
  saveDocument: (projectId: number, documentId: number, input: SaveDocumentInput) =>
    api<DocumentContent>(`/api/v1/projects/${projectId}/documents/${documentId}`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  confirmDocument: (projectId: number, documentId: number) =>
    api<ProjectDocument>(`/api/v1/projects/${projectId}/documents/${documentId}/confirm`, {
      method: 'POST',
    }),
  exportUrl: (projectId: number, documentId: number, format: DocumentFormat) =>
    apiPath(`/api/v1/projects/${projectId}/documents/${documentId}/export?format=${format}`),
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
  bindableProducts: () => api<Product[]>('/api/v1/products?bindable=true')
    .then(items => items.filter(item => item.status === 'ACTIVE')),
  bindableVersions: (productId: number) => api<ProductVersion[]>(`/api/v1/products/${productId}/versions?bindable=true`)
    .then(items => items.filter(item => item.status === 'RELEASED')),
}
