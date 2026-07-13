import { Button, Result, Spin } from 'antd'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { homeRoute } from './homeRoute'

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="full-state"><Spin size="large" /></div>
  if (!me) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  return <>{children}</>
}

export function RequirePermission({ code, children }: { code: string; children: React.ReactNode }) {
  const { me } = useAuth()
  return me?.permissions.includes(code) ? <>{children}</> : <Navigate to="/403" replace />
}

export function ForbiddenPage() {
  const { me } = useAuth()
  return <Result status="403" title="403" subTitle="你没有访问此页面的权限"
    extra={<Button type="primary" href={homeRoute(me?.permissions ?? [])}>返回工作台</Button>} />
}
