<template>
  <div class="page">
    <header>
      <div><h2>学年学期与校历</h2><p>统一维护学期、特殊教学日期以及各校区作息节次。</p></div>
      <el-button type="primary" :disabled="!organizationId" @click="openTerm()">新增学期</el-button>
    </header>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="学年学期" name="terms">
		<div class="toolbar"><OrganizationSelect v-model="organizationId" context-key="education-academic-terms" school-or-campus-only style="width:260px" /></div>
        <el-table :data="terms" border highlight-current-row @current-change="row => selectedTermId = row?.id || selectedTermId">
		  <el-table-column prop="organizationId" label="所属机构" min-width="170"><template #default="s">{{ campusName(s.row.organizationId) }}</template></el-table-column>
		  <el-table-column prop="localTermCode" label="校内学期编码" min-width="140" />
		  <el-table-column prop="termCode" label="机构学期编码" min-width="210" />
          <el-table-column prop="termName" label="学期名称" min-width="180" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="startDate" label="开始日期" width="120" />
          <el-table-column prop="endDate" label="结束日期" width="120" />
          <el-table-column prop="weekCount" label="教学周" width="80" />
          <el-table-column label="当前" width="70"><template #default="s"><el-tag v-if="s.row.currentTerm" type="success">是</el-tag><span v-else>否</span></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.status === 'ARCHIVED' ? 'info' : 'success'">{{ s.row.status === 'ARCHIVED' ? '已归档' : '启用' }}</el-tag></template></el-table-column>
          <AdaptiveActionColumn label="操作" width="150"><template #default="s"><el-button link type="primary" :disabled="s.row.status === 'ARCHIVED'" @click.stop="openTerm(s.row)">编辑</el-button><el-button link type="warning" :disabled="s.row.status === 'ARCHIVED'" @click.stop="archiveTerm(s.row)">归档</el-button></template></AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="教学日历" name="calendar">
        <div class="toolbar">
          <OrganizationSelect v-model="organizationId" context-key="education-academic-terms" school-or-campus-only style="width:260px" />
          <TermSelect v-model="selectedTermId" :terms="organizationId ? terms : []" @change="loadDetails" />
          <el-button type="primary" :disabled="!organizationId || !selectedTermId" @click="openDay()">新增特殊日期</el-button>
          <el-button :disabled="!organizationId || !selectedTermId" @click="openCalendarImport">批量导入</el-button>
        </div>
        <el-alert type="info" :closable="false" title="法定补班不自动等于学校补课。学校确认教学日后，为周末补课指定参照课表日期。" />
        <el-table :data="organizationId ? days : []" border>
          <el-table-column prop="calendarDate" label="日期" width="130" /><el-table-column prop="dayName" label="名称" /><el-table-column prop="dayType" label="类型" width="110" />
          <el-table-column label="教学日" width="90"><template #default="s">{{ s.row.teachingDay ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="按哪天课表" width="130"><template #default="s">{{ s.row.scheduleDate || '自然星期' }}</template></el-table-column>
          <el-table-column prop="remark" label="备注" /><AdaptiveActionColumn label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openDay(s.row)">编辑</el-button><el-button link type="danger" @click="removeDay(s.row)">删除</el-button></template></AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="校区作息" name="bells">
        <div class="toolbar"><TermSelect v-model="selectedTermId" :terms="terms" @change="loadDetails" /><el-button type="primary" :disabled="!selectedTermId" @click="openBell()">新增作息方案</el-button></div>
        <el-empty v-if="!bells.length" description="当前学期暂无作息方案" />
        <el-card v-for="item in bells" :key="item.schedule.id" class="bell-card" shadow="never">
          <template #header><div class="card-head"><div><strong>{{ item.schedule.scheduleName }}</strong><el-tag v-if="item.schedule.defaultSchedule" type="success" size="small">默认</el-tag><span class="muted">{{ campusName(item.schedule.campusId) }}</span></div><div><el-button link type="primary" @click="openPeriod(item.schedule)">新增节次</el-button><el-button link type="primary" @click="openBell(item.schedule)">编辑</el-button><el-button link type="danger" @click="removeBell(item.schedule)">删除</el-button></div></div></template>
          <el-table :data="item.periods" size="small" border><el-table-column prop="periodNo" label="序号" width="70" /><el-table-column prop="periodName" label="节次名称" /><el-table-column prop="daySegment" label="时段" width="100" /><el-table-column prop="startTime" label="开始" width="100" /><el-table-column prop="endTime" label="结束" width="100" /><el-table-column label="可排课" width="90"><template #default="s">{{ s.row.schedulable ? '是' : '否' }}</template></el-table-column><AdaptiveActionColumn label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openPeriod(item.schedule, s.row)">编辑</el-button><el-button link type="danger" @click="removePeriod(s.row)">删除</el-button></template></AdaptiveActionColumn></el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="termDialog" :title="termForm.id ? '编辑学期' : '新增学期'" width="620px"><el-form label-width="120px"><el-form-item label="所属机构"><el-select v-model="termForm.organizationId" filterable :disabled="!!termForm.id"><el-option v-for="item in campuses" :key="item.id" :label="orgName(item)" :value="item.id" /></el-select></el-form-item><el-form-item label="校内学期编码"><el-input v-model="termForm.localTermCode" :disabled="!!termForm.id" placeholder="例如 2026-2027-1" /><div class="muted">系统保存为“机构编码:校内学期编码”；创建后编码不可修改。</div></el-form-item><el-form-item v-if="termForm.termCode" label="机构学期编码"><el-input v-model="termForm.termCode" disabled /></el-form-item><el-form-item label="学期名称"><el-input v-model="termForm.termName" /></el-form-item><el-form-item label="学年"><el-input v-model="termForm.academicYear" /></el-form-item><el-form-item label="学期序号"><el-input-number v-model="termForm.termNo" :min="1" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="termForm.startDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="termForm.endDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="教学周数"><el-input-number v-model="termForm.weekCount" :min="1" /></el-form-item><el-form-item label="当前学期"><el-switch v-model="termForm.currentTerm" /></el-form-item></el-form><template #footer><el-button @click="termDialog = false">取消</el-button><el-button type="primary" @click="saveTerm">保存</el-button></template></el-dialog>
    <el-dialog v-model="calendarImportOpen" title="批量导入特殊日期" width="640px">
      <p>目标机构 {{ campusName(calendarImportTarget.organizationId) }} · {{ calendarImportTarget.termName }}</p>
      <p>从 Excel 复制数据行，按顺序填写日期、名称、类型、是否教学日、参照课表日期、备注。日期用 YYYY-MM-DD，教学日填“是”或“否”。</p>
      <el-input v-model="calendarImportText" type="textarea" :rows="7" :disabled="calendarImportBusy"
        placeholder="2026-10-01&#9;国庆节&#9;节假日&#9;否" @input="calendarImportResult = null" />
      <el-alert v-if="calendarImportResult" :type="calendarImportResult.errors.length ? 'error' : 'success'"
        :title="calendarImportResult.errors.length ? '请修正以下记录' : `已校验 ${calendarImportResult.total} 条记录`" :closable="false">
        <ul v-if="calendarImportResult.errors.length"><li v-for="error in calendarImportResult.errors" :key="error">{{ error }}</li></ul>
      </el-alert>
      <template #footer>
        <el-button :loading="calendarImportBusy" :disabled="!calendarImportText.trim()" @click="runCalendarImport(true)">校验</el-button>
        <el-button type="primary" :loading="calendarImportBusy" :disabled="!calendarImportResult || !!calendarImportResult.errors.length"
          @click="runCalendarImport(false)">确认导入</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="dayDialog" :title="dayForm.id ? '编辑特殊日期' : '新增特殊日期'" width="560px"><el-form label-width="110px"><el-form-item label="日期"><el-date-picker v-model="dayForm.calendarDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="名称"><el-input v-model="dayForm.dayName" /></el-form-item><el-form-item label="类型"><el-select v-model="dayForm.dayType"><el-option label="节假日" value="HOLIDAY" /><el-option label="调休补课日" value="MAKEUP_WORKDAY" /><el-option label="补课日" value="MAKEUP" /><el-option label="考试日" value="EXAM" /><el-option label="学校活动" value="EVENT" /></el-select></el-form-item><el-form-item label="计入教学日"><el-switch v-model="dayForm.teachingDay" /></el-form-item><el-form-item v-if="dayForm.teachingDay" label="按哪天课表"><el-date-picker v-model="dayForm.scheduleDate" value-format="YYYY-MM-DD" placeholder="周末补课必须指定" /><div class="muted">例如周日补上周五的课，选择对应的周五日期；单双周也按该日期计算。</div></el-form-item><el-form-item label="备注"><el-input v-model="dayForm.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="dayDialog = false">取消</el-button><el-button type="primary" @click="saveDay">保存</el-button></template></el-dialog>
    <el-dialog v-model="bellDialog" :title="bellForm.id ? '编辑作息方案' : '新增作息方案'" width="560px"><el-form label-width="100px"><el-form-item label="方案编码"><el-input v-model="bellForm.scheduleCode" /></el-form-item><el-form-item label="方案名称"><el-input v-model="bellForm.scheduleName" /></el-form-item><el-form-item label="校区"><el-select v-model="bellForm.campusId" filterable><el-option v-for="item in campuses" :key="item.id" :label="orgName(item)" :value="item.id" /></el-select></el-form-item><el-form-item label="默认方案"><el-switch v-model="bellForm.defaultSchedule" /></el-form-item></el-form><template #footer><el-button @click="bellDialog = false">取消</el-button><el-button type="primary" @click="saveBell">保存</el-button></template></el-dialog>
    <el-dialog v-model="periodDialog" :title="periodForm.id ? '编辑节次' : '新增节次'" width="560px"><el-form label-width="100px"><el-form-item label="节次序号"><el-input-number v-model="periodForm.periodNo" :min="1" /></el-form-item><el-form-item label="节次名称"><el-input v-model="periodForm.periodName" /></el-form-item><el-form-item label="所属时段"><el-select v-model="periodForm.daySegment"><el-option label="上午" value="MORNING" /><el-option label="下午" value="AFTERNOON" /><el-option label="晚上" value="EVENING" /></el-select></el-form-item><el-form-item label="开始时间"><el-time-picker v-model="periodForm.startTime" value-format="HH:mm:ss" /></el-form-item><el-form-item label="结束时间"><el-time-picker v-model="periodForm.endTime" value-format="HH:mm:ss" /></el-form-item><el-form-item label="允许排课"><el-switch v-model="periodForm.schedulable" /></el-form-item></el-form><template #footer><el-button @click="periodDialog = false">取消</el-button><el-button type="primary" @click="savePeriod">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { h, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { academicTermArchiveReadiness, archiveAcademicTerm, createAcademicCalendarDay, createAcademicTerm, createBellPeriod, createBellSchedule, deleteAcademicCalendarDay, deleteBellPeriod, deleteBellSchedule, importAcademicCalendarDays, listAcademicCalendarDays, listAcademicTerms, listBellSchedules, orgOptions, updateAcademicCalendarDay, updateAcademicTerm, updateBellPeriod, updateBellSchedule } from '../../../api/admin'

const TermSelect = (props, context) => h(ElSelect, { modelValue: props.modelValue, 'onUpdate:modelValue': value => context.emit('update:modelValue', value), onChange: () => context.emit('change'), placeholder: '请选择学期' }, () => props.terms.map(item => h(ElOption, { key: item.id, label: item.termName, value: item.id })))
TermSelect.props = ['modelValue', 'terms']; TermSelect.emits = ['update:modelValue', 'change']
const activeTab = ref('terms'); const terms = ref([]); const campuses = ref([]); const days = ref([]); const bells = ref([]); const selectedTermId = ref('')
const organizationId = ref('')
const termDialog = ref(false); const dayDialog = ref(false); const bellDialog = ref(false); const periodDialog = ref(false)
const termForm = reactive({}); const dayForm = reactive({}); const bellForm = reactive({}); const periodForm = reactive({})
const reset = (target, value) => { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, value) }
const unwrap = response => response.data?.content || response.data || []
let termsSequence = 0
const loadTerms = async () => {
  const sequence = ++termsSequence
  const result = await listAcademicTerms({ organizationId: organizationId.value || undefined })
  if (sequence !== termsSequence) return
  terms.value = unwrap(result)
  if (!terms.value.some(item => item.id === selectedTermId.value)) {
    selectedTermId.value = (terms.value.find(item => item.currentTerm) || terms.value[0])?.id || ''
  }
}
const loadCampuses = async () => { const rows = unwrap(await orgOptions()); campuses.value = rows.filter(item => ['CAMPUS', 'SCHOOL'].includes(item.organizationType || item.orgType)) }
let detailsSequence = 0
const loadDetails = async () => {
  const sequence = ++detailsSequence
  const termId = selectedTermId.value
  const organization = organizationId.value
  days.value = []
  bells.value = []
  if (!termId) return
  const [calendar, bell] = await Promise.allSettled([
    organization ? listAcademicCalendarDays(termId, organization) : Promise.resolve({ data: [] }),
    listBellSchedules(termId),
  ])
  if (sequence !== detailsSequence || termId !== selectedTermId.value || organization !== organizationId.value) return
  if (calendar.status === 'fulfilled') days.value = calendar.value.data || []
  else ElMessage.error(calendar.reason.message || '教学日历加载失败')
  if (bell.status === 'fulfilled') bells.value = bell.value.data || []
  else ElMessage.error(bell.reason.message || '校区作息加载失败')
}
const openTerm = row => { reset(termForm, row ? { ...row } : { organizationId: organizationId.value || (campuses.value.length === 1 ? campuses.value[0].id : ''), localTermCode: '', termNo: 1, weekCount: 20, currentTerm: false, status: 'ACTIVE' }); termDialog.value = true }
const changeOrganization = async () => {
  selectedTermId.value = ''
  terms.value = []
  days.value = []
  bells.value = []
  await loadTerms()
  await loadDetails()
}
const saveTerm = async () => { if (!termForm.organizationId) return ElMessage.warning('请选择学期所属机构'); if (!termForm.localTermCode?.trim()) return ElMessage.warning('请输入校内学期编码'); await (termForm.id ? updateAcademicTerm(termForm.id, termForm) : createAcademicTerm(termForm)); termDialog.value = false; ElMessage.success('学期保存成功'); await loadTerms(); await loadDetails() }
const archiveTerm = async row => {
  const response = await academicTermArchiveReadiness(row.id)
  const readiness = response.data || {}
  if (!readiness.readyForArchive) {
    ElMessage.warning(readiness.message || '当前学期尚不满足归档条件')
    return
  }
  await ElMessageBox.confirm(`确认归档“${row.termName}”？归档后学期历史数据将进入只读状态。`, '学期归档确认', { type: 'warning' })
  await archiveAcademicTerm(row.id)
  ElMessage.success('学期归档成功')
  await loadTerms()
}
const calendarImportOpen = ref(false)
const calendarImportBusy = ref(false)
const calendarImportText = ref('')
const calendarImportResult = ref(null)
const calendarImportTarget = ref({})
const calendarTypes = { 节假日: 'HOLIDAY', 调休补课日: 'MAKEUP_WORKDAY', 补课日: 'MAKEUP', 考试日: 'EXAM', 学校活动: 'EVENT' }
const openCalendarImport = () => {
  calendarImportTarget.value = {
    organizationId: organizationId.value,
    termId: selectedTermId.value,
    termName: terms.value.find(term => term.id === selectedTermId.value)?.termName,
  }
  calendarImportText.value = ''
  calendarImportResult.value = null
  calendarImportOpen.value = true
}
const runCalendarImport = async preview => {
  calendarImportBusy.value = true
  calendarImportResult.value = null
  try {
    const errors = []
    const rows = calendarImportText.value.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
      const [calendarDate, dayName, type, teaching, scheduleDate, remark, ...extra] = line.split('\t').map(cell => cell.trim())
      const dayType = calendarTypes[type]
      const datePattern = /^\d{4}-\d{2}-\d{2}$/
      if (!datePattern.test(calendarDate) || !dayName || !dayType || !['是', '否'].includes(teaching)
          || (scheduleDate && !datePattern.test(scheduleDate)) || extra.length) {
        errors.push(`第 ${index + 1} 条：请检查日期、名称、类型、教学日及列数`)
      }
      return { calendarDate, dayName, dayType, teachingDay: teaching === '是', scheduleDate: scheduleDate || null, remark }
    })
    if (errors.length) {
      calendarImportResult.value = { errors }
      return
    }
    const response = await importAcademicCalendarDays({ ...calendarImportTarget.value, preview, rows })
    calendarImportResult.value = response.data
    if (!preview && !response.data.errors.length) {
      calendarImportOpen.value = false
      ElMessage.success(`已导入 ${response.data.imported} 条特殊日期`)
      await loadDetails()
    }
  } catch (error) {
    ElMessage.error(error.message || '批量导入失败')
  } finally {
    calendarImportBusy.value = false
  }
}
const openDay = row => { reset(dayForm, row ? { ...row } : { academicTermId: selectedTermId.value, dayType: 'HOLIDAY', teachingDay: false }); dayDialog.value = true }
const saveDay = async () => { const payload = { ...dayForm, scheduleDate: dayForm.teachingDay ? dayForm.scheduleDate || null : null }; await (dayForm.id ? updateAcademicCalendarDay(dayForm.id, payload) : createAcademicCalendarDay(payload)); dayDialog.value = false; ElMessage.success('教学日历保存成功'); await loadDetails() }
const removeDay = async row => { await confirmDelete(`特殊日期“${row.dayName}”`); await deleteAcademicCalendarDay(row.id); await loadDetails() }
const openBell = row => { reset(bellForm, row ? { ...row } : { academicTermId: selectedTermId.value, defaultSchedule: false, status: 'ACTIVE' }); bellDialog.value = true }
const saveBell = async () => { await (bellForm.id ? updateBellSchedule(bellForm.id, bellForm) : createBellSchedule(bellForm)); bellDialog.value = false; ElMessage.success('作息方案保存成功'); await loadDetails() }
const removeBell = async row => { await confirmDelete(`作息方案“${row.scheduleName}”及其全部节次`); await deleteBellSchedule(row.id); await loadDetails() }
const openPeriod = (schedule, row) => { reset(periodForm, row ? { ...row } : { bellScheduleId: schedule.id, periodNo: 1, daySegment: 'MORNING', schedulable: true }); periodDialog.value = true }
const savePeriod = async () => { await (periodForm.id ? updateBellPeriod(periodForm.id, periodForm) : createBellPeriod(periodForm)); periodDialog.value = false; ElMessage.success('节次保存成功'); await loadDetails() }
const removePeriod = async row => { await confirmDelete(`节次“${row.periodName}”`); await deleteBellPeriod(row.id); await loadDetails() }
const confirmDelete = text => ElMessageBox.confirm(`确认删除${text}？`, '删除确认', { type: 'warning' })
const orgName = item => item.organizationName || item.orgName || item.name || item.id
const campusName = id => orgName(campuses.value.find(item => item.id === id) || { id })
onMounted(async () => {
  await loadCampuses()
})
watch(organizationId, changeOrganization)
</script>

<style scoped>
.page { padding: 24px; } header, .toolbar, .card-head { display: flex; align-items: center; justify-content: space-between; } header { margin-bottom: 18px; } h2 { margin: 0 0 6px; } p, .muted { margin: 0; color: #84909a; } .toolbar { justify-content: flex-start; gap: 12px; margin-bottom: 12px; } .toolbar .el-select { width: 260px; } .el-alert { margin-bottom: 12px; } .bell-card { margin-bottom: 14px; } .card-head > div { display: flex; align-items: center; gap: 10px; }
</style>
