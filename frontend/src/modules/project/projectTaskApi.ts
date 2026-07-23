import { api } from '../../services/api'
import type {
  CreateProjectTaskInput,
  ProjectTask,
  ProjectTaskFilter,
  TaskReminder,
  UpdateProjectTaskInput,
} from './projectTaskTypes'

export const projectTaskApi = {
  list: (projectId: number, filter: ProjectTaskFilter) =>
    api<ProjectTask[]>(`/api/v1/projects/${projectId}/tasks?filter=${filter}`),
  get: (projectId: number, taskId: number) =>
    api<ProjectTask>(`/api/v1/projects/${projectId}/tasks/${taskId}`),
  create: (projectId: number, input: CreateProjectTaskInput) =>
    api<ProjectTask>(`/api/v1/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  update: (projectId: number, taskId: number, input: UpdateProjectTaskInput) =>
    api<ProjectTask>(`/api/v1/projects/${projectId}/tasks/${taskId}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),
  complete: (projectId: number, taskId: number) =>
    api<ProjectTask>(`/api/v1/projects/${projectId}/tasks/${taskId}/complete`, {
      method: 'POST',
    }),
  reopen: (projectId: number, taskId: number) =>
    api<ProjectTask>(`/api/v1/projects/${projectId}/tasks/${taskId}/reopen`, {
      method: 'POST',
    }),
  remove: (projectId: number, taskId: number) =>
    api<void>(`/api/v1/projects/${projectId}/tasks/${taskId}`, {
      method: 'DELETE',
    }),
  unreadReminders: () => api<TaskReminder[]>('/api/v1/task-reminders/unread'),
  readReminder: (reminderId: number) =>
    api<void>(`/api/v1/task-reminders/${reminderId}/read`, { method: 'POST' }),
}
