import { api } from '../../services/api'
import type {
  AdminUser, AuditResult, DocumentCenterJob, DocumentCenterStatus, Permission, Role,
  OutlineConfiguration, OutlineConfigurationInput, OutlineConnectionTest, SystemSettings, Team,
} from './types'

export const adminApi = {
  users: () => api<AdminUser[]>('/api/v1/admin/users'),
  saveUser: (id: number | undefined, input: Record<string, unknown>) => api<AdminUser>(
    `/api/v1/admin/users${id ? `/${id}` : ''}`,
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  ),
  userStatus: (id: number, status: AdminUser['status']) => api<void>(`/api/v1/admin/users/${id}/status`, {
    method: 'PUT', body: JSON.stringify({ status }),
  }),
  teams: () => api<Team[]>('/api/v1/admin/teams'),
  saveTeam: (id: number | undefined, input: Record<string, unknown>) => api<Team>(
    `/api/v1/admin/teams${id ? `/${id}` : ''}`,
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  ),
  roles: () => api<Role[]>('/api/v1/admin/roles'),
  permissions: () => api<Permission[]>('/api/v1/admin/permissions'),
  saveRolePermissions: (id: number, permissionCodes: string[]) => api<Role>(
    `/api/v1/admin/roles/${id}/permissions`,
    { method: 'PUT', body: JSON.stringify({ permissionCodes }) },
  ),
  audits: (query: Record<string, string | number | undefined>) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value))
    })
    return api<AuditResult>(`/api/v1/admin/audit-logs?${params}`)
  },
  auditFacets: () => api<{ actions: string[]; resourceTypes: string[] }>('/api/v1/admin/audit-log-facets'),
  settings: () => api<SystemSettings>('/api/v1/admin/settings'),
  runtimeSettings: () => api<Pick<SystemSettings, 'platformName' | 'environmentLabel' | 'timezone'>>('/api/v1/runtime-settings'),
  saveSettings: (input: SystemSettings) => api<SystemSettings>('/api/v1/admin/settings', {
    method: 'PUT', body: JSON.stringify(input),
  }),
  documentCenterStatus: () => api<DocumentCenterStatus>(
    '/api/v1/admin/document-center/status',
  ),
  outlineConfiguration: () => api<OutlineConfiguration>(
    '/api/v1/admin/document-center/config',
  ),
  testOutlineConfiguration: (input: OutlineConfigurationInput) =>
    api<OutlineConnectionTest>(
      '/api/v1/admin/document-center/config/test',
      { method: 'POST', body: JSON.stringify(input) },
    ),
  saveOutlineConfiguration: (input: OutlineConfigurationInput) =>
    api<OutlineConfiguration>(
      '/api/v1/admin/document-center/config',
      { method: 'PUT', body: JSON.stringify(input) },
    ),
  documentCenterJobs: (status?: DocumentCenterJob['status']) => api<DocumentCenterJob[]>(
    `/api/v1/admin/document-center/jobs${status ? `?status=${status}` : ''}`,
  ),
  initializeDocumentCenter: () => api<Record<string, unknown>>(
    '/api/v1/admin/document-center/initialize', { method: 'POST' },
  ),
  initializeProductDocuments: () => api<{ completed: number; failed: number }>(
    '/api/v1/admin/document-center/initialize-products', { method: 'POST' },
  ),
  migrateKnowledgeDocuments: () => api<{ enqueued: number }>(
    '/api/v1/admin/document-center/migrate-knowledge', { method: 'POST' },
  ),
  migrateProjectDocuments: () => api<{ enqueued: number }>(
    '/api/v1/admin/document-center/migrate-projects', { method: 'POST' },
  ),
  retryDocumentJob: (id: number) => api<{ id: number; status: string }>(
    `/api/v1/admin/document-center/jobs/${id}/retry`, { method: 'POST' },
  ),
}
