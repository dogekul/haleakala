export type OpportunityStage = 'LEAD' | 'OPPORTUNITY' | 'POC' | 'BIDDING' | 'CONTRACT'
export type OpportunityStatus = 'OPEN' | 'WON' | 'LOST'
export type OperationStage = 'MAINTENANCE' | 'OPERATING' | 'REPURCHASE' | 'CLOSED'
export type OperationStatus = 'OPEN' | 'CLOSED'
export type Health = 'GREEN' | 'YELLOW' | 'RED'

export interface Opportunity {
  id: number
  organizationId: number
  customerId: number
  customerName: string
  title: string
  note?: string
  amount: number
  productId?: number
  productName?: string
  productVersionId?: number
  productVersionName?: string
  commercialOwnerUserId?: number
  commercialOwnerName?: string
  solutionOwnerUserId?: number
  solutionOwnerName?: string
  projectManagerUserId?: number
  projectManagerName?: string
  operationOwnerUserId?: number
  operationOwnerName?: string
  stage: OpportunityStage
  status: OpportunityStatus
  projectId?: number
  projectName?: string
  stageEnteredAt: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface OpportunityInput {
  customerId: number
  title: string
  note?: string
  amount: number
  productId?: number
  productVersionId?: number
  commercialOwnerUserId?: number
  solutionOwnerUserId?: number
  projectManagerUserId?: number
  operationOwnerUserId?: number
  version?: number
}

export interface OpportunityActivity {
  id: number
  opportunityId: number
  stageCode: OpportunityStage
  title: string
  status: 'TODO' | 'DONE'
  sortOrder: number
  createdAt: string
  completedAt?: string
  version: number
}

export interface OpportunityArtifact {
  id: number
  opportunityId: number
  stageFrom: OpportunityStage
  artifactType: string
  title: string
  contentMarkdown?: string
  outlineLinkId?: number
  sourceTemplateId?: number
  sourceTemplateRevision?: number
  fileId?: number
  fileName?: string
  decision?: 'PASS' | 'REJECT'
  createdAt: string
}

export interface ImplementationItem {
  opportunityId: number
  opportunityTitle: string
  customerId: number
  customerName: string
  projectId: number
  projectCode: string
  projectName: string
  projectStage: string
  projectStatus: string
  managerUserId: number
  managerName: string
  riskLevel: Health
  openRiskCount: number
  redRiskCount: number
  overdueMilestoneCount: number
  nextMilestoneName?: string
  nextMilestoneDueDate?: string
  plannedEndDate?: string
  updatedAt: string
  health: Health
}

export interface ImplementationCockpit {
  implementationProjects: number
  redRiskProjects: number
  overdueMilestones: number
  closingProjects: number
  items: ImplementationItem[]
}

export interface CustomerOperation {
  id: number
  organizationId: number
  customerId: number
  customerName: string
  title: string
  stage: OperationStage
  status: OperationStatus
  ownerUserId?: number
  ownerName?: string
  projectId?: number
  project?: { id: number; name: string }
  opportunityId?: number
  opportunity?: { id: number; title: string }
  createdAt: string
  updatedAt: string
  version: number
}

export interface OperationInput {
  customerId: number
  title: string
  ownerUserId?: number
  projectId?: number
  opportunityId?: number
  version?: number
}

export interface FullLink {
  customer: { id: number; name: string }
  opportunity: { id: number; title: string; stage: OpportunityStage; status: OpportunityStatus }
  project?: { id: number; name: string; stage: string; status: string }
  operation?: { id: number; title: string; stage: OperationStage; status: OperationStatus }
}

export interface OwnerOption { id: number; displayName: string }

export interface UploadedFile {
  id: number
  originalName: string
  fileVersion: number
  sizeBytes?: number
}
