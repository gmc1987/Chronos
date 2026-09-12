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

    <el-alert
      title="仅展示教务处最近发布的课表版本；未发布的调整草稿不会出现在这里。"
      type="info"
      :closable="false" />
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
        <el-table-column prop="entry.teacherName" label="教师" width="110" />
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
import { portalEducationSchedule } from '../../../api/portal'
import ScheduleGrid from '../components/ScheduleGrid.vue'

const loading = ref(true)
const scheduleData = ref({})
const selectedStudentId = ref('')
const schedule = computed(() => scheduleData.value?.schedule || [])
const occurrences = computed(() => scheduleData.value?.occurrences || [])
const selectedDate = ref(new Date().toISOString().slice(0, 10))
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

const changeStudent = async studentId => {
  localStorage.setItem('chronos.portal.education.studentId', studentId)
  await loadSchedule(studentId, selectedDate.value)
}

const occurrenceStatusName = status => ({
  SCHEDULED: '正常',
  CANCELLED: '停课',
  MOVED_OUT: '已调出',
  MOVED_IN: '调入',
  SUBSTITUTED: '代课',
  MAKEUP: '补课',
}[status] || status)

onMounted(async () => {
  await loadSchedule()
  const remembered = localStorage.getItem('chronos.portal.education.studentId')
  if (remembered && studentContexts.value.some(student => student.studentId === remembered)) {
    await loadSchedule(remembered)
  }
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
</style>
