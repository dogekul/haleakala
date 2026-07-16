export type CustomerStatus = 'ACTIVE' | 'INACTIVE'

export interface Customer {
  id: number
  organizationId: number
  name: string
  shortName?: string
  contactName?: string
  phone?: string
  email?: string
  address?: string
  status: CustomerStatus
  remark?: string
  projectCount: number
  updatedAt: string
  version: number
}
