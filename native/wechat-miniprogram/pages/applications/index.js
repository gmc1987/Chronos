const api = require('../../services/request')

const nativeModules = [
  { title: '流程中心', detail: '待办、已办、我发起和发起流程', kind: 'tab', target: '/pages/workflow/index', icon: '流' },
  { title: '公文中心', detail: '查看本人经办和签发的公文', kind: 'records', feature: 'DOCUMENTS', path: '/portal/collaboration/documents/my', icon: '文' },
  { title: '文件中心', detail: '查看授权共享文件', kind: 'records', feature: 'FILES', path: '/portal/collaboration/files/shares', icon: '件' },
  { title: '请假', detail: '发起请假并查看审批状态', kind: 'records', form: 'leave', path: '/portal/education/leaves', icon: '假' },
  { title: '出差', detail: '查看出差行程、预算和审批', kind: 'records', form: 'trip', path: '/portal/collaboration/business-trips', icon: '差' },
  { title: '用车与用印', detail: '发起申请并查看执行状态', kind: 'records', form: 'office', path: '/portal/collaboration/requests/my', icon: '印' },
  { title: '会议', detail: '查看会议安排和参会状态', kind: 'records', path: '/portal/education/meetings', icon: '会' },
  { title: '通讯录', detail: '分页查看学校教职工联系方式', kind: 'records', path: '/portal/collaboration/contacts?keyword=&page=0&size=10', icon: '录' },
  { title: '教学计划', detail: '查看授权范围内的教学计划', kind: 'records', path: '/education/teaching-center/api/plans?page=0&size=50', icon: '教' },
  { title: '教案', detail: '查看和管理课程教案', kind: 'records', path: '/education/teaching-center/api/lesson-plans?page=0&size=50', icon: '案' },
  { title: '课件', detail: '查看课程课件和最近更新', kind: 'records', path: '/education/teaching-center/api/coursewares?page=0&size=50', icon: '课' },
  { title: '题库', detail: '进入授权练习与题库', kind: 'records', path: '/education/teaching-center/api/questions?page=0&size=50', icon: '题' },
  { title: '知识中心', detail: '查看授权知识库和资料', kind: 'records', feature: 'KNOWLEDGE', path: '/admin/knowledge-bases?page=0&size=50', icon: '知' },
  { title: 'AI 智能中心', detail: '查看学校配置的智能体能力', kind: 'records', feature: 'AI_CENTER', path: '/agent/specs/list?page=0&size=50', icon: '智' },
  { title: '家校通知', detail: '查看班级和学校通知', kind: 'records', path: '/portal/education/family/notices', icon: '家' },
  { title: '成绩', detail: '查看本人或孩子的成绩', kind: 'records', path: '/portal/education/grades', icon: '分' }
]

Page({
  data: { loading: true, modules: nativeModules, applications: [] },
  onShow() { this.load() },
  async load() {
    try {
      const bootstrap = await api.get('/portal/bootstrap')
      const dynamic = (bootstrap?.applications || []).map(item => ({
        ...item,
        kind: 'dynamic',
        title: item.name || item.applicationName || '应用',
        detail: item.description || '学校授权应用',
        icon: (item.name || '应').slice(0, 1)
      }))
      this.setData({ loading: false, applications: dynamic })
    } catch { this.setData({ loading: false }) }
  },
  async open(event) {
    const item = event.currentTarget.dataset.item
    if (item.kind === 'tab') return wx.switchTab({ url: item.target })
    if (item.kind === 'records') return wx.navigateTo({ url: `/pages/records/index?title=${encodeURIComponent(item.title)}&path=${encodeURIComponent(item.path)}&form=${item.form || ''}` })
    const aliases = {
      WORKFLOW_TASKS: '流程中心', DOCUMENTS: '公文中心', FILES: '文件中心',
      MEETINGS: '会议', BUSINESS_TRIPS: '出差', KNOWLEDGE: '知识中心', AI_CENTER: 'AI 智能中心',
      TEACHING_CENTER: '教学计划'
    }
    const fallback = { workflow: '流程中心', oa: '请假', document: '公文中心', file: '文件中心',
      EDUCATION_MEETINGS: '会议', STAFF_BUSINESS_TRIP: '出差', knowledge: '知识中心', ai: 'AI 智能中心',
      'teaching-center': '教学计划' }
    if (item.nativeFeatureCode === 'COLLABORATION' || item.code === 'oa') {
      return wx.navigateTo({ url: '/pages/module-center/index?type=collaboration' })
    }
    if (item.nativeFeatureCode === 'TEACHING_CENTER' || item.code === 'teaching-center') {
      return wx.navigateTo({ url: '/pages/module-center/index?type=teaching' })
    }
    const targetTitle = aliases[item.nativeFeatureCode] || fallback[item.code]
    const route = item.nativeRoute || item.routePath || ''
    const match = nativeModules.find(value => value.title === targetTitle || route.includes(value.path?.split('?')[0]) || item.title.includes(value.title))
    if (match) return this.open({ currentTarget: { dataset: { item: match } } })
    if (item.presentationMode === 'EXTERNAL' || item.openMode === 'EXTERNAL') {
      return wx.showToast({ title: '外部应用需由学校配置小程序 AppID 后打开', icon: 'none' })
    }
    wx.showToast({ title: '当前应用尚未配置小程序原生入口', icon: 'none' })
  }
})
