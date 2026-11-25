<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { http } from '../../../api/http'
import { hasAdminPermission } from '../../../store/auth'
import { todayLocalDate } from '../../../utils/localDate'

const endpoint = '/admin/education/data-center/school-rollups'
const canImport = hasAdminPermission('education:data-center:import')
const schoolId = ref('')
const start = ref(todayLocalDate().slice(0, 7) + '-01')
const end = ref(todayLocalDate())
const source = ref('')
const dialog = ref(false)
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const latest = computed(() => rows.value[0])
const columns = [
  { field: 'studentCount', label: '在籍学生' },
  { field: 'teacherCount', label: '在岗教师' },
  { field: 'classCount', label: '开设班级' },
]
const query = () => new URLSearchParams({ schoolId: schoolId.value, start: start.value, end: end.value })
let requestId = 0

async function load() {
  const current = ++requestId
  rows.value = []
  if (!schoolId.value || !start.value || !end.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const response = await http.get(`${endpoint}?${query()}`)
    if (current === requestId) rows.value = response.data || []
  } finally {
    if (current === requestId) loading.value = false
  }
}
watch([schoolId, start, end], load)

async function save() {
  const target = schoolId.value
  if (!target) return ElMessage.warning('请选择学校')
  const lines = source.value.split(/\r?\n/).filter(line => line.trim())
  if (!lines.length || lines.length > 366) return ElMessage.warning('每批请提供 1 至 366 天的指标')
  const data = []
  for (const [index, line] of lines.entries()) {
    const values = line.trim().split('\t').map(value => value.trim())
    const [date, ...counts] = values
    if (values.length !== 4 || !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      counts.some(value => !/^\d+$/.test(value) || Number(value) > 2147483647)) {
      return ElMessage.warning(`第 ${index + 1} 行格式不正确`)
    }
    const [studentCount, teacherCount, classCount] = counts.map(Number)
    data.push({ date, studentCount, teacherCount, classCount })
  }
  await ElMessageBox.confirm('相同学校和日期的指标会以本次数据为准。是否导入？', '确认导入')
  saving.value = true
  try {
    await http.post(`${endpoint}?schoolId=${encodeURIComponent(target)}`, { rows: data })
    dialog.value = false
    source.value = ''
    ElMessage.success(`已导入 ${data.length} 天的校务指标`)
    await load()
  } finally {
    saving.value = false
  }
}

async function exportRows() {
  const download = await http.download(`${endpoint}/export?${query()}`)
  const url = URL.createObjectURL(download)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '校务日报.csv'
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <section class="report">
    <h2>校务数据分析</h2>
    <div class="toolbar">
      <OrganizationSelect v-model="schoolId" school-only style="width: 260px" />
      <el-input v-model="start" type="date" aria-label="开始日期" style="width: 150px" />
      <el-input v-model="end" type="date" aria-label="结束日期" style="width: 150px" />
      <el-button v-if="canImport" :disabled="!schoolId" type="primary" @click="dialog = true">导入校务日报</el-button>
      <el-button :disabled="!rows.length || loading" @click="exportRows">导出 CSV</el-button>
    </div>
    <div v-if="latest" class="summary">
      <el-statistic v-for="column in columns" :key="column.field" :title="column.label" :value="latest[column.field]" />
      <small>统计日期 {{ latest.date }}</small>
    </div>
    <el-table v-loading="loading" :data="rows" border row-key="date" empty-text="所选日期暂无校务日报">
      <el-table-column prop="date" label="日期" />
      <el-table-column v-for="column in columns" :key="column.field" :prop="column.field" :label="column.label" />
    </el-table>
    <el-dialog v-model="dialog" title="导入校务日报" width="550px">
      <el-form label-position="top">
        <el-form-item label="日期、在籍学生、在岗教师、开设班级">
          <el-input v-model="source" type="textarea" :rows="8" placeholder="从表格复制数据行，用 Tab 分隔列。日期格式 YYYY-MM-DD。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="save">导入</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.report { padding: 24px; }
.toolbar, .summary { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.summary { gap: 28px; padding: 12px 18px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.summary small { color: var(--el-text-color-secondary); }
</style>
