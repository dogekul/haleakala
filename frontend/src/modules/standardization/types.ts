export interface Baseline {
  id: number
  productVersionId: number
  capabilityCode: string
  capabilityName: string
  dimension: 'FUNCTION' | 'CONFIGURATION' | 'EXTENSION'
  scopeDescription: string
  configurationOptions?: string
  extensionPoints?: string
  ownerName?: string
  status: string
  version: number
}

export interface Assessment {
  period: string
  standardCoverage: number
  reuseRate: number
  documentationScore: number
  extensionReadiness: number
  deliveryStability: number
  maturityScore: number
}

export interface Deviation {
  projectId: number
  projectCode: string
  projectName: string
  total: number
  l0: number
  l1: number
  l2: number
  deviationRate: number
}

export interface StandardizationDebt {
  id: number
  patternKey: string
  title: string
  occurrenceCount: number
  distinctProjects: number
  status: 'CANDIDATE' | 'PENDING' | 'INCLUDED' | 'VERIFYING' | 'CLOSED'
  targetVersion?: string
  verificationNote?: string
}

export interface CostSummary {
  estimatedPersonDays: number
  actualPersonDays: number
  estimatedCost: number
  actualCost: number
  byExtensionPoint: Array<{ extension_point: string; task_count: number; person_days: number; amount: number }>
}

export interface CustomDevTask {
  id: number
  requirementId: number
  requirementCode: string
  requirementTitle?: string
  projectId: number
  projectCode?: string
  projectName: string
  productVersionId: number
  title: string
  status: 'BACKLOG' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE' | 'CANCELLED'
  technicalOwnerId?: number
  technicalOwnerName?: string
  estimatedPersonDays?: number
  actualPersonDays?: number
  estimatedCost?: number
  actualCost?: number
  extensionPoint?: string
  version: number
}

export interface FlywheelMetric {
  period: string
  confirmedRequirements: number
  l0Count: number
  l1Count: number
  reuseRate: number
  debtClosedCount: number
  customCost: number
  standardCoverage: number
}
