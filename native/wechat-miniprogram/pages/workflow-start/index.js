const api = require('../../services/request')

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, value => {
    const random = Math.random() * 16 | 0
    return (value === 'x' ? random : (random & 3 | 8)).toString(16)
  })
}

Page({
  data: { id: '', title: '发起流程', loading: true, fields: [], values: {}, submitting: false },
  onLoad(options) {
    const title = decodeURIComponent(options.title || '发起流程')
    wx.setNavigationBarTitle({ title })
    this.setData({ id: options.id, title })
    this.load()
  },
  async load() {
    try {
      const schema = await api.get(`/workflows/${this.data.id}/start-form`)
      const fields = (schema?.fields || []).map(field => ({
        ...field,
        fieldType: (field.fieldType || 'TEXT').toUpperCase(),
        options: this.options(field.optionsJson)
      }))
      this.setData({ loading: false, title: schema.flowName || this.data.title, fields })
      wx.setNavigationBarTitle({ title: schema.flowName || this.data.title })
    } catch { this.setData({ loading: false }) }
  },
  options(raw) { try { return typeof raw === 'string' ? JSON.parse(raw) : (raw || []) } catch { return [] } },
  input(event) {
    const key = event.currentTarget.dataset.key
    this.setData({ [`values.${key}`]: event.detail.value })
  },
  choose(event) {
    const key = event.currentTarget.dataset.key
    const field = this.data.fields.find(value => value.fieldKey === key)
    const option = field?.options?.[Number(event.detail.value)]
    this.setData({ [`values.${key}`]: option?.value ?? '' })
  },
  async submit() {
    for (const field of this.data.fields) {
      if (field.required && (this.data.values[field.fieldKey] === undefined || this.data.values[field.fieldKey] === '')) {
        return wx.showToast({ title: `请填写${field.fieldLabel}`, icon: 'none' })
      }
    }
    this.setData({ submitting: true })
    try {
      await api.post(`/workflows/${this.data.id}/start`, {
        idempotencyKey: uuid(),
        formData: this.data.values,
        variablesJson: JSON.stringify(this.data.values)
      })
      wx.showToast({ title: '申请已提交', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 700)
    } finally { this.setData({ submitting: false }) }
  }
})
