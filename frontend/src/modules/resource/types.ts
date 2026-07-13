export interface EngineerSkill { id: number; code: string; name: string; category: string; proficiency: number; certified: boolean; experienceMonths: number }
export interface TeamMember { userId: number; username: string; displayName: string; email?: string; jobTitle: string; location: string; weeklyCapacityHours: number; resourceStatus: string; skills: EngineerSkill[] }
export interface Assignment { id: number; userId: number; displayName: string; projectId: number; projectCode: string; projectName: string; role: string; startDate: string; endDate: string; allocationPercent: number; status: string; version: number }
export interface ResourceLoad { userId: number; displayName: string; jobTitle: string; weeklyCapacityHours: number; allocationPercent: number; availablePercent: number; loadStatus: 'OVERLOAD' | 'HIGH' | 'BALANCED' | 'AVAILABLE' }
export interface ResourceConflict { userId: number; displayName: string; startDate: string; endDate: string; peakAllocationPercent: number; assignments: Assignment[] }
export interface SkillCatalogItem { id: number; code: string; name: string; category: string; status: string }
