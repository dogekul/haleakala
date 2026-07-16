const destinations = [
  { path: '/dashboard', permissions: ['dashboard:read'] },
  { path: '/customers/opportunities', permissions: ['crm:read'] },
  { path: '/customers', permissions: ['customer:read'] },
  { path: '/projects', permissions: ['project:read'] },
  { path: '/products', permissions: ['product:read'] },
  { path: '/requirements', permissions: ['requirement:read'] },
  { path: '/standardization', permissions: ['standardization:read'] },
  { path: '/knowledge', permissions: ['knowledge:read'] },
  { path: '/resources', permissions: ['resource:read'] },
  { path: '/audit-logs', permissions: ['audit:read'] },
  { path: '/admin', permissions: ['system:manage'] },
]

export function homeRoute(permissions: string[], requested?: string) {
  if (requested) {
    const destination = destinations.find(item => requested.startsWith(item.path))
    if (destination && destination.permissions.some(permission => permissions.includes(permission))) return requested
  }
  return destinations.find(item => item.permissions.some(permission => permissions.includes(permission)))?.path ?? '/403'
}
