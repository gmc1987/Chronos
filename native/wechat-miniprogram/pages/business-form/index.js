const api = require('../../services/request')

const today = () => {
  const date = new Date()
  const offset = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

Page({
  data: {
    type: 'leave', title: '发起申请', submitting: false,
    leaveTypes: [{ label: '事假', value: 'PERSONAL' }, { label: '病假', value: 'SICK' }, { label: '年假', value: 'ANNUAL' }], leaveTypeIndex: 0,
    requestTypes: [{ label: '用车', value: 'VEHICLE' }, { label: '用印', value: 'SEAL' }], requestTypeIndex: 0,
    resources: [], resourceIndex: 0,
    form: { startDate: today(), endDate: today(), transportAmount: '0', accommodationAmount: '0', mealAmount: '0', otherAmount: '0', quantity: '1' }
  },
  onLoad(options) {
    const titles = { leave: '发起请假', trip: '发起出差', office: '发起用车/用印' }
    const title = titles[options.type] || '发起申请'
    this.setData({ type: options.type, title }); wx.setNavigationBarTitle({ title })
    if (options.type === 'office') this.loadResources()
  },
  input(event) { this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value }) },
  chooseLeaveType(event) { this.setData({ leaveTypeIndex: Number(event.detail.value) }) },
  chooseRequestType(event) { this.setData({ requestTypeIndex: Number(event.detail.value), resourceIndex: 0 }, () => this.loadResources()) },
  chooseResource(event) { this.setData({ resourceIndex: Number(event.detail.value) }) },
  async loadResources() {
    const type = this.data.requestTypes[this.data.requestTypeIndex].value
    try {
      const result = await api.get(`/portal/collaboration/resources?type=${type}`)
      const resources = Array.isArray(result) ? result : (result?.content || result?.items || [])
      this.setData({ resources })
    } catch { this.setData({ resources: [] }) }
  },
  async submit() {
    let path = ''; let body = {}
    const form = this.data.form
    if (form.endDate < form.startDate) return wx.showToast({ title: '结束日期不能早于开始日期', icon: 'none' })
    if (this.data.type === 'leave') {
      if (!form.reason) return wx.showToast({ title: '请填写请假原因', icon: 'none' })
      path = '/portal/education/leaves'; body = { leaveType: this.data.leaveTypes[this.data.leaveTypeIndex].value, startDate: form.startDate, endDate: form.endDate, reason: form.reason }
    } else if (this.data.type === 'trip') {
      if (!form.destination || !form.purpose) return wx.showToast({ title: '请填写出差地点和事由', icon: 'none' })
      path = '/portal/collaboration/business-trips'; body = {
        destination: form.destination, startDate: form.startDate, endDate: form.endDate, purpose: form.purpose, currency: 'CNY',
        transportAmount: Number(form.transportAmount || 0), accommodationAmount: Number(form.accommodationAmount || 0),
        mealAmount: Number(form.mealAmount || 0), otherAmount: Number(form.otherAmount || 0)
      }
    } else {
      const requestType = this.data.requestTypes[this.data.requestTypeIndex].value
      const resource = this.data.resources[this.data.resourceIndex]
      if (!resource || !form.purpose) return wx.showToast({ title: '请选择资源并填写申请事由', icon: 'none' })
      path = '/portal/collaboration/requests'; body = {
        requestType, resourceId: resource.id, startAt: `${form.startDate}T09:00:00`, endAt: `${form.endDate}T18:00:00`, purpose: form.purpose,
        destination: requestType === 'VEHICLE' ? form.name : null, passengerCount: requestType === 'VEHICLE' ? Number(form.quantity || 1) : null,
        documentTitle: requestType === 'SEAL' ? form.name : null, copyCount: requestType === 'SEAL' ? Number(form.quantity || 1) : null
      }
      if (!form.name) return wx.showToast({ title: requestType === 'VEHICLE' ? '请填写目的地' : '请填写文件名称', icon: 'none' })
    }
    this.setData({ submitting: true })
    try {
      await api.post(path, body); wx.showToast({ title: '申请已提交', icon: 'success' }); setTimeout(() => wx.navigateBack(), 600)
    } finally { this.setData({ submitting: false }) }
  }
})
