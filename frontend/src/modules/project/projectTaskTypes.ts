export type ProjectTaskStatus = 'TODO' | 'DONE'
export type ProjectTaskPriority = 'LOW' | 'NORMAL' | 'HIGH'
export type ProjectTaskFilter = 'mine' | 'all' | 'today' | 'overdue' | 'completed'

export interface ProjectTaskCheckItem {
  id?: number
  content: string
  completed: boolean
  sortOrder: number
}

export interface ProjectTask {
  id: number
  projectId: number
  title: string
  description?: string | null
  status: ProjectTaskStatus
  priority: ProjectTaskPriority
  creatorUserId: number
  creatorName: string
  assigneeUserId: number
  assigneeName: string
  dueAt?: string | null
  stageCode?: string | null
  milestoneId?: number | null
  milestoneName?: string | null
  completedByUserId?: number | null
  completedAt?: string | null
  reminderId?: number | null
  reminderAt?: string | null
  reminderEnabled: boolean
  checklist: ProjectTaskCheckItem[]
  checklistCompleted: number
  checklistTotal: number
  version: number
  canEdit: boolean
  canDelete: boolean
}

export interface CreateProjectTaskInput {
  title: string
  assigneeUserId: number
  dueAt: string | null
}

export interface UpdateProjectTaskInput {
  title: string
  description: string | null
  priority: ProjectTaskPriority
  assigneeUserId: number
  dueAt: string | null
  stageCode: string | null
  milestoneId: number | null
  reminderEnabled: boolean
  reminderAt: string | null
  version: number
  checklist: Array<Omit<ProjectTaskCheckItem, 'id'>>
}

export interface TaskReminder {
  id: number
  taskId: number
  projectId: number
  projectName: string
  taskTitle: string
  dueAt?: string | null
  remindAt: string
}
