import { Button, Empty, Result, Skeleton } from 'antd'

export function PageState({
  loading,
  error,
  empty,
  onRetry,
  children,
}: {
  loading?: boolean
  error?: Error | null
  empty?: boolean
  onRetry?: () => void
  children: React.ReactNode
}) {
  if (loading) return <div className="surface"><Skeleton active /></div>
  if (error) return <Result status="error" title="页面加载失败" subTitle={error.message}
    extra={<Button onClick={onRetry}>重新加载</Button>} />
  if (empty) return <div className="surface"><Empty description="暂无数据" /></div>
  return <>{children}</>
}
