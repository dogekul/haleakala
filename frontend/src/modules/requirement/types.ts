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
  confirmedLevel?: 'L0' | 'L1' | 'L2'
  overrideReason?: string
  outlineLinkId?: number
  sourceTemplateId?: number
  sourceTemplateRevision?: number
  version: number
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
