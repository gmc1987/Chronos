const api = require('../../services/request')
const dataUtils = require('../../utils/data')

Page({
  data: { raw: {}, meetingId: '', entries: [], published: false, checkedIn: false, comment: '', processing: false },
  onLoad(options) { this.render(dataUtils.decode(options.record)) },
  render(raw) {
    const value = dataUtils.flatten(raw)
    this.setData({
      raw,
      meetingId: value.id || raw.id || raw.meeting?.id || '',
      entries: dataUtils.displayEntries(raw),
      published: value.status === 'PUBLISHED',
      checkedIn: Boolean(value.checkedInAt)
    })
  },
  inputComment(event) { this.setData({ comment: event.detail.value }) },
  async respond(event) {
    if (this.data.processing) return
    const status = event.currentTarget.dataset.status
    if (status === 'LEAVE' && !this.data.comment.trim()) {
      return wx.showToast({ title: '会议请假必须填写原因', icon: 'none' })
    }
    this.setData({ processing: true })
    try {
      const result = await api.post(`/portal/education/meetings/${this.data.meetingId}/response`, {
        status, comment: this.data.comment
      })
      this.render(result)
      wx.showToast({ title: '参会状态已更新', icon: 'success' })
    } finally { this.setData({ processing: false }) }
  },
  async checkIn() {
    if (this.data.processing || this.data.checkedIn) return
    this.setData({ processing: true })
    try {
      const result = await api.post(`/portal/education/meetings/${this.data.meetingId}/check-in`, {})
      this.render(result)
      wx.showToast({ title: '签到成功', icon: 'success' })
    } finally { this.setData({ processing: false }) }
  }
})
