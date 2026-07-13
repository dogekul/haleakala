import type { ThemeConfig } from 'antd'

export const theme: ThemeConfig = {
  token: {
    colorPrimary: '#3370ff',
    colorInfo: '#3370ff',
    colorSuccess: '#2ea869',
    colorWarning: '#f5a623',
    colorError: '#f54a45',
    colorBgLayout: '#f5f6f7',
    colorText: '#1f2329',
    colorTextSecondary: '#646a73',
    colorBorder: '#dee0e3',
    borderRadius: 6,
    fontSize: 14,
    controlHeight: 34,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
  components: {
    Card: { paddingLG: 20, boxShadowTertiary: '0 1px 3px rgba(31,35,41,.06)' },
    Table: { headerBg: '#f7f8fa', headerColor: '#646a73', cellPaddingBlock: 12 },
    Menu: { itemHeight: 40, itemBorderRadius: 6 },
    Button: { primaryShadow: 'none' },
  },
}
