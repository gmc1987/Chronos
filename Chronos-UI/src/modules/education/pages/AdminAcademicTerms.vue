<template>
  <div class="page">
    <header>
      <div><h2>学年学期与校历</h2><p>统一维护学期、特殊教学日期以及各校区作息节次。</p></div>
      <el-button type="primary" @click="openTerm()">新增学期</el-button>
    </header>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="学年学期" name="terms">
        <el-table :data="terms" border highlight-current-row @current-change="row => selectedTermId = row?.id || selectedTermId">
          <el-table-column prop="termCode" label="学期编码" min-width="130" />
          <el-table-column prop="termName" label="学期名称" min-width="180" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="startDate" label="开始日期" width="120" />
          <el-table-column prop="endDate" label="结束日期" width="120" />
          <el-table-column prop="weekCount" label="教学周" width="80" />
          <el-table-column label="当前" width="70"><template #default="s"><el-tag v-if="s.row.currentTerm" type="success">是</el-tag><span v-else>否</span></template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="s"><el-button link type="primary" @click.stop="openTerm(s.row)">编辑</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="教学日历" name="calendar">
        <div class="toolbar"><TermSelect v-model="selectedTermId" :terms="terms" @change="loadDetails" /><el-button type="primary" :disabled="!selectedTermId" @click="openDay()">新增特殊日期</el-button></div>
        <el-alert type="info" :closable="false" title="仅维护节假日、补课日、考试日等偏离正常教学安排的日期。" />
        <el-table :data="days" border>
          <el-table-column prop="calendarDate" label="日期" width="130" /><el-table-column prop="dayName" label="名称" /><el-table-column prop="dayType" label="类型" width="110" />
          <el-table-column label="教学日" width="90"><template #default="s">{{ s.row.teachingDay ? '是' : '否' }}</template></el-table-column>
          <el-table-column prop="remark" label="备注" /><el-table-column label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openDay(s.row)">编辑</el-button><el-button link type="danger" @click="removeDay(s.row)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="校区作息" name="bells">
        <div class="toolbar"><TermSelect v-model="selectedTermId" :terms="terms" @change="loadDetails" /><el-button type="primary" :disabled="!selectedTermId" @click="openBell()">新增作息方案</el-button></div>
        <el-empty v-if="!bells.length" description="当前学期暂无作息方案" />
        <el-card v-for="item in bells" :key="item.schedule.id" class="bell-card" shadow="never">
          <template #header><div class="card-head"><div><strong>{{ item.schedule.scheduleName }}</strong><el-tag v-if="item.schedule.defaultSchedule" type="success" size="small">默认</el-tag><span class="muted">{{ campusName(item.schedule.campusId) }}</span></div><div><el-button link type="primary" @click="openPeriod(item.schedule)">新增节次</el-button><el-button link type="primary" @click="openBell(item.schedule)">编辑</el-button><el-button link type="danger" @click="removeBell(item.schedule)">删除</el-button></div></div></template>
          <el-table :data="item.periods" size="small" border><el-table-column prop="periodNo" label="序号" width="70" /><el-table-column prop="periodName" label="节次名称" /><el-table-column prop="daySegment" label="时段" width="100" /><el-table-column prop="startTime" label="开始" width="100" /><el-table-column prop="endTime" label="结束" width="100" /><el-table-column label="可排课" width="90"><template #default="s">{{ s.row.schedulable ? '是' : '否' }}</template></el-table-column><el-table-column label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openPeriod(item.schedule, s.row)">编辑</el-button><el-button link type="danger" @click="removePeriod(s.row)">删除</el-button></template></el-table-column></el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="termDialog" :title="termForm.id ? '编辑学期' : '新增学期'" width="620px"><el-form label-width="100px"><el-form-item label="学期编码"><el-input v-model="termForm.termCode" /></el-form-item><el-form-item label="学期名称"><el-input v-model="termForm.termName" /></el-form-item><el-form-item label="学年"><el-input v-model="termForm.academicYear" /></el-form-item><el-form-item label="学期序号"><el-input-number v-model="termForm.termNo" :min="1" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="termForm.startDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="termForm.endDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="教学周数"><el-input-number v-model="termForm.weekCount" :min="1" /></el-form-item><el-form-item label="当前学期"><el-switch v-model="termForm.currentTerm" /></el-form-item></el-form><template #footer><el-button @click="termDialog = false">取消</el-button><el-button type="primary" @click="saveTerm">保存</el-button></template></el-dialog>
    <el-dialog v-model="dayDialog" :title="dayForm.id ? '编辑特殊日期' : '新增特殊日期'" width="560px"><el-form label-width="100px"><el-form-item label="日期"><el-date-picker v-model="dayForm.calendarDate" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="名称"><el-input v-model="dayForm.dayName" /></el-form-item><el-form-item label="类型"><el-select v-model="dayForm.dayType"><el-option label="节假日" value="HOLIDAY" /><el-option label="补课日" value="MAKEUP" /><el-option label="考试日" value="EXAM" /><el-option label="学校活动" value="EVENT" /></el-select></el-form-item><el-form-item label="计入教学日"><el-switch v-model="dayForm.teachingDay" /></el-form-item><el-form-item label="备注"><el-input v-model="dayForm.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="dayDialog = false">取消</el-button><el-button type="primary" @click="saveDay">保存</el-button></template></el-dialog>
    <el-dialog v-model="bellDialog" :title="bellForm.id ? '编辑作息方案' : '新增作息方案'" width="560px"><el-form label-width="100px"><el-form-item label="方案编码"><el-input v-model="bellForm.scheduleCode" /></el-form-item><el-form-item label="方案名称"><el-input v-model="bellForm.scheduleName" /></el-form-item><el-form-item label="校区"><el-select v-model="bellForm.campusId" filterable><el-option v-for="item in campuses" :key="item.id" :label="orgName(item)" :value="item.id" /></el-select></el-form-item><el-form-item label="默认方案"><el-switch v-model="bellForm.defaultSchedule" /></el-form-item></el-form><template #footer><el-button @click="bellDialog = false">取消</el-button><el-button type="primary" @click="saveBell">保存</el-button></template></el-dialog>
    <el-dialog v-model="periodDialog" :title="periodForm.id ? '编辑节次' : '新增节次'" width="560px"><el-form label-width="100px"><el-form-item label="节次序号"><el-input-number v-model="periodForm.periodNo" :min="1" /></el-form-item><el-form-item label="节次名称"><el-input v-model="periodForm.periodName" /></el-form-item><el-form-item label="所属时段"><el-select v-model="periodForm.daySegment"><el-option label="上午" value="MORNING" /><el-option label="下午" value="AFTERNOON" /><el-option label="晚上" value="EVENING" /></el-select></el-form-item><el-form-item label="开始时间"><el-time-picker v-model="periodForm.startTime" value-format="HH:mm:ss" /></el-form-item><el-form-item label="结束时间"><el-time-picker v-model="periodForm.endTime" value-format="HH:mm:ss" /></el-form-item><el-form-item label="允许排课"><el-switch v-model="periodForm.schedulable" /></el-form-item></el-form><template #footer><el-button @click="periodDialog = false">取消</el-button><el-button type="primary" @click="savePeriod">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import { createAcademicCalendarDay, createAcademicTerm, createBellPeriod, createBellSchedule, deleteAcademicCalendarDay, deleteBellPeriod, deleteBellSchedule, listAcademicCalendarDays, listAcademicTerms, listBellSchedules, orgList, updateAcademicCalendarDay, updateAcademicTerm, updateBellPeriod, updateBellSchedule } from '../../../api/admin'

const TermSelect = (props, context) => h(ElSelect, { modelValue: props.modelValue, 'onUpdate:modelValue': value => context.emit('update:modelValue', value), onChange: () => context.emit('change'), placeholder: '请选择学期' }, () => props.terms.map(item => h(ElOption, { key: item.id, label: item.termName, value: item.id })))
TermSelect.props = ['modelValue', 'terms']; TermSelect.emits = ['update:modelValue', 'change']
const activeTab = ref('terms'); const terms = ref([]); const campuses = ref([]); const days = ref([]); const bells = ref([]); const selectedTermId = ref('')
const termDialog = ref(false); const dayDialog = ref(false); const bellDialog = ref(false); const periodDialog = ref(false)
const termForm = reactive({}); const dayForm = reactive({}); const bellForm = reactive({}); const periodForm = reactive({})
const reset = (target, value) => { Object.keys(target).forEach(key => delete target[key]); Object.assign(target, value) }
const unwrap = response => response.data?.content || response.data || []
const loadTerms = async () => { terms.value = unwrap(await listAcademicTerms({ page: 0, size: 100 })); if (!selectedTermId.value && terms.value.length) selectedTermId.value = (terms.value.find(item => item.currentTerm) || terms.value[0]).id }
const loadCampuses = async () => { const rows = unwrap(await orgList({ page: 0, size: 200 })); campuses.value = rows.filter(item => ['CAMPUS', 'SCHOOL'].includes(item.organizationType || item.orgType)) }
const loadDetails = async () => { if (!selectedTermId.value) return; const [dayResult, bellResult] = await Promise.all([listAcademicCalendarDays(selectedTermId.value), listBellSchedules(selectedTermId.value)]); days.value = dayResult.data || []; bells.value = bellResult.data || [] }
const openTerm = row => { reset(termForm, row ? { ...row } : { termNo: 1, weekCount: 20, currentTerm: false, status: 'ACTIVE' }); termDialog.value = true }
const saveTerm = async () => { await (termForm.id ? updateAcademicTerm(termForm.id, termForm) : createAcademicTerm(termForm)); termDialog.value = false; ElMessage.success('学期保存成功'); await loadTerms(); await loadDetails() }
const openDay = row => { reset(dayForm, row ? { ...row } : { academicTermId: selectedTermId.value, dayType: 'HOLIDAY', teachingDay: false }); dayDialog.value = true }
const saveDay = async () => { await (dayForm.id ? updateAcademicCalendarDay(dayForm.id, dayForm) : createAcademicCalendarDay(dayForm)); dayDialog.value = false; ElMessage.success('教学日历保存成功'); await loadDetails() }
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
onMounted(async () => { await Promise.all([loadTerms(), loadCampuses()]); await loadDetails() })
</script>

<style scoped>
.page { padding: 24px; } header, .toolbar, .card-head { display: flex; align-items: center; justify-content: space-between; } header { margin-bottom: 18px; } h2 { margin: 0 0 6px; } p, .muted { margin: 0; color: #84909a; } .toolbar { justify-content: flex-start; gap: 12px; margin-bottom: 12px; } .toolbar .el-select { width: 260px; } .el-alert { margin-bottom: 12px; } .bell-card { margin-bottom: 14px; } .card-head > div { display: flex; align-items: center; gap: 10px; }
</style>
