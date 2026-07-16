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
  customerId: number
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

export const stageNames: Record<string, string> = {
  START: '启动', REQUIREMENT: '需求采集', CUSTOM_DEV: '二开实施', GO_LIVE: '上线切换',
  TRIAL_HANDOVER: '试运行与移交', STANDARDIZATION: '标准化评估', CLOSE: '项目收尾',
}
