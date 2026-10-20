const api = require('../../services/request')
const dataUtils = require('../../utils/data')

function options(raw) {
  try { return typeof raw === 'string' ? JSON.parse(raw) : (raw || []) } catch (_) { return [] }
}

function fieldView(field, values) {
  const choices = options(field.optionsJson)
  const value = values?.[field.fieldKey] ?? ''
  const choice = choices.find(item => String(item.value) === String(value))
  return {
    ...field,
    fieldType: (field.fieldType || 'TEXT').toUpperCase(),
    value,
    options: choices,
    displayValue: choice?.label || dataUtils.format(value, field.fieldKey),
    editable: field.permission === 'EDIT'
  }
}

Page({
  data: {
    id: '', loading: true, title: '流程详情', sections: [], history: [], task: null,
    comment: '', claimableId: '', operations: {}, submitting: false
  },
  onLoad(optionsValue) { this.setData({ id: optionsValue.id }); this.load() },
  async load() {
    try {
      const bundle = await api.get(`/workflow-instances/${this.data.id}/forms`)
      const sections = (bundle.forms || []).map(form => {
        const values = form.data || {}
        return {
          id: form.formId,
          title: form.formName || '申请信息',
          fields: (form.fields || []).map(field => fieldView(field, values)),
          entries: (form.fields || []).length ? [] : dataUtils.displayEntries(values)
        }
      })
      const history = (bundle.history || []).map(item => ({ entries: dataUtils.displayEntries(item) }))
      const claimable = (bundle.history || []).find(item => item.status === 'CLAIMABLE')
      const title = bundle.flow?.flowName || '流程详情'
      wx.setNavigationBarTitle({ title })
      this.setData({
        loading: false, title, sections, history,
        task: bundle.currentTask || null,
        operations: bundle.operations || {},
        claimableId: claimable?.id || ''
      })
    } catch (_) { this.setData({ loading: false }) }
  },
  inputComment(event) { this.setData({ comment: event.detail.value }) },
  inputField(event) {
    const section = Number(event.currentTarget.dataset.section)
    const field = Number(event.currentTarget.dataset.field)
    this.setData({ [`sections[${section}].fields[${field}].value`]: event.detail.value })
  },
  chooseField(event) {
    const section = Number(event.currentTarget.dataset.section)
    const field = Number(event.currentTarget.dataset.field)
    const current = this.data.sections[section].fields[field]
    const selected = current.options[Number(event.detail.value)]
    this.setData({
      [`sections[${section}].fields[${field}].value`]: selected?.value ?? '',
      [`sections[${section}].fields[${field}].displayValue`]: selected?.label || ''
    })
  },
  async claim() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/workflow-tasks/${this.data.claimableId}/claim`, {})
      wx.showToast({ title: '任务已认领', icon: 'success' })
      await this.load()
    } finally { this.setData({ submitting: false }) }
  },
  async saveEditableForms() {
    for (const section of this.data.sections) {
      if (!section.id) continue
      const editable = section.fields.filter(field => field.editable)
      if (!editable.length) continue
      const missing = editable.find(field => field.required && (field.value === '' || field.value === null || field.value === undefined))
      if (missing) throw new Error(`请填写${missing.fieldLabel}`)
      const values = {}
      editable.forEach(field => { values[field.fieldKey] = field.value })
      await api.put(`/workflow-instances/${this.data.id}/forms/${section.id}`, {
        data: values, draft: false
      })
    }
  },
  async act(event) {
    if (this.data.submitting) return
    const action = event.currentTarget.dataset.action
    const taskId = this.data.task?.id
    if (!taskId) return
    this.setData({ submitting: true })
    try {
      await this.saveEditableForms()
      const operation = action === 'approve' ? 'complete' : action
      const body = { comment: this.data.comment }
      if (action === 'approve') body.approved = true
      await api.post(`/workflow-tasks/${taskId}/${operation}`, body)
      wx.showToast({ title: action === 'reject' ? '已驳回' : '处理完成', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (error) {
      if (error.statusCode === undefined) wx.showToast({ title: error.message || '提交失败', icon: 'none' })
    } finally { this.setData({ submitting: false }) }
  }
})
