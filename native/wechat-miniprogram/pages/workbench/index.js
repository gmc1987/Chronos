const api = require('../../services/request')
const dataUtils = require('../../utils/data')

function date(value) {
  const offset = value.getTimezoneOffset() * 60 * 1000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}

Page({
  data: { loading: true, bootstrap: null, user: {}, education: {}, workspace: 'STAFF', schedule: [], cards: [] },
  onShow() { this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },

  async load() {
    this.setData({ loading: true })
    try {
      const bootstrap = await api.get('/portal/bootstrap')
      wx.setStorageSync('chronosBootstrap', bootstrap)
      const user = bootstrap?.user || {}
      const education = bootstrap?.contributions?.DATA?.data || {}
      const workspace = this.classify(education.profileTypes || [], user.roles || [])
      let schedule = []
      if (workspace === 'STUDENT' || workspace === 'TEACHER') {
        const now = new Date()
        const start = new Date(now); start.setDate(now.getDate() - ((now.getDay() + 6) % 7))
        const end = new Date(start); end.setDate(start.getDate() + 6)
        try {
          const result = await api.get(`/portal/education/schedule/calendar?startDate=${date(start)}&endDate=${date(end)}`)
          schedule = dataUtils.rows(result).slice(0, 8)
        } catch { schedule = [] }
      }
      this.setData({
        loading: false, bootstrap, user, education, workspace, schedule,
        cards: this.cards(workspace, bootstrap)
      })
    } catch { this.setData({ loading: false }) }
  },

  classify(types, roles) {
    if (types.includes('TEACHER')) return 'TEACHER'
    if (types.includes('STUDENT')) return 'STUDENT'
    if (types.includes('PARENT')) return 'PARENT'
    if (roles.some(value => /管理员|领导|ADMIN|MANAGER/.test(value))) return 'MANAGEMENT'
    return 'STAFF'
  },

  cards(workspace, bootstrap) {
    const values = [
      { title: '我的待办', detail: '查看需要处理的审批任务', path: '/workflow-tasks/pending?page=0&size=20' },
      { title: '近期会议', detail: '查看会议安排并确认参会', path: '/portal/education/meetings' },
      { title: '通知公告', detail: '查看学校发布的最新信息', path: '/portal/publications?page=0&size=20' }
    ]
    if (workspace === 'PARENT') {
      values.unshift({ title: '家校通知', detail: '查看与孩子有关的班级通知', path: '/portal/education/family/notices' })
      values.push({ title: '成绩信息', detail: '查看孩子的成绩与学业情况', path: '/portal/education/family/grades' })
    }
    if (workspace === 'STUDENT') values.push({ title: '我的成绩', detail: '查看成绩与考试结果', path: '/portal/education/grades' })
    return values.map(item => ({ ...item, icon: item.title.slice(0, 1) }))
  },

  openCard(event) {
    const item = event.currentTarget.dataset.item
    wx.navigateTo({ url: `/pages/records/index?title=${encodeURIComponent(item.title)}&path=${encodeURIComponent(item.path)}` })
  },

  openSchedule(event) {
    wx.navigateTo({ url: `/pages/detail/index?title=${encodeURIComponent('课程详情')}&record=${dataUtils.encode(event.currentTarget.dataset.item)}` })
  }
})
