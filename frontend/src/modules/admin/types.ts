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

export interface OutlineConfiguration {
  baseUrl: string
  publicBaseUrl: string
  collectionId: string
  collectionName?: string
  apiTokenConfigured: boolean
  source: 'ENVIRONMENT' | 'ORGANIZATION' | 'MIXED'
}

export interface OutlineConfigurationInput {
  baseUrl: string
  publicBaseUrl: string
  collectionId: string
  apiToken?: string
}

export interface OutlineConnectionTest {
  status: 'READY'
  collectionId: string
  collectionName: string
}

export interface DocumentCenterJob {
  id: number
  jobType: 'PROJECT_INIT' | 'PROJECT_MIGRATION' | 'KNOWLEDGE_MIGRATION'
  businessKey: string
  businessId?: number
  status: 'PENDING' | 'RUNNING' | 'RETRY' | 'DONE' | 'FAILED'
  attemptCount: number
  lastError?: string
  startedAt?: string
  completedAt?: string
  updatedAt: string
}

export interface DocumentCenterStatus {
  integrationStatus: 'NOT_CONFIGURED' | 'READY' | 'FAILED'
  collectionId: string
  knowledgeRoot: { linkId?: number; status: string; lastError?: string }
  projectRoot: { linkId?: number; status: string; lastError?: string }
  jobs: {
    pending: number
    running: number
    success: number
    failed: number
  }
  failedJobs: DocumentCenterJob[]
  recentError?: string
}
