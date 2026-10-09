<template>
  <div class="trip-page">
    <el-page-header @back="$router.push('/portal')">
      <template #content><strong>我的出差</strong></template>
    </el-page-header>

    <el-alert
      :title="integration.message"
      :type="integration.submissionAllowed ? 'info' : 'error'"
      :closable="false"
      show-icon
    />

    <el-card v-if="adminConfigVisible" shadow="never">
      <template #header><strong>出差流程配置</strong></template>
      <div class="config-row">
        <div>
          <b>必须对接财务模块</b>
          <p>打开后，只有财务适配器可用且预算预校验通过时才能发起流程。</p>
        </div>
        <el-switch
          v-model="financeRequired"
          active-text="必须对接"
          inactive-text="允许非财务模式"
          @change="saveConfiguration"
        />
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><strong>发起出差申请</strong></template>
      <el-form :model="form" label-width="110px">
        <div class="form-grid">
          <el-form-item label="出差地点">
            <el-input v-model="form.destination" />
          </el-form-item>
          <el-form-item label="出差日期">
            <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="预算项目">
            <el-input v-model="form.budgetProjectCode" placeholder="财务强制模式下必填" />
          </el-form-item>
          <el-form-item label="成本中心">
            <el-input v-model="form.costCenterCode" placeholder="财务强制模式下必填" />
          </el-form-item>
          <el-form-item label="交通费预算">
            <el-input-number v-model="form.transportAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="住宿费预算">
            <el-input-number v-model="form.accommodationAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="餐费及补助">
            <el-input-number v-model="form.mealAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="其他费用">
            <el-input-number v-model="form.otherAmount" :min="0" :precision="2" />
          </el-form-item>
        </div>
        <el-form-item label="预计总额">
          <strong class="amount">CNY {{ estimatedAmount.toFixed(2) }}</strong>
        </el-form-item>
        <el-form-item label="出差事由">
          <el-input v-model="form.purpose" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!integration.submissionAllowed"
            @click="submit"
          >提交审批</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><strong>出差记录</strong></template>
      <el-empty v-if="!rows.length" description="暂无出差记录" />
      <el-table v-else :data="rows" border>
        <el-table-column prop="businessKey" label="申请单号" min-width="190" />
        <el-table-column prop="destination" label="地点" min-width="120" />
        <el-table-column prop="startDate" label="开始日期" width="115" />
        <el-table-column prop="endDate" label="结束日期" width="115" />
        <el-table-column prop="purpose" label="事由" min-width="180" show-overflow-tooltip />
        <el-table-column label="预计费用" width="120">
          <template #default="{ row }">{{ row.currency }} {{ money(row.estimatedAmount) }}</template>
        </el-table-column>
        <el-table-column label="审批状态" width="105">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="财务状态" width="110">
          <template #default="{ row }">{{ financeLabel(row.financeStatus) }}</template>
        </el-table-column>
        <AdaptiveActionColumn label="操作" width="130">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdraw(row)">撤回</el-button>
            <el-button v-if="row.status === 'APPROVED'" link type="warning" @click="cancel(row)">取消出差</el-button>
          </template>
        </AdaptiveActionColumn>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  businessTripConfiguration,
  updateBusinessTripConfiguration,
} from '../../../api/admin'
import {
  businessTripIntegrationStatus,
  cancelBusinessTrip,
  portalBusinessTrips,
  startBusinessTrip,
  withdrawBusinessTrip,
} from '../../../api/portal'

const rows = ref([])
const dates = ref([])
const submitting = ref(false)
const integration = ref({
  financeRequired: false,
  financeConnected: false,
  submissionAllowed: true,
  message: '正在检查财务集成状态',
})
const adminConfigVisible = ref(false)
const financeRequired = ref(false)
const form = reactive({
  destination: '',
  purpose: '',
  budgetProjectCode: '',
  costCenterCode: '',
  currency: 'CNY',
  transportAmount: 0,
  accommodationAmount: 0,
  mealAmount: 0,
  otherAmount: 0,
})

const estimatedAmount = computed(() =>
  Number(form.transportAmount || 0)
  + Number(form.accommodationAmount || 0)
  + Number(form.mealAmount || 0)
  + Number(form.otherAmount || 0))
const money = value => Number(value || 0).toFixed(2)
const statusLabel = value => ({
  PENDING: '审批中',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已取消',
}[value] || value)
const financeLabel = value => ({
  NOT_REQUIRED: '未要求',
  PRECHECKED: '预校验通过',
  RESERVED: '预算已冻结',
  RELEASED: '预算已释放',
  WRITTEN_OFF: '已核销',
}[value] || value)

const load = async () => {
  const [tripResult, statusResult] = await Promise.all([
    portalBusinessTrips(),
    businessTripIntegrationStatus(),
  ])
  rows.value = tripResult.data || []
  integration.value = statusResult.data || integration.value
  financeRequired.value = Boolean(integration.value.financeRequired)
  try {
    const configResult = await businessTripConfiguration()
    adminConfigVisible.value = true
    financeRequired.value = Boolean(configResult.data?.financeRequired)
  } catch {
    adminConfigVisible.value = false
  }
}

const saveConfiguration = async value => {
  await updateBusinessTripConfiguration({ financeRequired: value })
  ElMessage.success('出差财务对接策略已更新')
  await load()
}

const submit = async () => {
  if (!form.destination.trim() || !form.purpose.trim() || dates.value.length !== 2) {
    return ElMessage.warning('请填写出差地点、日期和事由')
  }
  submitting.value = true
  try {
    await startBusinessTrip({
      ...form,
      startDate: dates.value[0],
      endDate: dates.value[1],
    })
    ElMessage.success('出差申请已提交审批')
    form.destination = ''
    form.purpose = ''
    form.budgetProjectCode = ''
    form.costCenterCode = ''
    form.transportAmount = 0
    form.accommodationAmount = 0
    form.mealAmount = 0
    form.otherAmount = 0
    dates.value = []
    await load()
  } finally {
    submitting.value = false
  }
}

const withdraw = async row => {
  const { value = '' } = await ElMessageBox.prompt('可填写撤回原因', '撤回出差申请', {
    inputType: 'textarea',
  })
  await withdrawBusinessTrip(row.id, value)
  ElMessage.success('出差申请已撤回')
  await load()
}

const cancel = async row => {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消出差', {
    inputType: 'textarea',
    inputValidator: text => Boolean(text?.trim()) || '取消原因不能为空',
  })
  await cancelBusinessTrip(row.id, value)
  ElMessage.success('出差已取消')
  await load()
}

onMounted(load)
</script>

<style scoped>
.trip-page {
  display: grid;
  gap: 18px;
  padding: 24px;
}
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.config-row p {
  margin: 6px 0 0;
  color: #819097;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.amount {
  color: #2f6f78;
  font-size: 20px;
}
@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
