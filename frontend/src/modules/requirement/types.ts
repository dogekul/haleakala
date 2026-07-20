export interface Requirement {
  id: number
  organizationId: number
  projectId: number
  productId: number
  projectCode: string
  projectName: string
  code: string
  title: string
  description: string
  source?: string
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  status: 'DRAFT' | 'SUBMITTED' | 'CONFIRMED' | 'MERGED' | 'ABANDONED'
  validationWarning?: string
  suggestedLevel?: 'L0' | 'L1' | 'L2'
  confidence?: number
  suggestionReason?: string
  classificationEvidence?: string[]
  classificationWarnings?: string[]
  constructionContents?: ConstructionContent[]
  productionPlan?: ProductionPlanItem[]
  confirmedLevel?: 'L0' | 'L1' | 'L2'
  overrideReason?: string
  outlineLinkId?: number
  sourceTemplateId?: number
  sourceTemplateRevision?: number
  version: number
}

export interface ConstructionContent {
  moduleName: string
  featureCode: string
  featureName: string
  versionAvailability: string
  currentCapability: string
  gap: string
  changeType: 'CONFIGURATION' | 'ENHANCEMENT' | 'NEW_FEATURE' | 'INTEGRATION' | 'DATA' | 'NON_FUNCTIONAL' | 'OUT_OF_SCOPE'
  constructionContent: string
  acceptanceCriteria: string
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  evidence: string
}

export interface ProductionPlanItem {
  phase: string
  workItem: string
  ownerRole: string
  plannedStart: string
  plannedEnd: string
  deliverable: string
  entryCriteria: string
  exitCriteria: string
  riskAndRollback: string
}

export interface RequirementDocument {
  linkId: number
  title: string
  revision: number
  outlineUrl: string
}

export interface DuplicateCandidate { id: number; title: string; description: string; similarityScore: number }
export interface Funnel { L0: number; L1: number; L2: number }
export interface FeatureCoverageEntry {
  featureId: number
  featureCode: string
  featureName: string
  moduleName: string
  coverageType: 'FULL' | 'PARTIAL'
}
export interface RequirementCoverage { requirementId: number; fullyCovered: boolean; entries: FeatureCoverageEntry[] }
