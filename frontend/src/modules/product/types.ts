export type ProductStatus = 'PLANNING' | 'ACTIVE' | 'SUNSET' | 'ARCHIVED'
export type VersionStatus = 'PLANNING' | 'RELEASED' | 'SUNSET' | 'ARCHIVED'
export type StructureStatus = 'PLANNING' | 'ACTIVE' | 'DEPRECATED'
export type Availability = 'INCLUDED' | 'PLANNED' | 'REMOVED'

export interface Product {
  id: number
  organizationId: number
  ownerUserId?: number
  code: string
  name: string
  category?: string
  description?: string
  status: ProductStatus
  moduleCount: number
  featureCount: number
  latestVersionName?: string
  updatedAt: string
  version: number
}

export interface ProductVersion {
  id: number
  productId: number
  versionName: string
  releaseDate?: string
  status: VersionStatus
  version: number
}

export interface ProductModule {
  id: number
  productId: number
  parentId?: number
  ownerUserId?: number
  code: string
  name: string
  description?: string
  status: StructureStatus
  sortOrder: number
  version: number
}

export interface ProductFeature {
  id: number
  productId: number
  moduleId: number
  ownerUserId?: number
  code: string
  name: string
  description?: string
  status: StructureStatus
  version: number
}

export interface ManifestEntry { featureId: number; availability: Availability }
export interface VersionManifest { versionId: number; version: number; entries: ManifestEntry[] }
export interface CoverageFeature {
  featureId: number
  featureCode: string
  featureName: string
  moduleName: string
  fullCount: number
  partialCount: number
}
export interface UncoveredRequirement {
  requirementId: number
  requirementCode: string
  title: string
  projectCode: string
  debtLinked: boolean
}
export interface ProductCoverage {
  productId: number
  features: CoverageFeature[]
  uncoveredRequirements: UncoveredRequirement[]
}

export type ProductDocumentKind = 'PRODUCT' | 'MODULE' | 'FEATURE'
export interface ProductDocumentNode {
  kind: ProductDocumentKind
  id: number
  parentId?: number
  title: string
  syncStatus: 'PENDING' | 'CREATING' | 'READY' | 'FAILED'
  featureId?: number
}

export interface ProductDocumentSyncResult {
  products: number
  completed: number
  failed: number
}
