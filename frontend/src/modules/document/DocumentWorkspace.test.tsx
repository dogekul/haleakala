import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../services/api'
import { DocumentWorkspace } from './DocumentWorkspace'
import type { DocumentContent, DocumentFormat } from './types'

const content: DocumentContent = {
  linkId: 12,
  title: '项目启动会纪要',
  markdown: '# 启动会\n\n已明确范围。',
  renderedHtml: '<h1>启动会</h1><p>已明确范围。</p>',
  revision: 7,
  updatedAt: '2026-07-16T08:00:00Z',
  syncStatus: 'READY',
  outlineUrl: 'http://localhost:3000/doc/start-12',
}

it('loads, edits and saves with the loaded Outline revision', async () => {
  const load = vi.fn().mockResolvedValue(content)
  const save = vi.fn().mockImplementation(async input => ({
    ...content,
    ...input,
    revision: 8,
    renderedHtml: '<h1>启动会</h1><p>增加风险清单。</p>',
  }))
  render(<DocumentWorkspace
    title="启动文档"
    load={load}
    save={save}
    exportUrl={format => `/export?format=${format}`}
    canEdit
  />)

  expect(await screen.findByText('已明确范围。')).toBeVisible()
  expect(screen.getByText('修订 7')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '编辑' }))
  const editor = screen.getByRole('textbox', { name: 'Markdown 正文' })
  await userEvent.clear(editor)
  await userEvent.type(editor, '# 启动会\n\n增加风险清单。')
  await userEvent.click(screen.getByRole('button', { name: '保存' }))

  await waitFor(() => expect(save).toHaveBeenCalledWith({
    title: '项目启动会纪要',
    markdown: '# 启动会\n\n增加风险清单。',
    revision: 7,
  }))
  expect(await screen.findByText('修订 8')).toBeVisible()
})

it('keeps local markdown and explains a revision conflict', async () => {
  const server = { ...content, markdown: '# 服务端新版本', revision: 8 }
  const load = vi.fn()
    .mockResolvedValueOnce(content)
    .mockResolvedValueOnce(server)
  const save = vi.fn().mockRejectedValue(
    new ApiError(409, 'CONFLICT', '文档已在 Outline 中更新，请刷新后合并'),
  )
  render(<DocumentWorkspace
    title="启动文档"
    load={load}
    save={save}
    exportUrl={format => `/export?format=${format}`}
    canEdit
  />)

  await userEvent.click(await screen.findByRole('button', { name: '编辑' }))
  const editor = screen.getByRole('textbox', { name: 'Markdown 正文' })
  await userEvent.clear(editor)
  await userEvent.type(editor, '我的本地修改')
  await userEvent.click(screen.getByRole('button', { name: '保存' }))

  expect(await screen.findByText(/Outline 中已有更新/)).toBeVisible()
  expect(editor).toHaveValue('我的本地修改')
  await userEvent.click(screen.getByRole('button', { name: '放弃本地修改并刷新' }))
  await waitFor(() => expect(
    screen.getByRole('textbox', { name: 'Markdown 正文' }),
  ).toHaveValue('# 服务端新版本'))
})

it('opens Outline and exposes four backend export links', async () => {
  const exportUrl = vi.fn((format: DocumentFormat) => `/download?format=${format}`)
  render(<DocumentWorkspace
    title="启动文档"
    load={() => Promise.resolve(content)}
    save={() => Promise.resolve(content)}
    exportUrl={exportUrl}
    canEdit={false}
  />)

  expect(await screen.findByRole('link', { name: '在 Outline 中打开' }))
    .toHaveAttribute('href', content.outlineUrl)
  await userEvent.click(screen.getByRole('button', { name: /导出/ }))
  const menu = await screen.findByRole('menu')
  for (const label of ['Markdown', 'HTML', 'PDF', 'Word']) {
    expect(within(menu).getByRole('link', { name: label })).toBeInTheDocument()
  }
  for (const format of ['md', 'html', 'pdf', 'docx']) {
    expect(exportUrl).toHaveBeenCalledWith(format)
  }
})

it('shows failed synchronization with retry instead of an empty success state', async () => {
  const load = vi.fn()
    .mockRejectedValueOnce(new Error('Outline 暂不可用'))
    .mockResolvedValueOnce(content)
  render(<DocumentWorkspace
    title="启动文档"
    load={load}
    save={() => Promise.resolve(content)}
    exportUrl={format => `/export?format=${format}`}
    canEdit
  />)

  expect(await screen.findByText('Outline 暂不可用')).toBeVisible()
  expect(screen.queryByText('已明确范围。')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '重试' }))
  expect(await screen.findByText('已明确范围。')).toBeVisible()
})
