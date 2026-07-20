import { FileTextOutlined, FolderOpenOutlined, SyncOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Space, Tag, Tree, Typography, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useMemo, useState } from 'react'
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
    queryKey: ['product-document-nodes', productId],
    queryFn: () => productApi.documentNodes(productId),
  })
  const [nodeId, setNodeId] = useState<number>()
  const selected = query.data?.find(item => item.id === nodeId)
  const retry = useMutation({
    mutationFn: (id: number) => productApi.retryDocumentNode(productId, id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['product-document-nodes', productId] })
      message.success('文档已重新同步')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const tree = useMemo(() => buildDocumentTree(query.data ?? []), [query.data])

  return <div className="product-document-layout">
    <Card className="product-document-sidebar" styles={{ body: { padding: 0 } }}>
      <div className="product-document-sidebar-head">
        <div><Typography.Title level={5}>产品资料目录</Typography.Title>
          <Typography.Text type="secondary">独立文档工作区 · 内容同步至 Outline</Typography.Text></div>
      </div>
      {query.error ? <Alert type="error" showIcon message={(query.error as Error).message}
        action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} />
        : tree.length ? <Tree blockNode showIcon defaultExpandAll treeData={tree}
          selectedKeys={nodeId ? [`DOCUMENT-${nodeId}`] : []}
          onSelect={(_, info) => {
            const node = info.node as DataNode & { documentNodeId?: number }
            if (node.documentNodeId) setNodeId(node.documentNodeId)
          }} />
          : !query.isLoading && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无产品文档目录" />}
    </Card>
    <Card className="product-document-content" styles={{ body: { padding: 0 } }}>
      {selected ? <>
        {selected.syncStatus !== 'READY' && <Alert className="product-document-alert" showIcon
          type={selected.syncStatus === 'FAILED' ? 'error' : 'warning'}
          message={`${selected.title}尚未就绪`}
          description="请重试同步，不会影响产品模块与功能。"
          action={!readOnly && <Button icon={<SyncOutlined />} loading={retry.isPending}
            onClick={() => retry.mutate(selected.id)}>重试</Button>} />}
        {selected.syncStatus === 'READY' && <DocumentWorkspace
          key={selected.id}
          title={selected.title}
          load={() => productApi.documentNodeContent(productId, selected.id)}
          save={input => productApi.saveDocumentNodeContent(productId, selected.id, input)}
          exportUrl={format => productApi.documentNodeExportUrl(productId, selected.id, format)}
          canEdit={!readOnly}
          onSaved={() => message.success('产品文档已保存')}
        />}
      </> : <div className="product-document-empty"><Empty
        description="从左侧选择一份文档查看正文" /></div>}
    </Card>
  </div>
}

function buildDocumentTree(values: ProductDocumentNode[]): Array<DataNode & { documentNodeId?: number }> {
  const children = (parentId?: number): Array<DataNode & { documentNodeId?: number }> =>
    values.filter(item => parentId === undefined ? item.parentId == null : item.parentId === parentId).map(item => {
      const meta = statusMeta[item.syncStatus]
      const document = item.nodeType === 'DOCUMENT'
      return {
        key: `${item.nodeType}-${item.id}`,
        documentNodeId: document ? item.id : undefined,
        selectable: document,
        icon: document ? <FileTextOutlined /> : <FolderOpenOutlined />,
        title: <Space size={6}><span>{item.title}</span>
          <Tag color={meta.color}>{meta.label}</Tag></Space>,
        children: document ? [] : children(item.id),
      }
    })
  return children()
}
