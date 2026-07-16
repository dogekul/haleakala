import { ArrowLeftOutlined, EditOutlined, RightOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Descriptions, Space, Tag, Typography, message } from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { crmApi } from './crmApi'
import { formatDate, operationStageLabel } from './OperationBoardPage'
import { OperationEditor } from './OperationEditor'

export function OperationDetailPage() {
  const id = Number(useParams().id)
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('crm:write') ?? false
  const [editing, setEditing] = useState(false)
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['operation', id], queryFn: () => crmApi.operation(id), enabled: Boolean(id) })
  const advance = useMutation({ mutationFn: () => crmApi.advanceOperation(id, query.data!.version),
    onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['operation', id] }), client.invalidateQueries({ queryKey: ['operations'] })]); message.success('运营阶段已推进') },
    onError: (error: Error) => message.error(error.message) })
  const item = query.data
  return <div className="crm-page operation-detail"><Link className="detail-back" to="/customers/operations"><ArrowLeftOutlined /> 返回客户运营</Link>
    <PageState loading={query.isLoading} error={query.error} empty={!item} onRetry={() => void query.refetch()}>
      {item && <><div className="crm-detail-hero"><div><Space><Tag color={item.status === 'OPEN' ? 'processing' : 'default'}>{operationStageLabel(item.stage)}</Tag><Tag>{item.status === 'OPEN' ? '进行中' : '已关闭'}</Tag></Space>
        <Typography.Title level={2}>{item.title}</Typography.Title><p>{item.customerName}</p></div>
        {canWrite && item.status === 'OPEN' && <Space><Button icon={<EditOutlined />} aria-label="编辑运营" onClick={() => setEditing(true)}>编辑</Button><Button type="primary" icon={<RightOutlined />} aria-label="推进运营" onClick={() => advance.mutate()}>{item.stage === 'REPURCHASE' ? '关闭运营' : '推进阶段'}</Button></Space>}
      </div><Card className="crm-surface"><Descriptions column={{ xs: 1, sm: 2, lg: 3 }}>
        <Descriptions.Item label="客户">{item.customerName}</Descriptions.Item><Descriptions.Item label="运营负责人">{item.ownerName ?? '未分配'}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{formatDate(item.updatedAt)}</Descriptions.Item><Descriptions.Item label="来源商机">{item.opportunity
          ? <Link to={`/customers/opportunities/${item.opportunity.id}`}>{item.opportunity.title}</Link> : '手工创建'}</Descriptions.Item>
        <Descriptions.Item label="来源项目">{item.project ? <Link to={`/projects/${item.project.id}`}>{item.project.name}</Link> : '未关联'}</Descriptions.Item>
        <Descriptions.Item label="版本">v{item.version}</Descriptions.Item>
      </Descriptions></Card><Card className="crm-full-link" title="客户运营全链"><div className="full-link-track operation-link-track">
        <div><span>客户</span><strong>{item.customerName}</strong></div><i>→</i><div><span>商机</span><strong>{item.opportunity?.title ?? '未关联'}</strong></div><i>→</i>
        <div><span>项目</span><strong>{item.project?.name ?? '未关联'}</strong></div><i>→</i><div><span>运营</span><strong>{item.title}</strong></div>
      </div></Card><OperationEditor value={item} open={editing} onClose={() => setEditing(false)} /></>}
    </PageState></div>
}
