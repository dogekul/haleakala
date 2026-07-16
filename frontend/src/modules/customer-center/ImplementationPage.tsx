import { useQuery } from '@tanstack/react-query'
import { Card, Space, Table, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { crmApi } from './crmApi'
import type { Health, ImplementationItem } from './types'

export const projectStages: Record<string, string> = {
  START: '启动', REQUIREMENT: '需求采集', CUSTOM_DEV: '二开实施', GO_LIVE: '上线切换',
  TRIAL_HANDOVER: '试运行与移交', STANDARDIZATION: '标准化评估', CLOSE: '项目收尾',
}
const healthLabels: Record<Health, string> = { GREEN: '健康', YELLOW: '关注', RED: '风险' }

export function ImplementationPage() {
  const query = useQuery({ queryKey: ['implementation'], queryFn: crmApi.implementation })
  const columns = [
    { title: '客户 / 商机', key: 'customer', render: (_: unknown, item: ImplementationItem) => <div className="crm-name-cell"><strong>{item.customerName}</strong><span>{item.opportunityTitle}</span></div> },
    { title: '实施项目', key: 'project', render: (_: unknown, item: ImplementationItem) => <div className="crm-name-cell"><Link to={`/projects/${item.projectId}`}>{item.projectName}</Link><span>{item.projectCode}</span></div> },
    { title: '当前阶段', dataIndex: 'projectStage', render: (value: string) => <Tag color="blue">{projectStages[value] ?? value}</Tag> },
    { title: '负责人', dataIndex: 'managerName' },
    { title: '风险', key: 'risk', render: (_: unknown, item: ImplementationItem) => <Space direction="vertical" size={2}><Tag color={item.health === 'RED' ? 'error' : item.health === 'YELLOW' ? 'warning' : 'success'}>{healthLabels[item.health]}</Tag><span>{item.redRiskCount} 个红色风险</span></Space> },
    { title: '最近里程碑', key: 'milestone', render: (_: unknown, item: ImplementationItem) => <div className="crm-name-cell"><strong>{item.nextMilestoneName ?? '暂无待办里程碑'}</strong><span>{item.nextMilestoneDueDate ?? '—'}{item.overdueMilestoneCount ? ` · ${item.overdueMilestoneCount} 个逾期` : ''}</span></div> },
    { title: '计划完成', dataIndex: 'plannedEndDate', render: (value?: string) => value ?? '—' },
    { title: '', key: 'action', render: (_: unknown, item: ImplementationItem) => <Link to={`/projects/${item.projectId}`}>进入项目</Link> },
  ]
  return <div className="crm-page implementation-page"><div className="page-heading compact"><div>
    <Typography.Title level={2}>实施协同</Typography.Title><Typography.Paragraph>在客户视角查看已赢单项目，实施阶段以项目空间为唯一事实来源。</Typography.Paragraph>
  </div></div><Card className="crm-surface">
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && !query.data?.length} onRetry={() => void query.refetch()}>
      <Table rowKey="projectId" columns={columns} dataSource={query.data ?? []} pagination={{ pageSize: 12 }} scroll={{ x: 1180 }} />
    </PageState>
  </Card></div>
}
