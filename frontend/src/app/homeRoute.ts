const destinations = [
  { path: '/dashboard', permission: 'dashboard:read' },
  { path: '/projects', permission: 'project:read' },
  { path: '/products', permission: 'product:read' },
  { path: '/requirements', permission: 'requirement:read' },
  { path: '/standardization', permission: 'standardization:read' },
  { path: '/knowledge', permission: 'knowledge:read' },
  { path: '/resources', permission: 'resource:read' },
  { path: '/audit-logs', permission: 'audit:read' },
  { path: '/admin', permission: 'system:manage' },
]

export function homeRoute(permissions: string[], requested?: string) {
  if (requested) {
    const destination = destinations.find(item => requested.startsWith(item.path))
    if (destination && permissions.includes(destination.permission)) return requested
  }
  return destinations.find(item => permissions.includes(item.permission))?.path ?? '/403'
}
