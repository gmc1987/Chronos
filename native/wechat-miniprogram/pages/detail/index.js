const api = require('../../services/request')
const dataUtils = require('../../utils/data')
Page({
  data: { title: '详情', kind: '', record: {}, entries: [], reason: '', actualEndDate: '', processing: false },
  onLoad(options) {
    const title = decodeURIComponent(options.title || '详情')
    const record = dataUtils.decode(options.record)
    wx.setNavigationBarTitle({ title })
    const date = new Date(); const offset = date.getTimezoneOffset() * 60 * 1000
    this.setData({ title, kind: options.kind || '', record, entries: dataUtils.displayEntries(record), actualEndDate: new Date(date.getTime() - offset).toISOString().slice(0, 10) })
  },
  input(event) { this.setData({ [event.currentTarget.dataset.key]: event.detail.value }) },
  async perform(path, body, success) {
    if (this.data.processing || !this.data.record.id) return
    this.setData({ processing: true })
    try {
      await api.post(path, body)
      wx.showToast({ title: success, icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } finally { this.setData({ processing: false }) }
  },
  withdraw() {
    const roots = { leave: '/portal/education/leaves', trip: '/portal/collaboration/business-trips', office: '/portal/collaboration/requests' }
    const root = roots[this.data.kind]
    if (root) this.perform(`${root}/${this.data.record.id}/withdraw`, this.data.kind === 'office' ? {} : { reason: this.data.reason }, '已撤回')
  },
  cancelTrip() {
    if (!this.data.reason.trim()) return wx.showToast({ title: '请填写取消原因', icon: 'none' })
    this.perform(`/portal/collaboration/business-trips/${this.data.record.id}/cancel`, { reason: this.data.reason }, '已取消')
  },
  cancelLeave() {
    if (!this.data.reason.trim()) return wx.showToast({ title: '请填写销假原因', icon: 'none' })
    this.perform(`/portal/education/leaves/${this.data.record.id}/cancellation`, { reason: this.data.reason, actualEndDate: this.data.actualEndDate }, '销假申请已提交')
  }
})
