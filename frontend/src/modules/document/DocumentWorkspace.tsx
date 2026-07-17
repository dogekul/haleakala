import {
  CloudSyncOutlined, DownloadOutlined, EditOutlined, EyeOutlined, LinkOutlined,
  ReloadOutlined, SaveOutlined,
} from '@ant-design/icons'
import { Alert, Button, Dropdown, Input, Space, Spin, Tag, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../services/api'
import type { DocumentContent, DocumentFormat, SaveDocumentInput } from './types'

export interface DocumentWorkspaceProps {
  title: string
  load(): Promise<DocumentContent>
  save(input: SaveDocumentInput): Promise<DocumentContent>
  submit?(input: SaveDocumentInput): Promise<DocumentContent>
  submitLabel?: string
  exportUrl(format: DocumentFormat): string
  canEdit: boolean
  onSaved?(): void
  onSubmitted?(document: DocumentContent): void
}

const formats: Array<{ format: DocumentFormat; label: string }> = [
  { format: 'md', label: 'Markdown' },
  { format: 'html', label: 'HTML' },
  { format: 'pdf', label: 'PDF' },
  { format: 'docx', label: 'Word' },
]

export function DocumentWorkspace({
  title, load, save, submit, submitLabel = '提交', exportUrl, canEdit, onSaved, onSubmitted,
}: DocumentWorkspaceProps) {
  const loadRef = useRef(load)
  loadRef.current = load
  const [document, setDocument] = useState<DocumentContent>()
  const [draftTitle, setDraftTitle] = useState(title)
  const [markdown, setMarkdown] = useState('')
  const [mode, setMode] = useState<'preview' | 'edit'>('preview')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      const value = await loadRef.current()
      setDocument(value)
      setDraftTitle(value.title || title)
      setMarkdown(value.markdown)
      setSaveError('')
    } catch (error) {
      setDocument(undefined)
      setLoadError((error as Error).message || '文档加载失败')
    } finally {
      setLoading(false)
    }
  }, [title])

  useEffect(() => {
    void reload()
  }, [reload])

  const persist = async (action: typeof save, submitted = false) => {
    if (!document) return
    setSaving(true)
    setSaveError('')
    try {
      const value = await action({
        title: draftTitle,
        markdown,
        revision: document.revision,
      })
      setDocument(value)
      setDraftTitle(value.title)
      setMarkdown(value.markdown)
      setMode('preview')
      if (submitted) onSubmitted?.(value)
      else onSaved?.()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSaveError('Outline 中已有更新。你的本地内容仍保留在编辑器中；放弃本地修改后才能刷新服务端版本。')
      } else {
        setSaveError((error as Error).message || '保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <div className="document-workspace document-loading"><Spin /></div>
  }
  if (loadError) {
    return <div className="document-workspace">
      <Alert
        showIcon
        type="error"
        message="文档同步失败"
        description={loadError}
        action={<Button aria-label="重试" icon={<ReloadOutlined />} onClick={() => void reload()}>重试</Button>}
      />
    </div>
  }
  if (!document) return null

  const exportItems = formats.map(item => ({
    key: item.format,
    label: <a href={exportUrl(item.format)}>{item.label}</a>,
  }))
  return <section className="document-workspace">
    <header className="document-toolbar">
      <div className="document-toolbar-title">
        <CloudSyncOutlined />
        <div><strong>{document.title || title}</strong><span>修订 {document.revision}</span></div>
        <Tag color={document.syncStatus === 'READY' ? 'success' : 'warning'}>
          {document.syncStatus === 'READY' ? '已同步' : document.syncStatus}
        </Tag>
      </div>
      <Space wrap>
        <Space.Compact>
          <Button
            aria-label="预览"
            type={mode === 'preview' ? 'primary' : 'default'}
            icon={<EyeOutlined />}
            onClick={() => setMode('preview')}
          >预览</Button>
          {canEdit && <Button
            aria-label="编辑"
            type={mode === 'edit' ? 'primary' : 'default'}
            icon={<EditOutlined />}
            onClick={() => setMode('edit')}
          >编辑</Button>}
        </Space.Compact>
        {document.outlineUrl && <Button
          aria-label="在 Outline 中打开"
          href={document.outlineUrl}
          target="_blank"
          rel="noreferrer"
          icon={<LinkOutlined />}
        >在 Outline 中打开</Button>}
        <Dropdown menu={{ items: exportItems }} trigger={['click']}>
          <Button aria-label="导出" icon={<DownloadOutlined />}>导出</Button>
        </Dropdown>
        {canEdit && mode === 'edit' && <Button
          aria-label="保存"
          type={submit ? 'default' : 'primary'}
          icon={<SaveOutlined />}
          loading={saving}
          onClick={() => void persist(save)}
        >{submit ? '保存草稿' : '保存'}</Button>}
        {canEdit && mode === 'edit' && submit && <Button
          aria-label={submitLabel}
          type="primary"
          loading={saving}
          onClick={() => void persist(submit, true)}
        >{submitLabel}</Button>}
      </Space>
    </header>
    {saveError && <Alert
      className="document-save-alert"
      showIcon
      type={saveError.includes('Outline 中已有更新') ? 'warning' : 'error'}
      message={saveError}
      action={saveError.includes('Outline 中已有更新')
        ? <Button size="small" danger onClick={() => void reload()}>
            放弃本地修改并刷新
          </Button>
        : undefined}
    />}
    <div className="document-canvas">
      <article className="document-paper">
        {mode === 'edit' ? <>
          <Input
            aria-label="文档标题"
            className="document-title-input"
            value={draftTitle}
            onChange={event => setDraftTitle(event.target.value)}
          />
          <Input.TextArea
            aria-label="Markdown 正文"
            className="document-markdown-editor"
            value={markdown}
            onChange={event => setMarkdown(event.target.value)}
            autoSize={{ minRows: 24 }}
          />
        </> : document.renderedHtml
          ? <div
              className="document-preview"
              dangerouslySetInnerHTML={{ __html: document.renderedHtml }}
            />
          : <Alert type="warning" showIcon message="文档正文尚未同步完成" />}
      </article>
    </div>
    {document.updatedAt && <Typography.Text className="document-updated" type="secondary">
      最近同步：{new Date(document.updatedAt).toLocaleString()}
    </Typography.Text>}
  </section>
}
