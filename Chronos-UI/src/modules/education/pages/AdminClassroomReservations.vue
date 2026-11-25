<template>
  <section class="page">
    <h2>教室预约</h2>
    <div class="toolbar">
      <OrganizationSelect v-model="organizationId" school-or-campus-only style="width: 245px" />
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD"
        range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 134px">
        <el-option v-for="(label, code) in statuses" :key="code" :label="label" :value="code" />
      </el-select>
      <el-button :disabled="!organizationId || !dates?.length" @click="search">查询</el-button>
      <el-button v-if="canManage" @click="openTerm">新建预约学期</el-button>
      <router-link to="/portal/education/classroom-reservations"><el-button>我的教室申请</el-button></router-link>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="classroomName" label="教室" min-width="150" />
      <el-table-column prop="applicantUsername" label="申请账号" min-width="125" />
      <el-table-column prop="usageDate" label="使用日期" width="115" />
      <el-table-column label="使用节次" width="110">
        <template #default="{ row }">{{ row.startPeriod }}–{{ row.startPeriod + row.durationPeriods - 1 }}</template>
      </el-table-column>
      <el-table-column prop="attendeeCount" label="人数" width="75" />
      <el-table-column prop="purpose" label="用途" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statuses[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="reviewedBy" label="审核人" min-width="110" />
      <el-table-column prop="reviewNote" label="审核意见" min-width="130" show-overflow-tooltip />
      <el-table-column prop="cancellationReason" label="撤销原因" min-width="130" show-overflow-tooltip />
      <AdaptiveActionColumn v-if="canManage" label="操作" width="130">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING' && row.applicantUsername !== actor" type="primary" link @click="decide(row.id, true)">通过</el-button>
          <el-button v-if="row.status === 'PENDING' && row.applicantUsername !== actor" type="danger" link @click="decide(row.id, false)">驳回</el-button>
          <el-button v-if="row.status === 'ACTIVE'" type="warning" link @click="cancel(row.id)">撤销</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next"
      class="pagination" @current-change="load" />

    <el-dialog v-model="termVisible" title="新建预约学期" width="min(540px, 95vw)">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="学校"><OrganizationSelect v-model="schoolId" school-only /></el-form-item>
        <el-form-item label="学年"><el-input v-model="term.academicYear" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学期序号"><el-input-number v-model="term.termNo" :min="1" :max="3" /></el-form-item>
        <el-form-item label="学期名称"><el-input v-model="term.name" maxlength="128" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="term.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="term.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="教学周数"><el-input-number v-model="term.weeks" :min="1" :max="53" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" :disabled="!schoolId" @click="saveTerm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../../api/http'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import { getAdminUsername, hasAdminPermission } from '../../../store/auth'
import { todayLocalDate } from '../../../utils/localDate'

const endpoint = '/admin/education/classroom-reservations/desk'
const canManage = hasAdminPermission('education:resource:venue:manage')
const actor = getAdminUsername()
const statuses = { PENDING: '待审核', ACTIVE: '已生效', REJECTED: '已驳回', CANCELLED: '已撤销', APPLY_FAILED: '回写失败' }
const end = new Date()
end.setDate(end.getDate() + 30)
const organizationId = ref('')
const dates = ref([todayLocalDate(), todayLocalDate(end)])
const status = ref('')
const page = ref(1)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const termVisible = ref(false)
const saving = ref(false)
const schoolId = ref('')
const term = reactive({})
let request = 0

async function load() {
  const seq = ++request
  if (!organizationId.value || !dates.value?.length) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const params = new URLSearchParams({ organizationId: organizationId.value, startDate: dates.value[0],
      endDate: dates.value[1], page: page.value - 1 })
    if (status.value) params.set('status', status.value)
    const { data } = await http.get(endpoint + '?' + params)
    if (seq !== request) return
    rows.value = data.content
    total.value = data.totalElements
  } finally {
    if (seq === request) loading.value = false
  }
}

function search() {
  page.value = 1
  return load()
}
watch(organizationId, () => {
  rows.value = []
  total.value = 0
  return search()
})

function openTerm() {
  schoolId.value = organizationId.value
  Object.assign(term, { academicYear: '', termNo: 1, name: '', startDate: '', endDate: '', weeks: 20 })
  termVisible.value = true
}

async function saveTerm() {
  saving.value = true
  try {
    await http.post(endpoint + '/terms?' + new URLSearchParams({ organizationId: schoolId.value }), { ...term })
    termVisible.value = false
    ElMessage.success('预约学期已创建')
  } finally {
    saving.value = false
  }
}

async function decide(id, approve) {
  const answer = await ElMessageBox.prompt(approve ? '审核意见（选填）' : '请填写驳回原因',
    approve ? '通过教室申请' : '驳回教室申请', {
      inputValidator: value => approve || Boolean(value?.trim()) || '请输入驳回原因',
    }).catch(() => null)
  if (!answer) return
  await http.post(endpoint + '/' + id + '/review', { approve, note: answer.value })
  ElMessage.success(approve ? '已通过申请并占用教室' : '申请已驳回')
  await load()
}

async function cancel(id) {
  const answer = await ElMessageBox.prompt('请填写撤销原因', '撤销教室预约', {
    inputValidator: value => Boolean(value?.trim()) || '请输入撤销原因',
  }).catch(() => null)
  if (!answer) return
  await http.post(endpoint + '/' + id + '/cancel', { reason: answer.value })
  ElMessage.success('预约已撤销')
  await load()
}
</script>

<style scoped>
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.pagination { justify-content: flex-end; margin-top: 10px; }
</style>
