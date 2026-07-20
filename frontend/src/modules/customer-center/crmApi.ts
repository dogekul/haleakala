import { api } from '../../services/api'
import type { DocumentContent, DocumentFormat, SaveDocumentInput } from '../document/types'
import type {
  CustomerOperation, FullLink, ImplementationCockpit, ImplementationItem, OperationInput,
  Opportunity, OpportunityActivity, OpportunityArtifact, OpportunityInput, OwnerOption,
  UploadedFile,
} from './types'

export interface PreparedResearchReport extends DocumentContent {
  sourceTemplateId: number
  sourceTemplateRevision: number
}
export type OpportunityDocumentType = 'DECISION_MINUTES' | 'CLIENT_REQUESTS' | 'GAP_ANALYSIS' | 'REVIEW_MINUTES'
export interface PreparedOpportunityDocument extends DocumentContent {
  sourceTemplateId: number
  sourceTemplateRevision: number
  generationStatus: 'MANUAL' | 'AI' | 'FAILED'
  generationError?: string
  warnings: string[]
}

function fileBody(file: File) {
  const body = new FormData()
  body.append('file', file)
  return body
}

export const crmApi = {
  opportunities: (query = '') => api<Opportunity[]>(`/api/v1/opportunities${query}`),
  opportunity: (id: number) => api<Opportunity>(`/api/v1/opportunities/${id}`),
  createOpportunity: (input: OpportunityInput) => api<Opportunity>('/api/v1/opportunities', {
    method: 'POST', body: JSON.stringify(input),
  }),
  updateOpportunity: (id: number, input: OpportunityInput) => api<Opportunity>(`/api/v1/opportunities/${id}`, {
    method: 'PUT', body: JSON.stringify(input),
  }),
  advanceOpportunity: (id: number, version: number, decision?: 'PASS' | 'REJECT') =>
    api<Opportunity>(`/api/v1/opportunities/${id}/advance`, {
      method: 'POST', body: JSON.stringify({ version, decision }),
    }),
  prepareResearchReport: (id: number, version: number) =>
    api<PreparedResearchReport>(`/api/v1/opportunities/${id}/research-report/prepare`, {
      method: 'POST', body: JSON.stringify({ version }),
    }),
  researchReport: (id: number) =>
    api<DocumentContent>(`/api/v1/opportunities/${id}/research-report`),
  saveResearchReport: (id: number, input: SaveDocumentInput) =>
    api<DocumentContent>(`/api/v1/opportunities/${id}/research-report`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  submitResearchReport: (id: number, opportunityVersion: number, input: SaveDocumentInput) =>
    api<DocumentContent & { opportunity: Opportunity }>(
      `/api/v1/opportunities/${id}/research-report/submit`, {
        method: 'POST', body: JSON.stringify({ ...input, opportunityVersion }),
      },
    ),
  researchReportExportUrl: (id: number, format: DocumentFormat) =>
    `/api/v1/opportunities/${id}/research-report/export?format=${format}`,
  prepareStageDocument: (id: number, type: OpportunityDocumentType, version: number) =>
    api<PreparedOpportunityDocument>(`/api/v1/opportunities/${id}/documents/${type}/prepare`, {
      method: 'POST', body: JSON.stringify({ version }),
    }),
  saveStageDocument: (id: number, type: OpportunityDocumentType, input: SaveDocumentInput) =>
    api<DocumentContent>(`/api/v1/opportunities/${id}/documents/${type}`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  generateStageDocument: (id: number, type: OpportunityDocumentType, revision: number,
    confirmOverwrite: boolean) => api<PreparedOpportunityDocument>(
      `/api/v1/opportunities/${id}/documents/${type}/generate`, {
        method: 'POST', body: JSON.stringify({ revision, confirmOverwrite }),
      },
    ),
  submitStageDocument: (id: number, type: OpportunityDocumentType,
    opportunityVersion: number, input: SaveDocumentInput) =>
    api<DocumentContent & { opportunity: Opportunity }>(
      `/api/v1/opportunities/${id}/documents/${type}/submit`, {
        method: 'POST', body: JSON.stringify({ ...input, opportunityVersion }),
      },
    ),
  stageDocumentExportUrl: (id: number, type: OpportunityDocumentType, format: DocumentFormat) =>
    `/api/v1/opportunities/${id}/documents/${type}/export?format=${format}`,
  activities: (id: number) => api<OpportunityActivity[]>(`/api/v1/opportunities/${id}/activities`),
  createActivity: (id: number, title: string, sortOrder = 0) =>
    api<OpportunityActivity>(`/api/v1/opportunities/${id}/activities`, {
      method: 'POST', body: JSON.stringify({ title, sortOrder }),
    }),
  updateActivity: (id: number, activityId: number, status: 'TODO' | 'DONE', version: number) =>
    api<OpportunityActivity>(`/api/v1/opportunities/${id}/activities/${activityId}`, {
      method: 'PUT', body: JSON.stringify({ status, version }),
    }),
  artifacts: (id: number) => api<OpportunityArtifact[]>(`/api/v1/opportunities/${id}/artifacts`),
  createArtifact: (id: number, input: Partial<OpportunityArtifact>) =>
    api<OpportunityArtifact>(`/api/v1/opportunities/${id}/artifacts`, {
      method: 'POST', body: JSON.stringify(input),
    }),
  uploadFile: (file: File) => api<UploadedFile>('/api/v1/files', {
    method: 'POST', body: fileBody(file),
  }),
  handoff: (id: number, input: object) => api<Opportunity>(`/api/v1/opportunities/${id}/handoff`, {
    method: 'POST', body: JSON.stringify(input),
  }),
  fullLink: (id: number) => api<FullLink>(`/api/v1/opportunities/${id}/full-link`),
  ownerOptions: () => api<OwnerOption[]>('/api/v1/crm/owner-options'),
  implementation: () => api<ImplementationItem[]>('/api/v1/crm/implementation'),
  cockpit: () => api<ImplementationCockpit>('/api/v1/crm/implementation-cockpit'),
  operations: (query = '') => api<CustomerOperation[]>(`/api/v1/operations${query}`),
  operation: (id: number) => api<CustomerOperation>(`/api/v1/operations/${id}`),
  createOperation: (input: OperationInput) => api<CustomerOperation>('/api/v1/operations', {
    method: 'POST', body: JSON.stringify(input),
  }),
  updateOperation: (id: number, input: OperationInput) => api<CustomerOperation>(`/api/v1/operations/${id}`, {
    method: 'PUT', body: JSON.stringify(input),
  }),
  advanceOperation: (id: number, version: number) => api<CustomerOperation>(`/api/v1/operations/${id}/advance`, {
    method: 'POST', body: JSON.stringify({ version }),
  }),
}
