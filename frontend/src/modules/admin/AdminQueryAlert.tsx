import { Alert, Button } from 'antd'

export function AdminQueryAlert({ errors, onRetry }: { errors: unknown[]; onRetry(): void }) {
  const error = errors.find(Boolean)
  if (!error) return null
  return <Alert
    className="admin-query-alert"
    type="error"
    showIcon
    message="管理数据加载失败"
    description={error instanceof Error ? error.message : '请稍后重试'}
    action={<Button size="small" onClick={onRetry}>重试</Button>}
  />
}
