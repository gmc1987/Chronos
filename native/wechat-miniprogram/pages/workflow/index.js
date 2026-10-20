const api = require('../../services/request')
const dataUtils = require('../../utils/data')

const tabs = [
  { key: 'pending', title: '待办', path: '/workflow-tasks/pending?page=0&size=30' },
  { key: 'handled', title: '已办', path: '/workflow-tasks/handled?page=0&size=30' },
  { key: 'initiated', title: '我发起', path: '/workflow-instances/initiated?page=0&size=30' },
  { key: 'start', title: '发起', path: '/workflows/available' }
]

Page({
  data: { tabs, active: 'pending', loading: true, records: [] },
  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  switchTab(event) { this.setData({ active: event.currentTarget.dataset.key }, () => this.load()) },
  async load() {
    this.setData({ loading: true })
    const tab = tabs.find(value => value.key === this.data.active)
    try {
      const result = await api.get(tab.path)
      this.setData({ loading: false, records: dataUtils.rows(result).map(item => ({ raw: item, entries: dataUtils.displayEntries(item) })) })
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
