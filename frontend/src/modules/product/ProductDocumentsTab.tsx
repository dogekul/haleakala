import {
  AppstoreOutlined, FileTextOutlined, FolderOpenOutlined, SyncOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Space, Tag, Tree, Typography, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useEffect, useMemo, useState } from 'react'
import { DocumentWorkspace } from '../document/DocumentWorkspace'
import { productApi } from './productApi'
import type { ProductDocumentNode } from './types'

const statusMeta = {
  READY: { label: '已同步', color: 'success' },
  PENDING: { label: '待初始化', color: 'default' },
  CREATING: { label: '初始化中', color: 'processing' },
  FAILED: { label: '同步失败', color: 'error' },
} as const

export function ProductDocumentsTab({ productId, readOnly }: {
  productId: number
  readOnly: boolean
}) {
  const client = useQueryClient()
  const query = useQuery({
    queryKey: ['product-documents', productId],
    queryFn: () => productApi.documents(productId),
  })
  const [featureId, setFeatureId] = useState<number>()
  useEffect(() => {
    if (!featureId) setFeatureId(query.data?.find(item => item.kind === 'FEATURE')?.featureId)
  }, [featureId, query.data])
  const sync = useMutation({
    mutationFn: () => productApi.syncDocuments(productId),
    onSuccess: async result => {
      await client.invalidateQueries({ queryKey: ['product-documents', productId] })
      if (result.failed) message.warning(`已同步 ${result.completed} 项，${result.failed} 项待重试`)
      else message.success('产品文档已同步')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const tree = useMemo(() => buildDocumentTree(query.data ?? []), [query.data])
  const selected = query.data?.find(item => item.featureId === featureId)

  return <div className="product-document-layout">
    <Card className="product-document-sidebar" styles={{ body: { padding: 0 } }}>
      <div className="product-document-sidebar-head">
        <div><Typography.Title level={5}>产品资料目录</Typography.Title>
          <Typography.Text type="secondary">产品结构与 Outline 实时对应</Typography.Text></div>
        {!readOnly && <Button aria-label="同步产品文档" icon={<SyncOutlined />}
          loading={sync.isPending} onClick={() => sync.mutate()}>同步</Button>}
      </div>
      {query.error ? <Alert type="error" showIcon message={(query.error as Error).message}
        action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} />
        : tree.length ? <Tree blockNode showIcon defaultExpandAll treeData={tree}
          selectedKeys={featureId ? [`FEATURE-${featureId}`] : []}
          onSelect={(_, info) => {
            const node = info.node as DataNode & { featureId?: number }
            if (node.featureId) setFeatureId(node.featureId)
          }} />
          : !query.isLoading && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无产品文档目录" />}
    </Card>
    <Card className="product-document-content" styles={{ body: { padding: 0 } }}>
      {featureId && selected ? <>
        {selected.syncStatus !== 'READY' && <Alert className="product-document-alert" showIcon
          type={selected.syncStatus === 'FAILED' ? 'error' : 'warning'}
          message={`${selected.title}尚未就绪`}
          description="请同步产品文档，系统会从知识库的产品功能设计 Spec 模版初始化正文。" />}
        {selected.syncStatus === 'READY' && <DocumentWorkspace
          key={featureId}
          title={`${selected.title} · 设计 Spec`}
          load={() => productApi.featureSpec(productId, featureId)}
          save={input => productApi.saveFeatureSpec(productId, featureId, input)}
          exportUrl={format => productApi.featureSpecExportUrl(productId, featureId, format)}
          canEdit={!readOnly}
          onSaved={() => message.success('功能设计 Spec 已保存')}
        />}
      </> : <div className="product-document-empty"><Empty
        description="从左侧选择一个功能，查看并补充设计 Spec" /></div>}
    </Card>
  </div>
}

function buildDocumentTree(values: ProductDocumentNode[]): Array<DataNode & { featureId?: number }> {
  const children = (parent: ProductDocumentNode | undefined): Array<DataNode & { featureId?: number }> =>
    values.filter(item => parent
      ? item.parentId === parent.id && item.kind !== 'PRODUCT'
      : item.kind === 'PRODUCT').map(item => {
      const meta = statusMeta[item.syncStatus]
      const icon = item.kind === 'PRODUCT' ? <AppstoreOutlined />
        : item.kind === 'MODULE' ? <FolderOpenOutlined /> : <FileTextOutlined />
      return {
        key: `${item.kind}-${item.id}`,
        featureId: item.featureId,
        selectable: item.kind === 'FEATURE',
        icon,
        title: <Space size={6}><span>{item.title}</span>
          <Tag color={meta.color}>{meta.label}</Tag></Space>,
        children: item.kind === 'FEATURE' ? [] : children(item),
      }
    })
  return children(undefined)
}
