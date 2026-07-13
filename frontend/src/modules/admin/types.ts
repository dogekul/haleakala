export interface AdminUser {
  id: number
  organizationId: number
  primaryTeamId: number | null
  primaryTeamName: string | null
  username: string
  displayName: string
  email: string | null
  status: 'ACTIVE' | 'DISABLED'
  roles: string[]
}

export interface Team {
  id: number
  organizationId: number
  parentId: number | null
  name: string
  code: string
  enabled: boolean
}

export interface Role {
  id: number
  code: string
  name: string
  description: string
  builtIn: boolean
  permissions: string[]
}

export interface Permission {
  code: string
  name: string
  module: string
}

export interface AuditLog {
  id: number
  actorUserId: number | null
  actorName: string | null
  action: string
  resourceType: string
  resourceId: string | null
  traceId: string
  details: string | null
  createdAt: string
}

export interface AuditResult {
  items: AuditLog[]
  page: number
  pageSize: number
  total: number
}

export interface SystemSettings {
  platformName: string
  environmentLabel: string
  timezone: string
  supportEmail: string
  agentTimeoutMinutes: number
}
