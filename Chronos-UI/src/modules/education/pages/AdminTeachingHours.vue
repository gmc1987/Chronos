<template>
  <section class="hours-page">
    <header><h2>教师课时台账</h2><p>按已发布课表和日期变更统计应授节次。实授分钟取自授课确认。</p></header>
    <div class="filters">
      <OrganizationSelect v-model="organizationId" school-or-campus-only style="width: 240px" />
      <el-select v-model="semesterCode" placeholder="选择学期" style="width: 210px">
        <el-option v-for="term in terms" :key="term.termCode" :label="term.termName" :value="term.termCode" />
      </el-select>
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD"
        start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-select v-model="teacherId" clearable filterable placeholder="全部教师" style="width: 160px">
        <el-option v-for="item in teacherOptions" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="courseCode" clearable filterable placeholder="全部课程" style="width: 172px">
        <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="offeringId" clearable filterable placeholder="全部教学班" style="width: 210px">
        <el-option v-for="item in offeringOptions" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button type="primary" :disabled="!semesterCode || !dates?.length" :loading="loading" @click="load">查询</el-button>
      <el-button :disabled="!filtered.length || loading" @click="exportRows">导出明细</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="totals">
      <span>应授节次 <strong>{{ totals.periods }}</strong></span>
      <span>已知应授分钟 <strong>{{ totals.planned }}</strong></span>
      <span>待配置作息课次 <strong>{{ totals.unknown }}</strong></span>
      <span>已确认实授分钟 <strong>{{ totals.actual }}</strong></span>
      <span>待确认课次 <strong>{{ totals.pending }}</strong></span>
    </div>
    <el-table v-loading="loading" :data="pageRows" border empty-text="当前范围没有已发布课次或授课确认">
      <el-table-column prop="lesson.date" label="日期" width="115" />
      <el-table-column label="节次" width="72">
        <template #default="{ row }">{{ row.lesson.periodNo }}</template>
      </el-table-column>
      <el-table-column prop="lesson.teacherName" label="授课教师" min-width="115" />
      <el-table-column prop="lesson.courseName" label="课程" min-width="130" />
      <el-table-column prop="lesson.teachingClassName" label="教学班" min-width="165" />
      <el-table-column label="应授节次 / 分钟" width="138">
        <template #default="{ row }">{{ row.historical ? '—' : `${row.lesson.plannedPeriods} / ${row.lesson.plannedMinutes ?? '待配置'}` }}</template>
      </el-table-column>
      <el-table-column label="实授分钟" width="88">
        <template #default="{ row }">{{ row.actualMinutes ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="126">
        <template #default="{ row }">
          {{ scheduleLabel(row.lesson.scheduleStatus) }}<br />{{ evidenceLabel(row.evidenceStatus) }}
        </template>
      </el-table-column>
      <AdaptiveActionColumn label="操作" width="180">
        <template #default="{ row }">
          <el-button link @click="openDetail(row)">详情</el-button>
          <el-button v-if="canConfirm && canEdit(row)" link type="primary" @click="openConfirm(row)">
            {{ row.actualMinutes !== null ? '更正' : '确认授课' }}
          </el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
      :total="filtered.length" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" />

    <el-dialog v-model="detailsVisible" title="课次与确认记录" width="min(820px, 94vw)">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item label="课程">{{ selected.lesson.courseName }}</el-descriptions-item>
        <el-descriptions-item label="原课日期">{{ selected.lesson.sourceDate }}</el-descriptions-item>
        <el-descriptions-item label="原授课教师">{{ selected.lesson.originalTeacherName }}</el-descriptions-item>
        <el-descriptions-item label="生效授课教师">{{ selected.lesson.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="变更记录">{{ selected.lesson.adjustmentId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="变更原因">{{ selected.lesson.adjustmentReason || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="history" empty-text="暂无确认记录">
        <el-table-column prop="revision" label="版本" width="70" />
        <el-table-column label="实授分钟" width="100">
          <template #default="{ row }">{{ row.actualMinutes ?? '已作废' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="确认或更正依据" min-width="160" />
        <el-table-column prop="recordedBy" label="确认人" width="130" />
        <el-table-column label="确认时间" width="175">
          <template #default="{ row }">{{ String(row.recordedAt ?? '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="confirmVisible" :title="selected?.actualMinutes !== null ? '更正授课确认' : '确认授课'"
      width="min(500px, 94vw)">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" />
      <el-form label-width="100px" @submit.prevent>
        <el-form-item v-if="selected?.actualMinutes !== null" label="作废确认">
          <el-checkbox v-model="form.voidConfirmation">保留历史版本，撤销当前凭证</el-checkbox>
        </el-form-item>
        <el-form-item v-if="!form.voidConfirmation" label="实授分钟">
          <el-input-number v-model="form.actualMinutes" :min="0" :max="1440" />
        </el-form-item>
        <el-form-item label="确认依据">
          <el-input v-model="form.reason" type="textarea" :maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!form.reason.trim() || (!form.voidConfirmation && form.actualMinutes == null)"
          :loading="saving" @click="save">保存确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import AdaptiveActionColumn from '../../../components/AdaptiveActionColumn.vue'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { listAcademicTerms, listTeachingHours, listTeachingHoursHistory, confirmTeachingHours } from '../../../api/admin'
import { hasAdminPermission } from '../../../store/auth'
import { todayLocalDate } from '../../../utils/localDate'

const route = useRoute()
const organizationId = ref(typeof route.query.organizationId === 'string' ? route.query.organizationId : '')
const semesterCode = ref('')
const dates = ref([])
const terms = ref([])
const rows = ref([])
const teacherId = ref('')
const courseCode = ref('')
const offeringId = ref('')
const page = ref(1)
const pageSize = ref(20)
const error = ref('')
const loading = ref(false)
const detailsVisible = ref(false)
const confirmVisible = ref(false)
const selected = ref(null)
const history = ref([])
const formError = ref('')
const saving = ref(false)
const form = reactive({ actualMinutes: null, reason: '', voidConfirmation: false })
const canConfirm = hasAdminPermission('education:scheduling:manage')
let loadSequence = 0

const options = (id, name) => [...new Map(rows.value.map(({ lesson }) =>
  [lesson[id], { id: lesson[id], name: lesson[name] }])).values()].filter(item => item.id)
const teacherOptions = computed(() => options('teacherId', 'teacherName'))
const courseOptions = computed(() => options('courseCode', 'courseName'))
const offeringOptions = computed(() => options('offeringId', 'teachingClassName'))
const filtered = computed(() => rows.value.filter(({ lesson }) =>
  (!teacherId.value || teacherId.value === lesson.teacherId) &&
  (!courseCode.value || courseCode.value === lesson.courseCode) &&
  (!offeringId.value || offeringId.value === lesson.offeringId)))
const pageRows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const totals = computed(() => filtered.value.reduce((total, row) => {
  if (!row.historical) {
    total.periods += row.lesson.plannedPeriods
    total.planned += row.lesson.plannedMinutes || 0
    if (row.lesson.plannedMinutes === null && row.lesson.plannedPeriods > 0) total.unknown++
    if (row.lesson.plannedPeriods > 0 && row.actualMinutes === null) total.pending++
  }
  total.actual += row.actualMinutes || 0
  return total
}, { periods: 0, planned: 0, actual: 0, unknown: 0, pending: 0 }))
const scheduleLabel = value => ({
  SCHEDULED: '正常排课', CANCELLED: '停课', MOVED_OUT: '调出', MOVED_IN: '调入',
  MAKEUP: '补课', SUBSTITUTED: '代课', PENDING_MAKEUP: '待补课',
}[value] || value)
const evidenceLabel = value => ({
  PENDING: '待确认', CONFIRMED: '已确认', VOIDED: '凭证已作废', NO_LESSON: '—',
  HISTORICAL: '历史确认', CHANGE_REQUIRED: '待更正确认',
}[value] || value)
const message = exception => exception.response?.data?.msg || exception.message
const scope = () => ({ organizationId: organizationId.value, semesterCode: semesterCode.value })
const canEdit = row => row.actualMinutes !== null || (!row.historical && row.lesson.plannedPeriods > 0
  && row.lesson.date <= todayLocalDate() && row.evidenceStatus !== 'CHANGE_REQUIRED')

function resetRows() {
  loadSequence++
  loading.value = false
  rows.value = []
  teacherId.value = ''
  courseCode.value = ''
  offeringId.value = ''
  page.value = 1
  detailsVisible.value = false
  confirmVisible.value = false
}

async function load() {
  if (!dates.value?.length || !semesterCode.value) return
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const response = await listTeachingHours({
      ...scope(), startDate: dates.value[0], endDate: dates.value[1],
    })
    if (sequence !== loadSequence) return
    rows.value = response.data || []
    if (!teacherOptions.value.some(item => item.id === teacherId.value)) teacherId.value = ''
    if (!courseOptions.value.some(item => item.id === courseCode.value)) courseCode.value = ''
    if (!offeringOptions.value.some(item => item.id === offeringId.value)) offeringId.value = ''
    page.value = 1
  } catch (exception) {
    if (sequence === loadSequence) {
      rows.value = []
      error.value = message(exception)
    }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

watch(organizationId, async org => {
  resetRows()
  terms.value = []
  semesterCode.value = ''
  if (!org) return
  try {
    const response = await listAcademicTerms({ organizationId: org })
    if (org !== organizationId.value) return
    terms.value = response.data?.content || response.data || []
    semesterCode.value = terms.value.find(term => term.termCode === route.query.semesterCode)?.termCode
      || terms.value.find(term => term.currentTerm)?.termCode || terms.value[0]?.termCode || ''
  } catch (exception) {
    if (org === organizationId.value) error.value = message(exception)
  }
}, { immediate: true })

watch(semesterCode, code => {
  resetRows()
  const term = terms.value.find(item => item.termCode === code)
  if (!term) { dates.value = []; return }
  const today = todayLocalDate()
  const date = today < term.startDate ? term.startDate : today > term.endDate ? term.endDate : today
  dates.value = [date, date]
})
watch([teacherId, courseCode, offeringId, pageSize], () => { page.value = 1 })
watch(dates, () => { rows.value = []; loadSequence++; loading.value = false; load() })

async function openDetail(row) {
  selected.value = row
  history.value = []
  detailsVisible.value = true
  try {
    const response = await listTeachingHoursHistory({ ...scope(), key: row.lesson.key })
    if (selected.value === row) history.value = response.data || []
  } catch (exception) { ElMessage.error(message(exception)) }
}
function openConfirm(row) {
  selected.value = row
  Object.assign(form, { actualMinutes: row.actualMinutes ?? null, reason: '', voidConfirmation: false })
  formError.value = ''
  confirmVisible.value = true
}
async function save() {
  const row = selected.value
  saving.value = true
  formError.value = ''
  try {
    await confirmTeachingHours({ ...scope(), date: row.lesson.date, key: row.lesson.key, token: row.lesson.token,
      expectedRevision: row.revision, ...form })
    confirmVisible.value = false
    ElMessage.success('授课确认已保存')
    await load()
  } catch (exception) {
    formError.value = message(exception)
  } finally {
    saving.value = false
  }
}

function exportRows() {
  const encode = value => {
    const cell = String(value ?? '')
    return '"' + (/^\s*[=+@-]/.test(cell) ? "'" + cell : cell).replaceAll('"', '""') + '"'
  }
  const lines = [['日期', '原日期', '节次', '教师', '课程', '教学班', '应授节次', '应授分钟',
    '实授分钟', '课表状态', '确认状态', '版本', '变更记录'],
  ...filtered.value.map(({ lesson, historical, actualMinutes, evidenceStatus, revision }) => [
    lesson.date, lesson.sourceDate, lesson.periodNo, lesson.teacherName, lesson.courseName, lesson.teachingClassName,
    historical ? '' : lesson.plannedPeriods, historical ? '' : lesson.plannedMinutes,
    actualMinutes, scheduleLabel(lesson.scheduleStatus), evidenceLabel(evidenceStatus), revision, lesson.adjustmentId,
  ])]
  const url = URL.createObjectURL(new Blob(['\uFEFF', lines.map(line => line.map(encode).join(',')).join('\r\n')],
    { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `教师课时-${dates.value[0]}至${dates.value[1]}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<style scoped>
.hours-page { padding: 24px; }
header h2 { margin: 0 0 6px; }
header p { margin: 0; color: var(--el-text-color-secondary); }
.filters { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 18px 0; }
.totals { display: flex; flex-wrap: wrap; gap: 16px; padding: 12px 0; }
.totals strong { color: var(--el-color-primary); font-size: 17px; padding-left: 4px; }
.el-pagination { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
