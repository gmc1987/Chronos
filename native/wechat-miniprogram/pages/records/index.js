const api = require('../../services/request')
const dataUtils = require('../../utils/data')

Page({
  data: { title: '列表', path: '', form: '', kind: '', loading: true, records: [], page: 0, finished: false },
  onLoad(options) {
    const title = decodeURIComponent(options.title || '列表')
    const path = decodeURIComponent(options.path || '')
    wx.setNavigationBarTitle({ title })
    const form = options.form || ''
    const kind = path.includes('/portal/education/meetings') ? 'meeting' : form
    this.setData({ title, path, form, kind })
    this.load(true)
  },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (!this.data.finished) this.load(false) },
  async load(reset) {
    if (!this.data.path) return this.setData({ loading: false })
    const page = reset ? 0 : this.data.page + 1
    let path = this.data.path
    if (/([?&])page=\d+/.test(path)) path = path.replace(/([?&])page=\d+/, `$1page=${page}`)
    this.setData({ loading: reset })
    try {
      const result = await api.get(path)
      const values = dataUtils.rows(result).map(item => ({ raw: item, entries: dataUtils.displayEntries(item) }))
      this.setData({
        loading: false,
        page,
        records: reset ? values : this.data.records.concat(values),
        finished: values.length < 10 || !/([?&])page=/.test(path)
      })
    } catch { this.setData({ loading: false }) }
  },
  open(event) {
    const record = dataUtils.encode(event.currentTarget.dataset.item)
    if (this.data.kind === 'meeting') {
      return wx.navigateTo({ url: `/pages/meeting-detail/index?record=${record}` })
    }
    wx.navigateTo({ url: `/pages/detail/index?title=${encodeURIComponent(this.data.title)}&kind=${this.data.kind}&record=${record}` })
  },
  create() { wx.navigateTo({ url: `/pages/business-form/index?type=${this.data.form}` }) }
})
