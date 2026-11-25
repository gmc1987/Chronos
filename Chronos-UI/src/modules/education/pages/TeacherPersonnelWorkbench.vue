<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeliveryContext, hasAdminPermission } from '../../../store/auth'
import TeacherPersonnelActions from './TeacherPersonnelActions.vue'
import { useRoute } from 'vue-router'
import { http } from '../../../api/http'
import OrganizationSelect from '../../../components/OrganizationSelect.vue'
import PortalWorkflowStart from '../../workflow/pages/PortalWorkflowStart.vue'

const props = defineProps({ portal: Boolean })
const standalone = getDeliveryContext().mode === 'customer'
const canManage = hasAdminPermission('education:teacher:business:manage')
const canView = hasAdminPermission('education:teacher:business:view', 'education:teacher:business:manage')
const organizationId = ref('')
const route = useRoute()
const tab = ref(route.query.start === '1' ? 'start' : 'cases')
const page = ref(0)
const status = ref('')
const rows = ref([])
const loading = ref(false)
const types = { ONBOARD: '入职', TERMINATE: '离职', TRANSFER: '调动', SUSPEND: '停职', RESUME: '复职' }
const states = { REVIEWING: '待审批', APPROVED: '待生效', APPLIED: '已生效', REJECTED: '已驳回', CANCELLED: '已撤回', APPLY_FAILED: '生效异常' }

let version = 0
async function load() {
  const current = ++version
  if (!props.portal && (!organizationId.value || (standalone && !canView))) return
  loading.value = true
  try {
    const path = props.portal ? '/portal/education/teacher-personnel/mine' : '/admin/education/teacher-personnel'
    const params = new URLSearchParams({ page: page.value })
    if (!props.portal) {
      params.set('organizationId', organizationId.value)
      if (status.value) params.set('status', status.value)
    }
    const response = await http.get(path + '?' + params)
    if (version === current) rows.value = response.data || []
  } finally {
    if (version === current) loading.value = false
  }
}
watch([organizationId, status], () => {
  page.value = 0
  rows.value = []
  loading.value = false
  load()
}, { immediate: true })

async function turnPage(delta) {
  page.value += delta
  await load()
}
async function retry(row) {
  await http.post('/admin/education/teacher-personnel/' + encodeURIComponent(row.instanceId) + '/retry', {})
  await load()
}
async function handover(row) {
  const result = await ElMessageBox.prompt('设置交接给教师的临时密码，教师首次登录须修改。', '交接教师账号', {
    inputType: 'password',
    inputValidator: value => (value && value.length >= 12 && value.length <= 64 && /[A-Z]/.test(value)
      && /[a-z]/.test(value) && /[0-9]/.test(value)) || '请输入 12 至 64 位且含大小写字母和数字的密码',
  })
  await http.post(`/admin/education/teacher-personnel/directory/${encodeURIComponent(row.employeeCode)}/handover?organizationId=${encodeURIComponent(organizationId.value)}`, {
    password: result.value,
  })
  ElMessage.success('临时密码已设置，请通过校内安全渠道交接')
  await load()
}

</script>

<template>
  <section class="personnel">
    <h2>教师人事工作台</h2>
    <p>办理教师入职、离职、调动、停职和复职。审批通过后按生效日期处理。</p>
    <div class="toolbar">
      <OrganizationSelect v-if="!portal && (!standalone || canView)" v-model="organizationId" school-only style="width:260px" />
      <el-select v-if="!portal && (!standalone || canView)" v-model="status" clearable placeholder="全部办理状态" style="width:150px">
        <el-option v-for="(label, code) in states" :key="code" :value="code" :label="label" />
      </el-select>
      <el-button v-if="portal || !standalone || canView" :loading="loading" @click="load">刷新申请</el-button>
      <el-button v-if="!standalone && !portal" type="primary" @click="$router.push('/portal/education/teacher-personnel?start=1')">发起人事流程</el-button>
      <el-button v-if="!standalone" @click="$router.push('/portal/tasks')">审批待办</el-button>
      <TeacherPersonnelActions v-else :portal="portal" :organization-id="organizationId" @saved="load" />
    </div>
    <el-tabs v-if="portal && !standalone" v-model="tab" @tab-change="value => value === 'cases' && load()">
      <el-tab-pane name="cases" label="我的申请" />
      <el-tab-pane name="start" label="发起申请" />
    </el-tabs>
    <PortalWorkflowStart v-if="!standalone && portal && tab === 'start'"
      flow-code-prefix="EDU_TEACHER_PERSONNEL_" />
    <template v-else-if="!standalone || portal || canView">
      <el-table v-loading="loading" :data="rows" border :empty-text="portal || organizationId ? '暂无申请' : '请先选择学校'">
        <el-table-column prop="employeeName" label="教师" min-width="110" />
        <el-table-column prop="employeeCode" label="工号" min-width="110" />
        <el-table-column label="业务" width="90"><template #default="{ row }">{{ types[row.changeType] }}</template></el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="130" />
        <el-table-column label="办理状态" width="110"><template #default="{ row }">{{ states[row.status] || row.status }}</template></el-table-column>
        <el-table-column v-if="standalone" prop="accountUsername" label="教师账号" min-width="100" />
        <el-table-column v-if="standalone" label="账号状态" width="105">
          <template #default="{ row }">{{ row.accountStatus === 1 ? '启用' : row.accountStatus === 0 ? '停用' : '—' }}</template>
        </el-table-column>
        <el-table-column v-if="standalone" prop="applicant" label="发起人" min-width="125" />
        <el-table-column v-if="standalone" prop="reviewer" label="审核人" min-width="125" />
        <el-table-column prop="reason" label="申请说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="异常与交接提示" min-width="180" />
        <el-table-column label="操作" min-width="140">
          <template #default="{ row }">
            <el-button v-if="!standalone" link type="primary" @click="$router.push('/portal/workflow-instances/' + row.instanceId + '/forms')">流程详情</el-button>
            <el-button v-if="standalone && !portal && canManage && row.teacherStatus === 'ACTIVE'
              && row.status === 'APPLIED' && row.accountStatus === 1 && row.mustChangePassword" link type="primary"
              @click="handover(row)">交接教师账号</el-button>
            <el-button v-if="!portal && row.status === 'APPLY_FAILED'" v-permission="['education:teacher:business:update','education:teacher:business:manage']"
              link type="warning" @click="retry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-button :disabled="page === 0 || loading" @click="turnPage(-1)">上一页</el-button>
        <span>第 {{ page + 1 }} 页</span>
        <el-button :disabled="rows.length < 20 || loading" @click="turnPage(1)">下一页</el-button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.personnel { padding: 24px; }
h2 { margin: 0 0 8px; }
p { color: #64748b; }
.toolbar, .pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 10px 0; }
.pagination { justify-content: flex-end; }
</style>
