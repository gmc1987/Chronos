<template>
  <div class="classroom-reservations">
    <el-page-header @back="$router.push(customer ? '/admin/delivery-access' : '/portal/education/schedule')">
      <template #content><strong>我的教室申请</strong></template>
    </el-page-header>

    <div class="toolbar">
      <el-alert
        :title="canApply ? '提交后由本校场地管理人员审核，审核通过后占用教室。' : '请在流程中心发起申请。'"
        type="info"
        :closable="false"
      />
      <el-button v-if="canApply" type="primary" @click="open">发起教室申请</el-button>
      <el-button v-else-if="!customer" type="primary" @click="$router.push('/portal/workflows')">发起教室申请</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!rows.length" description="暂无教室申请记录" />
    <el-table v-else :data="rows" border>
      <el-table-column prop="businessKey" label="申请单号" min-width="150" />
      <el-table-column prop="semesterCode" label="学期" width="130" />
      <el-table-column prop="classroomName" label="教室" min-width="150" />
      <el-table-column prop="usageDate" label="使用日期" width="120" />
      <el-table-column label="使用时段" width="120">
        <template #default="scope">
          第 {{ scope.row.startPeriod }} -
          {{ scope.row.startPeriod + scope.row.durationPeriods - 1 }} 节
        </template>
      </el-table-column>
      <el-table-column prop="attendeeCount" label="人数" width="80" />
      <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)">{{ statusName(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="failureMessage" label="回写说明" min-width="120" show-overflow-tooltip />
      <el-table-column prop="reviewNote" label="审核意见" min-width="180" show-overflow-tooltip />
      <AdaptiveActionColumn label="操作" width="90">
        <template #default="scope">
          <el-button v-if="canCancel(scope.row)" link type="warning" @click="cancel(scope.row)">
            撤销
          </el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
    <el-dialog v-model="visible" title="教室预约申请" width="min(600px, 95vw)">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="教室">
          <el-select v-model="form.classroomId" filterable placeholder="选择教室" @change="loadTerms">
            <el-option v-for="room in rooms" :key="room.value" :label="room.label" :value="room.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.semesterCode" placeholder="选择学期" :disabled="!form.classroomId" :loading="loadingTerms">
            <el-option v-for="term in terms" :key="term.termCode" :label="term.termName" :value="term.termCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用日期">
          <el-date-picker v-model="form.usageDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="开始节次"><el-input-number v-model="form.startPeriod" :min="1" :max="30" /></el-form-item>
        <el-form-item label="持续节数"><el-input-number v-model="form.durationPeriods" :min="1" :max="30" /></el-form-item>
        <el-form-item label="使用人数"><el-input-number v-model="form.attendeeCount" :min="1" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" type="textarea" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!canCheck" :loading="busy === 'check'" @click="send('check')">检查时段</el-button>
        <el-button type="primary" :disabled="!canCheck || !form.purpose?.trim()" :loading="busy === 'submit'" @click="send('submit')">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { http } from '../../../api/http'
import { getDeliveryContext, hasAdminPermission } from '../../../store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { todayLocalDate } from '../../../utils/localDate'
import {
  cancelPortalClassroomReservation,
  portalClassroomReservations,
} from '../../../api/portal'

const endpoint = '/portal/education/classroom-reservations'
const customer = getDeliveryContext().mode === 'customer'
const canApply = hasAdminPermission('education:venue-reservation:apply', 'education:resource:venue:manage')
const rooms = ref([])
const terms = ref([])
const form = reactive({})
const visible = ref(false)
const loadingTerms = ref(false)
const busy = ref('')
const canCheck = computed(() => !busy.value && form.classroomId && form.semesterCode && form.usageDate)
const rows = ref([])
const loading = ref(true)

const load = async () => {
  loading.value = true
  try {
    rows.value = (await portalClassroomReservations()).data || []
  } finally {
    loading.value = false
  }
}

const canCancel = (row) => ['ACTIVE', 'PENDING'].includes(row.status) && row.usageDate > todayLocalDate()
const statusName = (status) => ({
  PENDING: '待审核',
  REJECTED: '已驳回',
  ACTIVE: '已生效',
  CANCELLED: '已撤销',
  APPLY_FAILED: '回写失败',
  APPLYING: '处理中',
}[status] || status)
const statusType = (status) => ({
  ACTIVE: 'success',
  CANCELLED: 'info',
  APPLY_FAILED: 'danger',
  REJECTED: 'danger',
}[status] || 'warning')

async function open() {
  rooms.value = (await http.get('/portal/education/classrooms/options')).data || []
  terms.value = []
  Object.assign(form, { classroomId: '', semesterCode: '', usageDate: '',
    startPeriod: 1, durationPeriods: 1, attendeeCount: 1, purpose: '' })
  visible.value = true
}

async function loadTerms(classroomId) {
  terms.value = []
  form.semesterCode = ''
  loadingTerms.value = true
  try {
    const result = await http.get(endpoint + '/term-options?' + new URLSearchParams({ classroomId }))
    if (form.classroomId !== classroomId) return
    terms.value = result.data || []
    if (terms.value.length === 1) form.semesterCode = terms.value[0].termCode
    if (!terms.value.length) ElMessage.warning('该教室所属学校还没有启用学期，请联系场地管理人员')
  } finally {
    if (form.classroomId === classroomId) loadingTerms.value = false
  }
}

async function send(mode) {
  busy.value = mode
  try {
    await http.post(mode === 'check' ? endpoint + '/check' : endpoint, { ...form })
    if (mode === 'check') {
      ElMessage.success('该时段当前可用，审核通过后正式占用')
    } else {
      visible.value = false
      ElMessage.success('申请已提交')
      await load()
    }
  } finally {
    busy.value = ''
  }
}

const cancel = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入撤销原因', '撤销教室申请', {
    inputType: 'textarea',
    inputValidator: (text) => Boolean(text?.trim()) || '撤销原因不能为空',
  })
  await cancelPortalClassroomReservation(row.id, value)
  ElMessage.success('教室申请已撤销，资源已经释放')
  await load()
}

onMounted(load)
</script>

<style scoped>
.classroom-reservations {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.toolbar .el-alert {
  flex: 1;
}
</style>
