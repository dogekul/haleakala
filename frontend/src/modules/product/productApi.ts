import { api } from '../../services/api'
import type {
  Product, ProductCoverage, ProductDocumentNode, ProductFeature,
  ProductModule, ProductVersion, VersionManifest,
} from './types'
import type { DocumentContent, DocumentFormat, SaveDocumentInput } from '../document/types'
import { apiPath } from '../../services/apiPath'

type Input = Record<string, unknown>

export const productApi = {
  products: () => api<Product[]>('/api/v1/products'),
  product: (id: number) => api<Product>(`/api/v1/products/${id}`),
  saveProduct: (id: number | undefined, input: Input) => api<Product>(`/api/v1/products${id ? `/${id}` : ''}`, {
    method: id ? 'PUT' : 'POST', body: JSON.stringify(input),
  }),
  versions: (productId: number) => api<ProductVersion[]>(`/api/v1/products/${productId}/versions`),
  saveVersion: (productId: number, id: number | undefined, input: Input) => api<ProductVersion>(
    `/api/v1/products/${productId}/versions${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(input),
    },
  ),
  modules: (productId: number) => api<ProductModule[]>(`/api/v1/products/${productId}/modules`),
  saveModule: (productId: number, id: number | undefined, input: Input) => api<ProductModule>(
    `/api/v1/products/${productId}/modules${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(input),
    },
  ),
  features: (productId: number, moduleId?: number) => api<ProductFeature[]>(
    `/api/v1/products/${productId}/features${moduleId ? `?moduleId=${moduleId}` : ''}`,
  ),
  saveFeature: (productId: number, id: number | undefined, input: Input) => api<ProductFeature>(
    `/api/v1/products/${productId}/features${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(input),
    },
  ),
  manifest: (productId: number, versionId: number) => api<VersionManifest>(
    `/api/v1/products/${productId}/versions/${versionId}/features`,
  ),
  replaceManifest: (productId: number, versionId: number, input: Input) => api<VersionManifest>(
    `/api/v1/products/${productId}/versions/${versionId}/features`, {
      method: 'PUT', body: JSON.stringify(input),
    },
  ),
  coverage: (productId: number) => api<ProductCoverage>(`/api/v1/products/${productId}/coverage`),
  documentNodes: (productId: number) => api<ProductDocumentNode[]>(
    `/api/v1/products/${productId}/document-nodes`,
  ),
  saveDocumentNode: (productId: number, id: number | undefined, input: Input) => api<ProductDocumentNode>(
    `/api/v1/products/${productId}/document-nodes${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(input),
    },
  ),
  retryDocumentNode: (productId: number, nodeId: number) => api<ProductDocumentNode>(
    `/api/v1/products/${productId}/document-nodes/${nodeId}/retry`, { method: 'POST' },
  ),
  documentNodeContent: (productId: number, nodeId: number) => api<DocumentContent>(
    `/api/v1/products/${productId}/document-nodes/${nodeId}/content`,
  ),
  saveDocumentNodeContent: (productId: number, nodeId: number, input: SaveDocumentInput) =>
    api<DocumentContent>(`/api/v1/products/${productId}/document-nodes/${nodeId}/content`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  documentNodeExportUrl: (productId: number, nodeId: number, format: DocumentFormat) =>
    apiPath(`/api/v1/products/${productId}/document-nodes/${nodeId}/export?format=${format}`),
}
