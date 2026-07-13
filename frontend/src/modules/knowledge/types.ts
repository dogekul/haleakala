export interface KnowledgeItem {
  id: number
  type: 'CASE' | 'CODE' | 'TRAINING'
  title: string
  summary: string
  content: string
  tags?: string
  productId?: number
  productVersionId?: number
  productName?: string
  versionName?: string
  visibility: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  ownerUserId: number
  ownerName: string
  updatedAt: string
  version: number
  language?: string
  codeText?: string
  usageNotes?: string
  audience?: string
  durationMinutes?: number
  fileObjectId?: number
}
