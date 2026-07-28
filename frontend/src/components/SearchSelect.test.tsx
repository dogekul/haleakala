import { Form } from 'antd'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { SearchSelect } from './SearchSelect'

const products = [
  { value: 8, label: 'CP-001 · 消保合规' },
  { value: 9, label: 'RISK-002 · 风险管理' },
]

it('按可见编码和名称搜索并向表单提交原始 ID', async () => {
  const submit = vi.fn()
  const user = userEvent.setup()
  render(<Form onFinish={submit}>
    <Form.Item name="productId">
      <SearchSelect aria-label="产品" options={products} />
    </Form.Item>
    <button type="submit">保存</button>
  </Form>)

  const select = screen.getByRole('combobox', { name: '产品' })
  await user.click(select)
  await user.type(select, 'cp 001')
  await user.click(await screen.findByText('CP-001 · 消保合规'))
  await user.click(screen.getByRole('button', { name: '保存' }))

  expect(submit).toHaveBeenCalledWith({ productId: 8 })
})

it('异步选项暂时清空时保留当前选择但不保留无关旧选项', async () => {
  const user = userEvent.setup()
  const view = render(
    <SearchSelect aria-label="产品" value={8} options={products} />,
  )

  view.rerender(
    <SearchSelect aria-label="产品" value={8} loading options={[]} />,
  )
  expect(screen.getByText('CP-001 · 消保合规')).toBeInTheDocument()

  view.rerender(
    <SearchSelect aria-label="产品" value={undefined} options={[]} />,
  )
  expect(screen.queryByText('CP-001 · 消保合规')).not.toBeInTheDocument()

  await user.click(screen.getByRole('combobox', { name: '产品' }))
  expect(screen.queryByText('RISK-002 · 风险管理')).not.toBeInTheDocument()
})
