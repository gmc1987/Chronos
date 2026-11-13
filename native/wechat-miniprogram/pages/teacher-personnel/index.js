const api = require('../../services/request')
const dataUtils = require('../../utils/data')

const tabs = [
  { key: 'initiated', title: '我的申请', path: '/portal/education/teacher-personnel/mine' },
  { key: 'start', title: '发起申请', path: '/workflows/available' },
  { key: 'pending', title: '审批待办', path: '/workflow-tasks/pending?page=0&size=30' }
]

Page({
  data: { tabs, active: 'initiated', loading: true, records: [] },
  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  switchTab(event) { this.setData({ active: event.currentTarget.dataset.key }, () => this.load()) },
  async load() {
    this.setData({ loading: true })
    const tab = tabs.find(value => value.key === this.data.active)
    try {
      const result = await api.get(tab.path)
      const rows = dataUtils.rows(result)
      const filtered = tab.key === 'start' ? rows.filter(item => String(item.flowCode || '').startsWith('EDU_TEACHER_PERSONNEL_')) : rows
      if (this.data.active !== tab.key) return
      this.setData({ loading: false, records: filtered.map(item => ({ raw: item, entries: dataUtils.displayEntries(item) })) })
    } catch { this.setData({ loading: false, records: [] }) }
  },
  open(event) {
    const item = event.currentTarget.dataset.item
    if (this.data.active === 'start') {
      return wx.navigateTo({ url: `/pages/workflow-start/index?id=${encodeURIComponent(item.id)}&title=${encodeURIComponent(item.flowName || item.name || '发起流程')}` })
    }
    const id = item.instanceId || item.processInstanceId || item.id
    wx.navigateTo({ url: `/pages/workflow-detail/index?id=${encodeURIComponent(id)}` })
  }
})
