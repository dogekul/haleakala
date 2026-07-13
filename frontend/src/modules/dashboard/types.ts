export interface DashboardSummary {
  activeProjects: number
  totalProjects: number
  redProjects: number
  yellowProjects: number
  healthScore: number
  openRisks: number
  overdueMilestones: number
  stageDistribution: Record<string, number>
  productDistribution: Record<string, number>
}

export interface DashboardProject {
  id: number
  code: string
  name: string
  customerName: string
  productName: string
  productVersionName: string
  managerName: string
  status: string
  currentStage: string
  riskLevel: 'GREEN' | 'YELLOW' | 'RED'
  progress: number
  openRiskCount: number
  overdueMilestoneCount: number
  plannedEndDate?: string
  daysRemaining?: number
}

export interface RiskHeatmapRow { projectId: number; projectCode: string; projectName: string; category: string; riskCount: number; maxScore: number }
export interface MatrixRow { productName: string; projects: DashboardProject[] }
