export interface KnowledgeItem {
  id: number
  type: 'CASE' | 'CODE' | 'TRAINING' | 'TEMPLATE'
  title: string
  summary: string
  content?: string | null
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
  fileOriginalName?: string
  fileVersion?: number
  fileSizeBytes?: number
  documentStatus?: 'PENDING' | 'CREATING' | 'READY' | 'FAILED'
  documentRevision?: number
  documentError?: string
  outlineUrl?: string
  stageCode?: string
  requirement?: 'REQUIRED' | 'OPTIONAL'
  enabled?: boolean
  publishedRevision?: number
}

export interface UploadedFile {
  id: number
  originalName: string
  sizeBytes: number
  fileVersion: number
}
