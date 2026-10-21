const dataUtils = require('../../utils/data')

function value(text) {
  return text === null || text === undefined || String(text).trim() === '' ? '待定' : String(text)
}

function statusText(status) {
  return {
    CANCELLED: '停课', MOVED_OUT: '已调出', MOVED_IN: '调入',
    SUBSTITUTED: '代课', MAKEUP: '补课', SCHEDULED: '正常'
  }[status] || '正常'
}

Page({
  data: { title: '课程详情', rows: [], reason: '' },

  onLoad(options) {
    const occurrence = dataUtils.decode(options.record)
    const entry = occurrence.entry || {}
    const time = occurrence.startTime
      ? `${occurrence.startTime}${occurrence.endTime ? ` - ${occurrence.endTime}` : ''}（第 ${occurrence.effectivePeriodNo || '-'} 节）`
      : `第 ${occurrence.effectivePeriodNo || '-'} 节`
    this.setData({
      title: value(entry.courseName),
      rows: [
        { label: '课程', value: value(entry.courseName) },
        { label: '上课日期', value: value(occurrence.date) },
        { label: '上课时间', value: time },
        { label: '上课教师', value: value(occurrence.effectiveTeacherName) },
        { label: '任课老师', value: value(entry.teacherName) },
        { label: '教学班', value: value(entry.teachingClassName) },
        { label: '上课教室', value: value(entry.classroomName) },
        { label: '课程状态', value: statusText(occurrence.occurrenceStatus) }
      ],
      reason: occurrence.reason || ''
    })
  }
})
