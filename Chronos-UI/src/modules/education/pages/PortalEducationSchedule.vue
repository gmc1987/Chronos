<template>
  <div class="portal-schedule">
    <el-page-header @back="$router.push('/portal')">
      <template #content>
        <div>
          <strong>我的课表</strong>
          <small>{{ termName || '当前学期' }}</small>
        </div>
      </template>
    </el-page-header>

    <el-button class="exam-duty-link" @click="$router.push('/portal/education/exam/invigilation')">
      查看我的监考
    </el-button>
    <el-button @click="$router.push('/portal/education/exam/my-exams')">查看我的考试</el-button>
    <el-button @click="$router.push('/portal/education/class-notices')">班级通知</el-button>
    <el-button @click="$router.push('/portal/education/leaves')">我的请假</el-button>
    <el-button @click="$router.push('/portal/education/classroom-reservations')">教室申请</el-button>
    <el-button
      v-if="scheduleData.headTeacherClassCount"
      @click="$router.push('/portal/education/head-teacher')"
    >
      班主任工作台
    </el-button>

    <el-alert
      title="仅展示教务处最近发布的课表版本；未发布的调整草稿不会出现在这里。"
      type="info"
      :closable="false" />
    <el-card shadow="never">
      <div class="week-toolbar">
        <div>
          <strong>调代课周历</strong>
          <small>{{ weekStart }} 至 {{ weekEnd }}</small>
        </div>
        <el-button-group>
          <el-button @click="moveWeek(-7)">上一周</el-button>
          <el-button @click="moveWeek(0)">本周</el-button>
          <el-button @click="moveWeek(7)">下一周</el-button>
        </el-button-group>
      </div>
      <el-skeleton v-if="calendarLoading" :rows="4" animated />
      <div v-else class="week-grid">
        <section
          v-for="day in weekDays"
          :key="day.date"
          :class="['week-day', { today: day.date === today }]"
        >
          <header>
            <strong>{{ day.weekday }}</strong>
            <span>{{ day.date.slice(5) }}</span>
          </header>
          <el-empty
            v-if="!calendarByDate[day.date]?.length"
            description="无课程"
            :image-size="34"
          />
          <article
            v-for="item in calendarByDate[day.date] || []"
            :key="item.occurrenceKey"
            :class="['lesson-card', statusClass(item.occurrenceStatus)]"
          >
            <div>
              <b>第 {{ item.effectivePeriodNo }} 节</b>
              <el-tag
                v-if="item.occurrenceStatus !== 'SCHEDULED'"
                size="small"
                :type="statusTagType(item.occurrenceStatus)"
              >
                {{ occurrenceStatusName(item.occurrenceStatus) }}
              </el-tag>
            </div>
            <strong>{{ item.entry.courseName }}</strong>
            <small>{{ item.entry.teachingClassName }}</small>
            <small>
              {{ item.effectiveTeacherName }} · {{ item.entry.classroomName || '教室待定' }}
            </small>
            <small v-if="item.reason" class="reason">{{ item.reason }}</small>
          </article>
        </section>
      </div>
    </el-card>
    <el-card shadow="never">
      <div class="date-toolbar">
        <strong>日期课表</strong>
        <el-date-picker
          v-model="selectedDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          @change="loadSchedule(selectedStudentId, selectedDate)" />
      </div>
      <el-empty v-if="!occurrences.length" description="当天没有课程" />
      <el-table v-else :data="occurrences">
        <el-table-column prop="effectivePeriodNo" label="节次" width="80" />
        <el-table-column prop="entry.courseName" label="课程" min-width="130" />
        <el-table-column prop="entry.teachingClassName" label="教学班" min-width="150" />
        <el-table-column prop="effectiveTeacherName" label="实际教师" width="110" />
        <el-table-column prop="entry.classroomName" label="教室" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="scope">{{ occurrenceStatusName(scope.row.occurrenceStatus) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="调整原因" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>
    <el-card v-if="studentContexts.length" shadow="never" class="student-context">
      <div>
        <small>当前学生</small>
        <strong>{{ selectedStudent?.studentName || '-' }}</strong>
        <span>{{ selectedStudent?.studentNo }} · {{ selectedStudent?.relationship }}</span>
      </div>
      <el-select
        v-if="studentContexts.length > 1"
        v-model="selectedStudentId"
        aria-label="切换学生"
        placeholder="请选择学生"
        :loading="loading"
        @change="changeStudent">
        <el-option
          v-for="student in studentContexts"
          :key="student.studentId"
          :label="`${student.studentName}（${student.studentNo}）`"
          :value="student.studentId">
          <span>{{ student.studentName }}（{{ student.relationship }}）</span>
          <small class="student-no">{{ student.studentNo }}</small>
        </el-option>
      </el-select>
    </el-card>
    <el-skeleton v-if="loading" :rows="7" animated />
    <el-empty v-else-if="!schedule.length" description="当前账号没有已发布的课表" />
    <ScheduleGrid v-else :entries="schedule" :periods="periods" readonly />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { portalEducationSchedule, portalEducationScheduleCalendar } from '../../../api/portal'
import ScheduleGrid from '../components/ScheduleGrid.vue'

const loading = ref(true)
const calendarLoading = ref(true)
const scheduleData = ref({})
const calendarData = ref({})
const selectedStudentId = ref('')
const schedule = computed(() => scheduleData.value?.schedule || [])
const occurrences = computed(() => scheduleData.value?.occurrences || [])
const selectedDate = ref(new Date().toISOString().slice(0, 10))
const today = new Date().toISOString().slice(0, 10)
const formatDate = value => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const startOfWeek = value => {
  const date = new Date(`${value}T00:00:00`)
  const offset = date.getDay() === 0 ? -6 : 1 - date.getDay()
  date.setDate(date.getDate() + offset)
  return formatDate(date)
}
const weekStart = ref(startOfWeek(today))
const weekEnd = computed(() => {
  const date = new Date(`${weekStart.value}T00:00:00`)
  date.setDate(date.getDate() + 6)
  return formatDate(date)
})
const weekDays = computed(() => {
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  return labels.map((weekday, index) => {
    const date = new Date(`${weekStart.value}T00:00:00`)
    date.setDate(date.getDate() + index)
    return { weekday, date: formatDate(date) }
  })
})
const calendarByDate = computed(() => (calendarData.value?.occurrences || []).reduce(
  (result, item) => {
    if (!result[item.date]) result[item.date] = []
    result[item.date].push(item)
    return result
  },
  {},
))
const periods = computed(() => {
  const maximum = Math.max(8, ...schedule.value.map(
    item => item.periodNo + (item.durationPeriods || 1) - 1,
  ))
  return Array.from(
    { length: maximum },
    (_, index) => ({ periodNo: index + 1, periodName: `第 ${index + 1} 节` }),
  )
})
const termName = computed(() => scheduleData.value?.termName || '')
const studentContexts = computed(() => scheduleData.value?.studentContexts || [])
const selectedStudent = computed(() => studentContexts.value.find(
  student => student.studentId === selectedStudentId.value,
))
const loadSchedule = async (studentId, date = selectedDate.value) => {
  loading.value = true
  try {
    const response = await portalEducationSchedule(studentId, date)
    scheduleData.value = response.data || {}
    selectedStudentId.value = scheduleData.value.selectedStudentId || ''
    selectedDate.value = scheduleData.value.selectedDate || date
  } finally {
    loading.value = false
  }
}

const loadCalendar = async (studentId = selectedStudentId.value) => {
  calendarLoading.value = true
  try {
    const response = await portalEducationScheduleCalendar(
      studentId,
      weekStart.value,
      weekEnd.value,
    )
    calendarData.value = response.data || {}
  } finally {
    calendarLoading.value = false
  }
}

const moveWeek = async offset => {
  weekStart.value = offset === 0
    ? startOfWeek(today)
    : (() => {
        const date = new Date(`${weekStart.value}T00:00:00`)
        date.setDate(date.getDate() + offset)
        return formatDate(date)
      })()
  await loadCalendar()
}

const changeStudent = async studentId => {
  localStorage.setItem('chronos.portal.education.studentId', studentId)
  await Promise.all([
    loadSchedule(studentId, selectedDate.value),
    loadCalendar(studentId),
  ])
}

const occurrenceStatusName = status => ({
  SCHEDULED: '正常',
  CANCELLED: '停课',
  MOVED_OUT: '已调出',
  MOVED_IN: '调入',
  SUBSTITUTED: '代课',
  MAKEUP: '补课',
}[status] || status)
const statusTagType = status => ({
  CANCELLED: 'danger',
  MOVED_OUT: 'warning',
  MOVED_IN: 'success',
  SUBSTITUTED: 'warning',
  MAKEUP: 'success',
}[status] || 'info')
const statusClass = status => `status-${String(status || '').toLowerCase()}`

onMounted(async () => {
  await loadSchedule()
  const remembered = localStorage.getItem('chronos.portal.education.studentId')
  if (remembered && studentContexts.value.some(student => student.studentId === remembered)) {
    await loadSchedule(remembered)
  }
  await loadCalendar(selectedStudentId.value)
})
</script>

<style scoped>
.portal-schedule { display: grid; gap: 18px; padding: 24px; }
.portal-schedule :deep(.el-page-header__content > div) { display: grid; gap: 3px; }
.portal-schedule :deep(.el-page-header__content strong) { color: #263f49; font-size: 20px; }
.portal-schedule :deep(.el-page-header__content small) { color: #819097; font-size: 12px; }
.portal-schedule span { color: #829197; font-size: 12px; }
.student-context :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.student-context div { display: grid; gap: 4px; }
.student-context small { color: #819097; }
.student-context .el-select { width: 280px; }
.student-no { float: right; margin-left: 24px; }
.date-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.week-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.week-toolbar > div { display: grid; gap: 3px; }
.week-toolbar small { color: #819097; }
.week-grid { display: grid; grid-template-columns: repeat(7, minmax(150px, 1fr)); gap: 10px; overflow-x: auto; }
.week-day { min-height: 180px; padding: 10px; border: 1px solid #e5ebee; border-radius: 8px; background: #fafcfc; }
.week-day.today { border-color: #409eff; background: #f2f8ff; }
.week-day > header { display: flex; justify-content: space-between; margin-bottom: 9px; }
.lesson-card { display: grid; gap: 4px; margin-bottom: 8px; padding: 8px; border-left: 3px solid #8aa3ad; border-radius: 5px; background: #fff; }
.lesson-card > div { display: flex; align-items: center; justify-content: space-between; gap: 4px; }
.lesson-card small { color: #687b83; }
.lesson-card .reason { color: #b26a00; }
.lesson-card.status-substituted, .lesson-card.status-moved-out { border-left-color: #e6a23c; }
.lesson-card.status-moved-in, .lesson-card.status-makeup { border-left-color: #67c23a; }
.lesson-card.status-cancelled { border-left-color: #f56c6c; opacity: .72; }
@media (max-width: 1100px) { .week-grid { grid-template-columns: repeat(7, 170px); } }
</style>
