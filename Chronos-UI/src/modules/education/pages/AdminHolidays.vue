<template>
  <div class="page">
    <header><div><h2>节假日维护</h2><p>自动同步国务院公布的年度安排，再由学校确认教学日及参照课表。</p></div></header>
    <div class="toolbar">
      <el-select v-model="termId" placeholder="选择学期" @change="loadDays"><el-option v-for="term in terms" :key="term.id" :label="term.termName" :value="term.id" /></el-select>
      <el-input-number v-model="year" :min="2000" :max="maxYear" @change="loadDays" />
      <el-button type="primary" :loading="importing" :disabled="!termId" @click="importOfficial">从公告数据导入</el-button>
      <el-button :disabled="!termId" @click="manualDialog = true">手动导入 JSON</el-button>
      <el-button :disabled="!termId" @click="openDay()">新增特殊日期</el-button>
      <el-button :disabled="!termId" @click="loadDays">刷新</el-button>
    </div>
    <el-alert type="info" :closable="false" title="系统每日自动同步已公布的当年和下一年安排。自动同步不会覆盖人工修改；补班日默认不排学校课程，需指定参照课表日期后才能启用。" />
    <p class="sync-status">本学期自动导入 {{ autoCount }} 条；最近同步：{{ lastImportedAt || '尚无已公布数据' }}</p>
    <el-table :data="visibleDays" border empty-text="当前年份没有节假日记录">
      <el-table-column prop="calendarDate" label="日期" width="125" />
      <el-table-column prop="dayName" label="名称" min-width="130" />
      <el-table-column label="类别" width="120"><template #default="s">{{ typeName(s.row.dayType) }}</template></el-table-column>
      <el-table-column label="学校上课" width="100"><template #default="s">{{ s.row.teachingDay ? '是' : '否' }}</template></el-table-column>
      <el-table-column prop="scheduleDate" label="参照课表日期" width="130" />
      <el-table-column label="来源" width="110"><template #default="s">{{ s.row.sourceType === 'AUTO' ? '自动导入' : '人工维护' }}</template></el-table-column>
      <el-table-column label="公告" min-width="100"><template #default="s"><a v-if="s.row.sourceUrl" :href="s.row.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源</a></template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <AdaptiveActionColumn label="操作" width="110"><template #default="s"><el-button link type="primary" @click="openDay(s.row)">修改</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="manualDialog" title="手动导入年度节假日 JSON" width="680px">
      <p>使用含 year、days（date、name、isOffDay）的年度 JSON；已有人工记录会保留。</p>
      <input type="file" accept=".json,application/json" @change="readFile" />
      <el-input v-model="manualContent" type="textarea" :rows="12" placeholder="也可粘贴年度 JSON" />
      <template #footer><el-button @click="manualDialog = false">取消</el-button><el-button type="primary" :loading="importing" @click="importManual">导入</el-button></template>
    </el-dialog>
    <el-dialog v-model="dayDialog" title="维护学校特殊日期" width="580px">
      <el-form label-width="115px">
        <el-form-item label="日期"><el-date-picker v-model="dayForm.calendarDate" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="dayForm.dayName" /></el-form-item>
        <el-form-item label="类别"><el-select v-model="dayForm.dayType"><el-option label="休假" value="HOLIDAY" /><el-option label="调休补班" value="MAKEUP_WORKDAY" /><el-option label="学校补课" value="MAKEUP" /><el-option label="考试" value="EXAM" /><el-option label="学校活动" value="EVENT" /></el-select></el-form-item>
        <el-form-item label="学校上课"><el-switch v-model="dayForm.teachingDay" /></el-form-item>
        <el-form-item v-if="dayForm.teachingDay" label="参照课表日期"><el-date-picker v-model="dayForm.scheduleDate" value-format="YYYY-MM-DD" placeholder="周末上课必填" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="dayForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dayDialog = false">取消</el-button><el-button type="primary" @click="saveDay">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdaptiveActionColumn from '../../../components/AdaptiveActionColumn.vue'
import { createAcademicCalendarDay, importAcademicHolidays, listAcademicCalendarDays, listAcademicTerms, updateAcademicCalendarDay } from '../../../api/admin'

const terms = ref([])
const termId = ref('')
const year = ref(new Date().getFullYear())
const maxYear = new Date().getFullYear() + 2
const days = ref([])
const importing = ref(false)
const manualDialog = ref(false)
const manualContent = ref('')
const dayDialog = ref(false)
const dayForm = reactive({})
const visibleDays = computed(() => days.value.filter(day => day.calendarDate?.startsWith(`${year.value}-`)))
const autoCount = computed(() => visibleDays.value.filter(day => day.sourceType === 'AUTO').length)
const lastImportedAt = computed(() => visibleDays.value.map(day => day.importedAt).filter(Boolean).sort().at(-1) || '')
const typeName = type => ({ HOLIDAY: '休假', MAKEUP_WORKDAY: '调休补班', MAKEUP: '学校补课', EXAM: '考试', EVENT: '学校活动' }[type] || type)
const loadDays = async () => { days.value = termId.value ? (await listAcademicCalendarDays(termId.value)).data || [] : [] }
const showResult = result => ElMessage.success(`导入完成：新增 ${result.counts.inserted}、更新 ${result.counts.updated}、保留人工记录 ${result.counts.skipped}`)
const importOfficial = async () => { importing.value = true; try { showResult((await importAcademicHolidays({ termId: termId.value, year: year.value, mode: 'AUTO' })).data); await loadDays() } finally { importing.value = false } }
const importManual = async () => { importing.value = true; try { showResult((await importAcademicHolidays({ termId: termId.value, year: year.value, mode: 'MANUAL', content: manualContent.value })).data); manualDialog.value = false; await loadDays() } finally { importing.value = false } }
const readFile = async event => { const file = event.target.files?.[0]; if (file) manualContent.value = await file.text() }
const openDay = row => { Object.keys(dayForm).forEach(key => delete dayForm[key]); Object.assign(dayForm, row ? { ...row } : { academicTermId: termId.value, calendarDate: `${year.value}-01-01`, dayType: 'HOLIDAY', teachingDay: false }); dayDialog.value = true }
const saveDay = async () => { const payload = { ...dayForm, scheduleDate: dayForm.teachingDay ? dayForm.scheduleDate || null : null }; await (dayForm.id ? updateAcademicCalendarDay(dayForm.id, payload) : createAcademicCalendarDay(payload)); dayDialog.value = false; ElMessage.success('已保存学校日期设置'); await loadDays() }
onMounted(async () => { const response = await listAcademicTerms({ page: 0, size: 100 }); terms.value = response.data?.content || response.data || []; const today = new Date().toISOString().slice(0, 10); termId.value = (terms.value.find(term => term.startDate <= today && term.endDate >= today) || terms.value.find(term => term.currentTerm) || terms.value[0])?.id || ''; await loadDays() })
</script>

<style scoped>
.page { padding: 24px; }
header { margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0 0 12px; color: #84909a; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.toolbar .el-select { width: 240px; }
.el-alert { margin-bottom: 12px; }
.sync-status { margin-bottom: 12px; }
</style>
