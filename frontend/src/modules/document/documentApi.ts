import { api } from '../../services/api'
import { apiPath } from '../../services/apiPath'
import type { DocumentContent, DocumentFormat, SaveDocumentInput } from './types'

export const documentApi = {
  load: (path: string) => api<DocumentContent>(path),
  save: (path: string, input: SaveDocumentInput) =>
    api<DocumentContent>(path, { method: 'PUT', body: JSON.stringify(input) }),
  exportUrl: (path: string, format: DocumentFormat) =>
    apiPath(`${path}/export?format=${format}`),
}
