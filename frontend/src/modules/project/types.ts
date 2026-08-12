export interface Stage {
  id: number
  code: string
  name: string
  order: number
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'BLOCKED'
  gateStatus: string
  gateMessage?: string
}

export interface Project {
  id: number
  organizationId: number
  code: string
  name: string
  customerId: number | null
  customerName: string
  productId: number
  productName: string
  productVersionId: number
  productVersionName: string
  managerUserId: number
  managerName: string
  status: string
  currentStage: string
  riskLevel: 'GREEN' | 'YELLOW' | 'RED'
  gateMode?: 'BLOCK' | 'WARNING'
  documentSpaceStatus?: 'PENDING' | 'INITIALIZING' | 'READY' | 'FAILED'
  documentSpaceError?: string
  startDate?: string
  plannedEndDate?: string
  version: number
  stages: Stage[]
  members: Array<Record<string, unknown>>
  risks: Array<Record<string, unknown>>
  milestones: Array<Record<string, unknown>>
  templates: Array<Record<string, unknown>>
  artifacts: Array<Record<string, unknown>>
  activities: Array<Record<string, unknown>>
}

export interface ProjectDocument {
  id: number
  stageCode: string
  title: string
  requirement: 'REQUIRED' | 'OPTIONAL'
  conditionCode: 'ALWAYS' | 'HAS_CUSTOM_DEV'
  gateRequired: boolean
  status: 'PENDING' | 'TODO' | 'PENDING_CONFIRMATION' | 'COMPLETED' | 'FAILED'
  revision?: number
  confirmedRevision?: number
  confirmedBy?: number
  confirmedByName?: string
  confirmedAt?: string
  outlineUrl?: string
  lastError?: string
  lastSyncedAt?: string
  sourceTemplateId: number
  sourceTemplateRevision: number
}

export interface Product {
  id: number
  code: string
  name: string
  status: string
}

export interface ProductVersion {
  id: number
  productId: number
  versionName: string
  status: string
}

export interface DeliveryTrackingItem {
  id: number
  itemCode: string
  originalRequest: string
  classification: 'CONFIGURATION' | 'INTEGRATION' | 'ENHANCEMENT' | 'NEW_FEATURE'
  deliveryEnd: 'C' | 'B' | 'BACKEND'
  featurePoint: string
  complexity: 'S' | 'M' | 'L' | 'XL'
  productDependency?: string
  dependencyStatus: 'READY' | 'PROCESSING' | 'GAP' | 'NA'
  dependencyNote?: string
  extensionPoint?: string
  estimatedDays: number
  actualDays?: number
  deviationPercent?: number
  reusableLevel: 'SEED' | 'GROWING' | 'MATURE' | 'NA'
  status: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED' | 'CANCELLED'
  ownerUserId?: number
  ownerName?: string
  notes?: string
  version: number
  updatedAt?: string
}

export const stageNames: Record<string, string> = {
  START: '项目立项', REQUIREMENT: '调研与启动', CUSTOM_DEV: '方案与计划', GO_LIVE: '开发与测试',
  TRIAL_HANDOVER: '验证与发布', STANDARDIZATION: '验收与结项', CLOSE: '过程跟进',
}
