export interface Requirement {
  id: number
  organizationId: number
  projectId: number
  projectCode: string
  projectName: string
  code: string
  title: string
  description: string
  source?: string
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  status: 'DRAFT' | 'SUBMITTED' | 'CONFIRMED' | 'MERGED'
  validationWarning?: string
  suggestedLevel?: 'L0' | 'L1' | 'L2'
  confidence?: number
  suggestionReason?: string
  confirmedLevel?: 'L0' | 'L1' | 'L2'
  overrideReason?: string
  version: number
}

export interface DuplicateCandidate { id: number; title: string; description: string; similarityScore: number }
export interface Funnel { L0: number; L1: number; L2: number }
