import { expect, it } from 'vitest'
import { formatAuditTime } from './AuditLogsPage'

it('按系统设置的时区格式化审计时间', () => {
  expect(formatAuditTime('2026-07-13T08:00:00Z', 'UTC')).toBe('2026-07-13 08:00:00')
  expect(formatAuditTime('2026-07-13T08:00:00Z', 'Asia/Shanghai')).toBe('2026-07-13 16:00:00')
  expect(formatAuditTime('not-a-date', 'Asia/Shanghai')).toBe('—')
})
