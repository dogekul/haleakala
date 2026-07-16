import { api } from '../../services/api'
import type {
  CustomerOperation, FullLink, ImplementationCockpit, ImplementationItem, OperationInput,
  Opportunity, OpportunityActivity, OpportunityArtifact, OpportunityInput, OwnerOption,
} from './types'

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
