const api = require('../../services/request')
const dataUtils = require('../../utils/data')

function isoDate(value) {
  const offset = value.getTimezoneOffset() * 60 * 1000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}

function weekStart(offset = 0) {
  const value = new Date()
  value.setHours(12, 0, 0, 0)
  value.setDate(value.getDate() - ((value.getDay() + 6) % 7) + offset * 7)
  return value
}

function addDays(value, days) {
  const result = new Date(value)
  result.setDate(result.getDate() + days)
  return result
}

function statusText(status) {
  return {
    CANCELLED: '停课', MOVED_OUT: '已调出', MOVED_IN: '调入',
    SUBSTITUTED: '代课', MAKEUP: '补课', SCHEDULED: '正常'
  }[status] || '正常'
}

function timeText(occurrence) {
  if (!occurrence.startTime) return `第 ${occurrence.effectivePeriodNo || '-'} 节`
  return occurrence.endTime
    ? `${occurrence.startTime}-${occurrence.endTime}`
    : occurrence.startTime
}

function calendarView(calendar, start) {
  const today = isoDate(new Date())
  const days = Array.from({ length: 7 }, (_, index) => {
    const value = isoDate(addDays(start, index))
    return {
      date: value,
      label: `周${'一二三四五六日'[index]}`,
      shortDate: value.slice(5),
      today: value === today
    }
  })
  const occurrences = Array.isArray(calendar?.occurrences)
    ? calendar.occurrences.map(item => {
      const entry = item.entry || {}
      return {
        ...item,
        entry,
        courseName: entry.courseName || '课程',
        classroomName: entry.classroomName || '教室待定',
        displayStatus: statusText(item.occurrenceStatus),
        timeText: timeText(item)
      }
    })
    : []
  occurrences.sort((left, right) => left.date.localeCompare(right.date)
    || Number(left.effectivePeriodNo || 0) - Number(right.effectivePeriodNo || 0))
  const periods = [...new Set(occurrences
    .map(item => Number(item.effectivePeriodNo || 0))
    .filter(value => value > 0))].sort((left, right) => left - right)
  const rows = periods.map(periodNo => {
    const timed = occurrences.find(item => Number(item.effectivePeriodNo) === periodNo)
    return {
      periodNo,
      timeText: timed ? timed.timeText : '',
      cells: days.map(day => {
        const lesson = occurrences.find(item => item.date === day.date
          && Number(item.effectivePeriodNo) === periodNo)
        return lesson
          ? { ...lesson, key: `${day.date}-${periodNo}`, hasLesson: true, today: day.today }
          : { key: `${day.date}-${periodNo}`, hasLesson: false, today: day.today }
      })
    }
  })
  return { days, rows, occurrences }
}

Page({
  data: {
    loading: true,
    bootstrap: null,
    user: {},
    education: {},
    workspace: 'STAFF',
    cards: [],
    weekOffset: 0,
    weekStart: '',
    weekEnd: '',
    scheduleState: 'idle',
    scheduleMessage: '',
    scheduleDays: [],
    scheduleRows: [],
    scheduleOccurrences: []
  },

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
      this.setData({
        loading: false,
        bootstrap,
        user,
        education,
        workspace,
        cards: this.cards(workspace)
      })
      if (workspace === 'STUDENT' || workspace === 'TEACHER') {
        await this.loadSchedule()
      } else {
        this.setData({ scheduleState: 'idle', scheduleRows: [], scheduleOccurrences: [] })
      }
    } catch (error) {
      this.setData({ loading: false })
    }
  },

  async loadSchedule() {
    const start = weekStart(this.data.weekOffset)
    const end = addDays(start, 6)
    const startDate = isoDate(start)
    const endDate = isoDate(end)
    this.setData({
      scheduleState: 'loading',
      scheduleMessage: '',
      weekStart: startDate,
      weekEnd: endDate
    })
    try {
      const calendar = await api.get(`/portal/education/schedule/calendar?startDate=${startDate}&endDate=${endDate}`)
      const view = calendarView(calendar, start)
      this.setData({
        scheduleState: 'loaded',
        scheduleDays: view.days,
        scheduleRows: view.rows,
        scheduleOccurrences: view.occurrences
      })
    } catch (error) {
      this.setData({
        scheduleState: 'error',
        scheduleMessage: error.message || '个人课表加载失败',
        scheduleRows: [],
        scheduleOccurrences: []
      })
    }
  },

  classify(types, roles) {
    if (types.includes('TEACHER')) return 'TEACHER'
    if (types.includes('STUDENT')) return 'STUDENT'
    if (types.includes('PARENT')) return 'PARENT'
    if (roles.some(value => /管理员|领导|ADMIN|MANAGER/.test(value))) return 'MANAGEMENT'
    return 'STAFF'
  },

  cards(workspace) {
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

  changeWeek(event) {
    const weekOffset = this.data.weekOffset + Number(event.currentTarget.dataset.delta || 0)
    this.setData({ weekOffset }, () => this.loadSchedule())
  },

  currentWeek() {
    if (this.data.weekOffset === 0) return
    this.setData({ weekOffset: 0 }, () => this.loadSchedule())
  },

  retrySchedule() { this.loadSchedule() },

  openCard(event) {
    const item = event.currentTarget.dataset.item
    wx.navigateTo({ url: `/pages/records/index?title=${encodeURIComponent(item.title)}&path=${encodeURIComponent(item.path)}` })
  },

  openSchedule(event) {
    const item = event.currentTarget.dataset.item
    if (!item?.hasLesson) return
    wx.navigateTo({ url: `/pages/course-detail/index?record=${dataUtils.encode(item)}` })
  }
})
