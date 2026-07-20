import { api } from '../../services/api'
import type {
  Product, ProductCoverage, ProductDocumentNode, ProductDocumentSyncResult, ProductFeature,
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
  documents: (productId: number) => api<ProductDocumentNode[]>(
    `/api/v1/products/${productId}/documents`,
  ),
  syncDocuments: (productId: number) => api<ProductDocumentSyncResult>(
    `/api/v1/products/${productId}/documents/sync`, { method: 'POST' },
  ),
  featureSpec: (productId: number, featureId: number) => api<DocumentContent>(
    `/api/v1/products/${productId}/features/${featureId}/spec`,
  ),
  saveFeatureSpec: (productId: number, featureId: number, input: SaveDocumentInput) =>
    api<DocumentContent>(`/api/v1/products/${productId}/features/${featureId}/spec`, {
      method: 'PUT', body: JSON.stringify(input),
    }),
  featureSpecExportUrl: (productId: number, featureId: number, format: DocumentFormat) =>
    apiPath(`/api/v1/products/${productId}/features/${featureId}/spec/export?format=${format}`),
}
