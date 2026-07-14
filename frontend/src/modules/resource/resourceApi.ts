import { api } from '../../services/api'
import type { Assignment, EngineerSkill, ResourceConflict, ResourceLoad, SkillCatalogItem, TeamMember } from './types'

export const resourceApi = {
  team: () => api<TeamMember[]>('/api/v1/resources/team'),
  skills: () => api<SkillCatalogItem[]>('/api/v1/resources/skills'),
  assignments: () => api<Assignment[]>('/api/v1/resources/assignments'),
  load: (from: string, to: string) => api<ResourceLoad[]>(`/api/v1/resources/load?from=${from}&to=${to}`),
  conflicts: (from: string, to: string) => api<ResourceConflict[]>(`/api/v1/resources/conflicts?from=${from}&to=${to}`),
  assign: (input: Record<string, unknown>) => api<Assignment>('/api/v1/resources/assignments', { method: 'POST', body: JSON.stringify(input) }),
  updateAssignment: (id: number, input: Record<string, unknown>) => api<Assignment>(`/api/v1/resources/assignments/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  saveProfile: (userId: number, input: Record<string, unknown>) => api<TeamMember>(`/api/v1/resources/team/${userId}/profile`, { method: 'PUT', body: JSON.stringify(input) }),
  saveSkill: (userId: number, input: Record<string, unknown>) => api<EngineerSkill>(`/api/v1/resources/team/${userId}/skills`, { method: 'POST', body: JSON.stringify(input) }),
}
