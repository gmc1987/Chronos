<template>
  <div class="leave-management">
    <header>
      <div>
        <h1>教职工假期管理</h1>
        <p>管理年度假期额度、处理销假并向考勤提供有效请假记录</p>
      </div>
      <div class="query-actions">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </header>

    <div class="metrics">
      <el-card v-for="item in metricItems" :key="item.label" shadow="never">
        <small>{{ item.label }}</small>
        <strong>{{ item.value }}</strong>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-title">
          <strong>员工年度假期额度</strong>
          <div>
            <el-select
              v-model="selectedEmployeeId"
              filterable
              placeholder="选择教职工"
              style="width: 220px"
              @change="loadBalances"
            >
              <el-option
                v-for="item in employeeOptions"
                :key="item.id"
                :label="`${item.employeeName}（${item.employeeCode}）`"
                :value="item.id"
              />
            </el-select>
            <el-input-number v-model="balanceYear" :min="2000" :max="2100" @change="loadBalances" />
            <el-button type="primary" :disabled="!selectedEmployeeId" @click="openAdjustment">调整额度</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!selectedEmployeeId" description="请选择教职工" />
      <el-table v-else :data="balances" border>
        <el-table-column label="假别" min-width="120">
          <template #default="{ row }">{{ leaveTypeLabel(row.leaveType) }}</template>
        </el-table-column>
        <el-table-column prop="entitlementDays" label="年度额度" width="105" />
        <el-table-column prop="carryoverDays" label="结转" width="90" />
        <el-table-column prop="adjustmentDays" label="调整" width="90" />
        <el-table-column prop="consumedDays" label="已使用" width="90" />
        <el-table-column prop="availableDays" label="可用余额" width="105" />
      </el-table>
      <el-collapse v-if="selectedEmployeeId" class="ledger">
        <el-collapse-item title="额度变动记录">
          <el-table :data="adjustments" border>
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="假别" width="110">
              <template #default="{ row }">{{ leaveTypeLabel(row.leaveType) }}</template>
            </el-table-column>
            <el-table-column prop="changeDays" label="变动天数" width="100" />
            <el-table-column prop="reason" label="原因" min-width="180" />
            <el-table-column prop="operatorUsername" label="操作人" width="130" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-card shadow="never">
      <template #header><strong>待审核销假</strong></template>
      <el-empty v-if="!rows.length" description="暂无待审核销假" />
      <el-table v-else :data="rows" border>
        <el-table-column prop="applicantType" label="人员类型" width="100" />
        <el-table-column prop="businessKey" label="申请单号" min-width="150" />
        <el-table-column prop="startDate" label="开始" width="110" />
        <el-table-column prop="endDate" label="原结束" width="110" />
        <el-table-column prop="actualEndDate" label="实际结束" width="110" />
        <el-table-column prop="cancellationReason" label="销假原因" min-width="180" />
        <AdaptiveActionColumn label="操作" width="140">
          <template #default="scope">
            <el-button link type="success" @click="decide(scope.row, true)">通过</el-button>
            <el-button link type="danger" @click="decide(scope.row, false)">驳回</el-button>
          </template>
        </AdaptiveActionColumn>
      </el-table>
    </el-card>

    <el-dialog v-model="adjustmentDialog" title="调整假期额度" width="460px">
      <el-form :model="adjustmentForm" label-width="90px">
        <el-form-item label="假别">
          <el-select v-model="adjustmentForm.leaveType" style="width: 100%">
            <el-option v-for="item in trackedLeaveTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整天数">
          <el-input-number v-model="adjustmentForm.changeDays" :precision="2" :step="0.5" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="adjustmentForm.reason" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustmentDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAdjustment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adjustStaffLeaveBalance,
  decideLeaveCancellation,
  employees,
  leaveStatistics,
  pendingLeaveCancellations,
  staffLeaveBalanceAdjustments,
  staffLeaveBalances,
} from '../../../api/admin'

const leaveTypes = [
  { label: '年假', value: 'ANNUAL' },
  { label: '事假', value: 'PERSONAL' },
  { label: '病假', value: 'SICK' },
  { label: '公假', value: 'OFFICIAL' },
  { label: '婚假', value: 'MARRIAGE' },
  { label: '产假', value: 'MATERNITY' },
  { label: '陪产假', value: 'PATERNITY' },
  { label: '调休', value: 'COMPENSATORY' },
]
const trackedLeaveTypes = leaveTypes.filter((item) => item.value !== 'OFFICIAL')
const today = new Date()
const first = new Date(today.getFullYear(), today.getMonth(), 1)
const format = (value) =>
  `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
const range = ref([format(first), format(today)])
const statistics = ref({})
const rows = ref([])
const employeeOptions = ref([])
const selectedEmployeeId = ref('')
const balanceYear = ref(today.getFullYear())
const balances = ref([])
const adjustments = ref([])
const adjustmentDialog = ref(false)
const adjustmentForm = ref({ leaveType: 'ANNUAL', changeDays: 0, reason: '' })

const leaveTypeLabel = (value) => leaveTypes.find((item) => item.value === value)?.label || value
const metricItems = computed(() => [
  { label: '请假申请', value: statistics.value.requestCount || 0 },
  { label: '教职工请假', value: statistics.value.staffCount || statistics.value.teacherCount || 0 },
  { label: '学生请假', value: statistics.value.studentCount || 0 },
  { label: '已销假', value: statistics.value.cancelledCount || 0 },
  { label: '请假天数', value: statistics.value.leaveDays || 0 },
])

const load = async () => {
  const [stats, pending] = await Promise.all([
    leaveStatistics(range.value[0], range.value[1]),
    pendingLeaveCancellations(),
  ])
  statistics.value = stats.data || {}
  rows.value = pending.data || []
}

const loadBalances = async () => {
  if (!selectedEmployeeId.value) return
  const [balanceResult, adjustmentResult] = await Promise.all([
    staffLeaveBalances(selectedEmployeeId.value, balanceYear.value),
    staffLeaveBalanceAdjustments(selectedEmployeeId.value, balanceYear.value),
  ])
  balances.value = balanceResult.data || []
  adjustments.value = adjustmentResult.data || []
}

const openAdjustment = () => {
  adjustmentForm.value = { leaveType: 'ANNUAL', changeDays: 0, reason: '' }
  adjustmentDialog.value = true
}

const saveAdjustment = async () => {
  if (!adjustmentForm.value.changeDays || !adjustmentForm.value.reason?.trim()) {
    return ElMessage.warning('请填写非零调整天数和调整原因')
  }
  await adjustStaffLeaveBalance(selectedEmployeeId.value, {
    ...adjustmentForm.value,
    year: balanceYear.value,
  })
  adjustmentDialog.value = false
  ElMessage.success('假期额度已调整')
  await loadBalances()
}

const decide = async (row, approved) => {
  const { value = '' } = await ElMessageBox.prompt(
    approved ? '可填写审核意见' : '请输入驳回原因',
    approved ? '通过销假' : '驳回销假',
    {
      inputType: 'textarea',
      inputValidator: (text) => approved || Boolean(text?.trim()) || '驳回原因不能为空',
    },
  )
  await decideLeaveCancellation(row.id, { approved, comment: value })
  ElMessage.success('销假申请已处理')
  await Promise.all([load(), loadBalances()])
}

onMounted(async () => {
  const employeeResult = await employees({ page: 0, size: 500 })
  employeeOptions.value = employeeResult?.data?.content || employeeResult?.data || []
  await load()
})
</script>

<style scoped>
.leave-management {
  display: grid;
  gap: 18px;
  padding: 24px;
}
.leave-management header,
.card-title,
.query-actions,
.card-title > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.leave-management h1 {
  margin: 0;
  color: #263f49;
}
.leave-management p {
  margin: 5px 0 0;
  color: #819097;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.metrics :deep(.el-card__body) {
  display: grid;
  gap: 6px;
}
.metrics small {
  color: #819097;
}
.metrics strong {
  font-size: 24px;
  color: #263f49;
}
.ledger {
  margin-top: 16px;
}
</style>
