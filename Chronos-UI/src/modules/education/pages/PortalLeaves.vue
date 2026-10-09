<template>
  <div class="portal-leaves">
    <el-page-header @back="$router.push('/portal')">
      <template #content><strong>我的请假</strong></template>
    </el-page-header>
    <el-alert
      title="请假提交后进入审批；教职工假期将在审批通过后扣减，撤回、驳回或销假会按规则释放或返还。"
      type="info"
      :closable="false"
    />

    <section v-if="balances.length" class="balance-grid">
      <el-card v-for="item in balances" :key="item.id" shadow="never">
        <small>{{ leaveTypeLabel(item.leaveType) }}</small>
        <strong>{{ number(item.availableDays) }} 天</strong>
        <span>年度额度 {{ number(item.entitlementDays + item.carryoverDays + item.adjustmentDays) }} 天 · 已用 {{ number(item.consumedDays) }} 天</span>
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header><strong>发起请假</strong></template>
      <el-form :model="form" label-width="90px" inline>
        <el-form-item v-if="children.length" label="学生">
          <el-select v-model="form.studentId" placeholder="选择学生" style="width: 180px">
            <el-option v-for="child in children" :key="child.studentId" :label="child.studentName" :value="child.studentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.leaveType" placeholder="选择类型" style="width: 150px">
            <el-option v-for="item in leaveTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" type="textarea" placeholder="填写请假原因" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitLeave">提交审批</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!rows.length" description="暂无请假记录" />
    <el-table v-else :data="rows" border>
      <el-table-column prop="businessKey" label="申请单号" min-width="150" />
      <el-table-column label="请假类型" width="110">
        <template #default="{ row }">{{ leaveTypeLabel(row.leaveType) }}</template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始日期" width="120" />
      <el-table-column prop="endDate" label="原结束日期" width="120" />
      <el-table-column prop="actualEndDate" label="实际结束日期" width="125" />
      <el-table-column prop="requestedDays" label="天数" width="75" />
      <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="销假状态" width="110">
        <template #default="{ row }">{{ cancellationLabel(row.cancellationStatus) }}</template>
      </el-table-column>
      <AdaptiveActionColumn label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdraw(row)">撤回</el-button>
          <el-button v-if="canCancel(row)" link type="warning" @click="cancelLeave(row)">申请销假</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictionaryOptions } from '../../../api/admin'
import {
  portalFamilyChildren,
  portalLeaveBalances,
  portalLeaveRecords,
  requestLeaveCancellation,
  startPortalLeave,
  withdrawPortalLeave,
} from '../../../api/portal'

const fallbackTypes = [
  { label: '年假', value: 'ANNUAL' },
  { label: '事假', value: 'PERSONAL' },
  { label: '病假', value: 'SICK' },
  { label: '公假', value: 'OFFICIAL' },
  { label: '婚假', value: 'MARRIAGE' },
  { label: '产假', value: 'MATERNITY' },
  { label: '陪产假', value: 'PATERNITY' },
  { label: '调休', value: 'COMPENSATORY' },
]
const rows = ref([])
const balances = ref([])
const loading = ref(true)
const children = ref([])
const leaveTypes = ref(fallbackTypes)
const submitting = ref(false)
const dates = ref([])
const form = ref({ leaveType: '', reason: '', studentId: '' })
const currentYear = new Date().getFullYear()

const number = (value) => Number(value || 0).toFixed(2).replace(/\.?0+$/, '')
const leaveTypeLabel = (value) => leaveTypes.value.find((item) => item.value === value)?.label || value
const statusLabel = (value) => ({
  PENDING: '审批中',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已销假',
  PARTIALLY_CANCELLED: '部分销假',
}[value] || value)
const cancellationLabel = (value) => ({
  NONE: '无',
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}[value] || value)

const load = async () => {
  loading.value = true
  try {
    const [records, family, balanceResult, typeResult] = await Promise.all([
      portalLeaveRecords(),
      portalFamilyChildren().catch(() => ({ data: [] })),
      portalLeaveBalances(currentYear).catch(() => ({ data: [] })),
      dictionaryOptions('EDU_LEAVE_TYPE').catch(() => ({ data: [] })),
    ])
    rows.value = records.data || []
    children.value = family.data || []
    balances.value = balanceResult.data || []
    if (typeResult.data?.length) {
      leaveTypes.value = typeResult.data.map((item) => ({ label: item.dictName, value: item.dictValue }))
    }
  } finally {
    loading.value = false
  }
}

const submitLeave = async () => {
  if (!form.value.leaveType || dates.value.length !== 2 || !form.value.reason?.trim()) {
    return ElMessage.warning('请完整填写请假类型、日期和原因')
  }
  submitting.value = true
  try {
    await startPortalLeave({
      ...form.value,
      startDate: dates.value[0],
      endDate: dates.value[1],
    })
    ElMessage.success('请假申请已提交审批')
    form.value.reason = ''
    dates.value = []
    await load()
  } finally {
    submitting.value = false
  }
}

const withdraw = async (row) => {
  const { value = '' } = await ElMessageBox.prompt('可填写撤回原因', '撤回请假', { inputType: 'textarea' })
  await withdrawPortalLeave(row.id, value)
  ElMessage.success('请假申请已撤回')
  await load()
}

const canCancel = (row) => row.status === 'APPROVED' && ['NONE', 'REJECTED'].includes(row.cancellationStatus)
const cancelLeave = async (row) => {
  const endResult = await ElMessageBox.prompt(
    `请输入实际休假结束日期（${row.startDate} 至 ${row.endDate}），留空表示整单销假`,
    '申请销假',
    {
      inputPlaceholder: 'YYYY-MM-DD',
      inputValidator: (text) => {
        if (!text) return true
        if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return '请输入 YYYY-MM-DD 格式日期'
        return text < row.endDate || '实际结束日期必须早于原结束日期'
      },
    },
  )
  const reasonResult = await ElMessageBox.prompt('请输入销假原因', '申请销假', {
    inputType: 'textarea',
    inputValidator: (text) => Boolean(text?.trim()) || '销假原因不能为空',
  })
  await requestLeaveCancellation(row.id, {
    reason: reasonResult.value,
    actualEndDate: endResult.value || undefined,
  })
  ElMessage.success('销假申请已提交')
  await load()
}

onMounted(load)
</script>

<style scoped>
.portal-leaves {
  display: grid;
  gap: 18px;
  padding: 24px;
}
.balance-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.balance-grid :deep(.el-card__body) {
  display: grid;
  gap: 6px;
}
.balance-grid small,
.balance-grid span {
  color: #819097;
}
.balance-grid strong {
  color: #263f49;
  font-size: 24px;
}
</style>
