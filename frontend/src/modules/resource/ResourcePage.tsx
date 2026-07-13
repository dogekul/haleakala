import {
  AlertOutlined, ApartmentOutlined, CalendarOutlined, ClockCircleOutlined,
  EnvironmentOutlined, PlusOutlined, ScheduleOutlined, TeamOutlined, ThunderboltOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Avatar, Button, Card, Col, DatePicker, Drawer, Empty, Form, Input,
  InputNumber, Progress, Row, Select, Space, Statistic, Table, Tabs, Tag, Typography, message,
} from 'antd'
import dayjs, { Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { projectApi } from '../project/projectApi'
import { resourceApi } from './resourceApi'
import type { Assignment, ResourceConflict, ResourceLoad, SkillCatalogItem, TeamMember } from './types'

const today = dayjs()
const defaultFrom = today.startOf('month').format('YYYY-MM-DD')
const defaultTo = today.endOf('month').format('YYYY-MM-DD')
const loadMeta = {
  OVERLOAD: { label: '超载', color: '#f54a45', tag: 'red' }, HIGH: { label: '高负载', color: '#f5a623', tag: 'orange' },
  BALANCED: { label: '均衡', color: '#3370ff', tag: 'blue' }, AVAILABLE: { label: '可用', color: '#2ea869', tag: 'green' },
} as const

export function ResourcePage() {
  const [range, setRange] = useState<[string, string]>([defaultFrom, defaultTo])
  const [profile, setProfile] = useState<TeamMember>()
  const [assignOpen, setAssignOpen] = useState(false)
  const team = useQuery({ queryKey: ['resource-team'], queryFn: resourceApi.team })
  const assignments = useQuery({ queryKey: ['resource-assignments'], queryFn: resourceApi.assignments })
  const load = useQuery({ queryKey: ['resource-load', ...range], queryFn: () => resourceApi.load(range[0], range[1]) })
  const conflicts = useQuery({ queryKey: ['resource-conflicts', ...range], queryFn: () => resourceApi.conflicts(range[0], range[1]) })
  const overloaded = (load.data ?? []).filter(item => item.loadStatus === 'OVERLOAD').length
  return <div className="resource-page">
    <div className="workshop-heading"><div><span className="eyebrow dark">RESOURCE COMMAND CENTER</span><Typography.Title level={2}>资源中心</Typography.Title><Typography.Paragraph>用结构化技能和真实投入比例排产，在冲突影响交付前完成调度。</Typography.Paragraph></div>
      <Space><DatePicker.RangePicker value={[dayjs(range[0]), dayjs(range[1])]} onChange={dates => dates?.[0] && dates[1] && setRange([dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')])} /><Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setAssignOpen(true)}>新建分配</Button></Space></div>
    <Row gutter={12} className="resource-kpis"><Col span={6}><Card><Statistic title="在岗人员" value={team.data?.length ?? 0} suffix="人" prefix={<TeamOutlined />} /></Card></Col><Col span={6}><Card><Statistic title="有效分配" value={(assignments.data ?? []).filter(item => item.status === 'ACTIVE').length} suffix="项" prefix={<ScheduleOutlined />} /></Card></Col><Col span={6}><Card><Statistic title="超载人员" value={overloaded} suffix="人" prefix={<ThunderboltOutlined />} valueStyle={{ color: overloaded ? '#f54a45' : '#1f2329' }} /></Card></Col><Col span={6}><Card><Statistic title="排期冲突" value={conflicts.data?.length ?? 0} suffix="处" prefix={<AlertOutlined />} valueStyle={{ color: conflicts.data?.length ? '#f54a45' : '#1f2329' }} /></Card></Col></Row>
    <Tabs className="resource-tabs" items={[
      { key: 'team', label: '团队能力', children: <TeamView values={team.data ?? []} onEdit={setProfile} /> },
      { key: 'schedule', label: '项目排期', children: <ScheduleView values={assignments.data ?? []} /> },
      { key: 'load', label: '负载分析', children: <LoadView values={load.data ?? []} /> },
      { key: 'conflicts', label: <span>冲突预警{Boolean(conflicts.data?.length) && <b>{conflicts.data?.length}</b>}</span>, children: <ConflictView values={conflicts.data ?? []} /> },
    ]} />
    <ProfileEditor value={profile} onClose={() => setProfile(undefined)} />
    <AssignmentEditor open={assignOpen} team={team.data ?? []} onClose={() => setAssignOpen(false)} />
  </div>
}

function TeamView({ values, onEdit }: { values: TeamMember[]; onEdit(value: TeamMember): void }) {
  const skills = Array.from(new Set(values.flatMap(item => item.skills.map(skill => skill.name))))
  return <Card className="resource-surface" title={<Space><TeamOutlined />人员与技能矩阵</Space>} extra={<span className="muted">熟练度 1-5</span>}><Table rowKey="userId" dataSource={values} pagination={false} scroll={{ x: 900 }} columns={[
    { title: '人员', fixed: 'left', width: 230, render: (_, row) => <button className="member-cell" onClick={() => onEdit(row)}><Avatar>{row.displayName.slice(0, 1)}</Avatar><span><strong>{row.displayName}</strong><small>{row.jobTitle} · {row.location}</small></span></button> },
    { title: '产能', width: 90, dataIndex: 'weeklyCapacityHours', render: value => `${value}h/周` },
    ...skills.map(name => ({ title: name, width: 110, render: (_: unknown, row: TeamMember) => { const skill = row.skills.find(item => item.name === name); return skill ? <div className="skill-level"><span>{'●'.repeat(skill.proficiency)}</span><small>{skill.certified ? '已认证' : `${skill.experienceMonths}月`}</small></div> : <span className="muted">-</span> } })),
  ]} /></Card>
}

function ScheduleView({ values }: { values: Assignment[] }) {
  const projectColors = ['#3370ff', '#2ea869', '#8f5bd7', '#f5a623']
  return <Card className="resource-surface" title={<Space><CalendarOutlined />人员项目排期</Space>} extra="保留全部分配记录便于追溯"><div className="schedule-board"><div className="schedule-header"><span>人员 / 项目</span>{['第1周', '第2周', '第3周', '第4周'].map(week => <b key={week}>{week}</b>)}</div>{values.map((item, index) => <div className="schedule-row" key={item.id}><div><strong>{item.displayName}</strong><span>{item.projectCode} · {item.role}</span></div><div className="schedule-track"><span style={{ left: `${(index * 13) % 34}%`, width: `${Math.max(28, item.allocationPercent / 1.5)}%`, background: projectColors[index % projectColors.length] }}>{item.projectName}<b>{item.allocationPercent}%</b></span></div></div>)}</div>
    <Table className="numeric-table" rowKey="id" dataSource={values} pagination={false} columns={[{ title: '人员', dataIndex: 'displayName' }, { title: '项目', render: (_, row) => `${row.projectCode} · ${row.projectName}` }, { title: '角色', dataIndex: 'role' }, { title: '日期', render: (_, row) => `${row.startDate} — ${row.endDate}` }, { title: '投入', dataIndex: 'allocationPercent', render: value => `${value}%` }]} /></Card>
}

function LoadView({ values }: { values: ResourceLoad[] }) {
  return <div><Alert className="load-alert" showIcon type="info" message="负载是查询周期内所有有效项目投入比例之和；超过 100% 为超载。" /><Row gutter={[12, 12]}>{values.map(item => { const meta=loadMeta[item.loadStatus]; return <Col span={8} key={item.userId}><Card className={`load-card load-${item.loadStatus.toLowerCase()}`}><div><Avatar>{item.displayName.slice(0,1)}</Avatar><span><strong>{item.displayName}</strong><small>{item.jobTitle}</small></span><Tag color={meta.tag}>{meta.label}</Tag></div><Progress percent={item.allocationPercent} success={{ percent: Math.min(item.allocationPercent, 80) }} strokeColor={meta.color} format={value => `${value}%`} /><footer><span>周产能 {item.weeklyCapacityHours}h</span><span>可用 {item.availablePercent}%</span></footer></Card></Col>})}</Row>
    <Card className="load-table-card" title="负载数字表"><Table rowKey="userId" dataSource={values} pagination={false} columns={[{ title: '人员', dataIndex: 'displayName' }, { title: '职位', dataIndex: 'jobTitle' }, { title: '周产能', dataIndex: 'weeklyCapacityHours', render: value => `${value}h` }, { title: '已分配', dataIndex: 'allocationPercent', render: value => `${value}%` }, { title: '可用', dataIndex: 'availablePercent', render: value => `${value}%` }, { title: '状态', dataIndex: 'loadStatus', render: value => <Tag color={loadMeta[value as keyof typeof loadMeta].tag}>{loadMeta[value as keyof typeof loadMeta].label}</Tag> }]} /></Card></div>
}

function ConflictView({ values }: { values: ResourceConflict[] }) {
  return <div>{!values.length ? <Card><Empty description="当前时间段无排期冲突" /></Card> : <Row gutter={[12,12]}>{values.map((item,index) => <Col span={12} key={`${item.user_id}-${index}`}><Card className="conflict-card"><div className="conflict-head"><AlertOutlined /><div><strong>{item.display_name} 负载达 {item.total_allocation}%</strong><span>两项分配在时间上重叠</span></div><Tag color="red">需调度</Tag></div><div className="conflict-projects"><div><b>{item.first_project_code}</b><span>{item.first_project_name}</span><strong>{item.first_allocation}%</strong></div><div><b>{item.second_project_code}</b><span>{item.second_project_name}</span><strong>{item.second_allocation}%</strong></div></div><Button block>调整资源分配</Button></Card></Col>)}</Row>}</div>
}

function ProfileEditor({ value, onClose }: { value?: TeamMember; onClose(): void }) {
  const [profileForm] = Form.useForm()
  const [skillForm] = Form.useForm()
  const client = useQueryClient()
  const catalog = useQuery({ queryKey: ['resource-skills'], queryFn: resourceApi.skills, enabled: Boolean(value) })
  useEffect(() => { if (value) profileForm.setFieldsValue({ jobTitle: value.jobTitle, location: value.location, weeklyCapacityHours: value.weeklyCapacityHours, resourceStatus: value.resourceStatus }) }, [profileForm, value])
  const saveProfile = useMutation({ mutationFn: (input: Record<string,unknown>) => resourceApi.saveProfile(value!.userId,input), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['resource-team'] }); message.success('人员档案已保存') } })
  const saveSkill = useMutation({ mutationFn: (input: Record<string,unknown>) => resourceApi.saveSkill(value!.userId,input), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['resource-team'] }); skillForm.resetFields(); message.success('技能已更新') } })
  return <Drawer width={580} title={value ? `${value.displayName} · 能力档案` : ''} open={Boolean(value)} onClose={onClose}>{value && <><Typography.Title level={5}>基本档案</Typography.Title><Form form={profileForm} layout="vertical" onFinish={saveProfile.mutate}><Row gutter={12}><Col span={12}><Form.Item name="jobTitle" label="职位" rules={[{required:true}]}><Input /></Form.Item></Col><Col span={12}><Form.Item name="location" label="地点"><Input prefix={<EnvironmentOutlined />} /></Form.Item></Col><Col span={12}><Form.Item name="weeklyCapacityHours" label="周产能"><InputNumber min={1} max={80} style={{width:'100%'}} /></Form.Item></Col><Col span={12}><Form.Item name="resourceStatus" label="状态"><Select options={['ACTIVE','LEAVE'].map(v=>({value:v,label:v}))} /></Form.Item></Col></Row><Button type="primary" htmlType="submit" loading={saveProfile.isPending}>保存档案</Button></Form><Typography.Title level={5} className="skill-editor-title">添加 / 更新技能</Typography.Title><Form form={skillForm} layout="vertical" initialValues={{proficiency:3,experienceMonths:12,certified:false}} onFinish={saveSkill.mutate}><Form.Item name="skillId" label="技能" rules={[{required:true}]}><Select options={catalog.data?.map((item:SkillCatalogItem)=>({value:item.id,label:`${item.name} · ${item.category}`}))} /></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="proficiency" label="熟练度 1-5"><InputNumber min={1} max={5} style={{width:'100%'}} /></Form.Item></Col><Col span={12}><Form.Item name="experienceMonths" label="经验月数"><InputNumber min={0} style={{width:'100%'}} /></Form.Item></Col></Row><Form.Item name="certified" label="认证"><Select options={[{value:true,label:'已认证'},{value:false,label:'未认证'}]} /></Form.Item><Button htmlType="submit" loading={saveSkill.isPending}>保存技能</Button></Form></>}</Drawer>
}

function AssignmentEditor({ open, team, onClose }: { open: boolean; team: TeamMember[]; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const projects = useQuery({ queryKey: ['projects-resource'], queryFn: projectApi.list, enabled: open })
  const create = useMutation({ mutationFn: (values: Record<string,unknown> & { dates: [Dayjs,Dayjs] }) => { const {dates,...input}=values; return resourceApi.assign({...input,startDate:dates[0].format('YYYY-MM-DD'),endDate:dates[1].format('YYYY-MM-DD')}) }, onSuccess: async () => { await Promise.all([client.invalidateQueries({queryKey:['resource-assignments']}),client.invalidateQueries({queryKey:['resource-load']}),client.invalidateQueries({queryKey:['resource-conflicts']})]);form.resetFields();onClose();message.success('资源分配已创建') } })
  return <Drawer width={560} title="新建资源分配" open={open} onClose={onClose} extra={<Button type="primary" loading={create.isPending} onClick={()=>form.submit()}>保存分配</Button>}><Alert className="drawer-alert" showIcon type="info" message="重叠排期可以保存；总投入超过 100% 后会立即进入冲突预警。" /><Form form={form} layout="vertical" initialValues={{allocationPercent:100}} onFinish={create.mutate}><Form.Item name="userId" label="人员" rules={[{required:true}]}><Select options={team.map(item=>({value:item.userId,label:`${item.displayName} · ${item.jobTitle}`}))} /></Form.Item><Form.Item name="projectId" label="项目" rules={[{required:true}]}><Select options={projects.data?.map(item=>({value:item.id,label:`${item.code} · ${item.name}`}))} /></Form.Item><Form.Item name="role" label="项目角色" rules={[{required:true}]}><Input /></Form.Item><Form.Item name="dates" label="分配日期" rules={[{required:true}]}><DatePicker.RangePicker style={{width:'100%'}} /></Form.Item><Form.Item name="allocationPercent" label="投入比例"><InputNumber min={1} max={100} addonAfter="%" style={{width:'100%'}} /></Form.Item></Form></Drawer>
}
