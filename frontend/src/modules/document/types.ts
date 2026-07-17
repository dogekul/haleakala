export type DocumentFormat = 'md' | 'html' | 'pdf' | 'docx'

export interface DocumentContent {
  linkId: number
  title: string
  markdown: string
  renderedHtml: string
  revision: number
  updatedAt?: string
  syncStatus: 'PENDING' | 'CREATING' | 'READY' | 'FAILED'
  lastError?: string
  outlineUrl?: string
  sourceTemplateId?: number
  sourceTemplateRevision?: number
  generationStatus?: 'MANUAL' | 'AI' | 'FAILED'
  generationError?: string
  warnings?: string[]
}

export interface SaveDocumentInput {
  title: string
  markdown: string
  revision: number
}
