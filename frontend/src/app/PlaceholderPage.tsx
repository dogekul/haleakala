import { ArrowRightOutlined, CheckCircleFilled } from '@ant-design/icons'
import { Button, Card, Col, Row, Tag, Typography } from 'antd'

const moduleData: Record<string, { title: string; subtitle: string; functions: string[] }> = {
  dashboard: { title: '交付驾驶舱', subtitle: '跨项目状态、风险与标准化指标的统一入口', functions: ['项目卡片墙', '风险热力图', '全局矩阵视图', '快速创建项目'] },
  projects: { title: '项目空间', subtitle: '围绕七阶段生命周期推进每一个交付项目', functions: ['七阶段生命周期', 'Skill / Agent 执行', '模板中心', '风险登记册', '里程碑与时间线', '项目信息与设置'] },
  requirements: { title: '需求工坊', subtitle: '从需求采集到 L0 / L1 / L2 分类确认', functions: ['需求采集单', 'AI 分类决策树', '三层漏斗', '需求去重与合并', '需求列表与看板'] },
  standardization: { title: '标准化中心', subtitle: '把项目二开持续回流为标品能力', functions: ['标品能力卡', '成熟度评估', '偏离度分析', '标准化债务', '二开成本归因', '飞轮仪表盘'] },
  knowledge: { title: '知识库', subtitle: '检索可复用的范例、代码与培训材料', functions: ['范例检索', '二开代码片段库', '培训材料管理'] },
  resources: { title: '资源中心', subtitle: '看清团队技能、配置、冲突与负载', functions: ['团队人员墙', '项目人力配置', '资源冲突检测', '团队负载看板'] },
  admin: { title: '系统管理', subtitle: '维护平台账号、权限、目录与运行设置', functions: ['用户与团队', '角色权限', '产品与版本', '审计日志', '系统配置'] },
}

export function PlaceholderPage({ module }: { module: keyof typeof moduleData }) {
  const data = moduleData[module]
  return (
    <div className="module-overview">
      <div className="page-heading">
        <div><Tag color="blue">交付范式 v2.0</Tag><Typography.Title level={2}>{data.title}</Typography.Title>
          <Typography.Paragraph>{data.subtitle}</Typography.Paragraph></div>
        <Button type="primary">开始使用 <ArrowRightOutlined /></Button>
      </div>
      <Row gutter={[16, 16]}>
        {data.functions.map((item, index) => <Col key={item} xs={24} md={12} xl={8}>
          <Card className="function-card" hoverable>
            <div className="function-number">{String(index + 1).padStart(2, '0')}</div>
            <Typography.Title level={4}>{item}</Typography.Title>
            <Typography.Paragraph type="secondary">真实数据流与操作入口将在此工作区集中呈现。</Typography.Paragraph>
            <span className="function-ready"><CheckCircleFilled /> 能力已纳入交付范围</span>
          </Card>
        </Col>)}
      </Row>
    </div>
  )
}
