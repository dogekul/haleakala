import { api } from '../../services/api'
import type { Customer, CustomerStatus } from './types'

export const customerApi = {
  list: (filters: { keyword?: string; status?: CustomerStatus } = {}) => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    const query = params.toString()
    return api<Customer[]>(`/api/v1/customers${query ? `?${query}` : ''}`)
  },
  save: (id: number | undefined, input: Record<string, unknown>) => api<Customer>(
    `/api/v1/customers${id ? `/${id}` : ''}`,
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  ),
}
