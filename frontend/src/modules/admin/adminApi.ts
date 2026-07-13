import { api } from '../../services/api'
import type {
  AdminUser, AuditResult, Permission, Product, ProductVersion, Role, SystemSettings, Team,
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
  products: () => api<Product[]>('/api/v1/products'),
  saveProduct: (id: number | undefined, input: Record<string, unknown>) => api<Product>(
    `/api/v1/products${id ? `/${id}` : ''}`,
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  ),
  versions: (productId: number) => api<ProductVersion[]>(`/api/v1/products/${productId}/versions`),
  saveVersion: (productId: number, id: number | undefined, input: Record<string, unknown>) => api<ProductVersion>(
    `/api/v1/products/${productId}/versions${id ? `/${id}` : ''}`,
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  ),
  audits: (query: Record<string, string | number | undefined>) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value))
    })
    return api<AuditResult>(`/api/v1/admin/audit-logs?${params}`)
  },
  settings: () => api<SystemSettings>('/api/v1/admin/settings'),
  saveSettings: (input: SystemSettings) => api<SystemSettings>('/api/v1/admin/settings', {
    method: 'PUT', body: JSON.stringify(input),
  }),
}
