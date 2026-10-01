<template>
  <div class="portal-leaves">
    <el-page-header @back="$router.push('/portal')"
      ><template #content><strong>我的请假</strong></template></el-page-header
    >
    <el-alert title="请假提交后进入真实审批流程；审批完成后生成业务台账并发送通知。" type="info" :closable="false" />
    <el-card shadow="never">
      <template #header><strong>发起请假</strong></template>
      <el-form :model="form" label-width="90px" inline>
        <el-form-item v-if="children.length" label="学生">
          <el-select v-model="form.studentId" placeholder="选择学生" style="width: 180px">
            <el-option v-for="child in children" :key="child.studentId" :label="child.studentName" :value="child.studentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型"><el-select v-model="form.leaveType" placeholder="选择类型" style="width: 140px">
          <el-option label="事假" value="PERSONAL" /><el-option label="病假" value="SICK" /><el-option label="公假" value="OFFICIAL" />
        </el-select></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" type="textarea" placeholder="填写请假原因" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="submitting" @click="submitLeave">提交审批</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-empty v-else-if="!rows.length" description="暂无已审批请假记录" />
    <el-table v-else :data="rows" border>
      <el-table-column prop="businessKey" label="申请单号" min-width="150" /><el-table-column
        prop="leaveType"
        label="请假类型"
        width="110"
      />
      <el-table-column prop="startDate" label="开始日期" width="120" /><el-table-column
        prop="endDate"
        label="结束日期"
        width="120"
      />
      <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip /><el-table-column
        prop="status"
        label="状态"
        width="100"
      />
      <el-table-column prop="cancellationStatus" label="销假状态" width="110" /><el-table-column label="操作" width="90"
        ><template #default="scope"
          ><el-button v-if="canCancel(scope.row)" link type="warning" @click="cancelLeave(scope.row)"
            >申请销假</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalFamilyChildren, portalLeaveRecords, requestLeaveCancellation, startPortalLeave } from '../../../api/portal'
const rows = ref([]),
  loading = ref(true),
  children = ref([]),
  submitting = ref(false),
  dates = ref([]),
  form = ref({ leaveType: '', reason: '', studentId: '' })
const load = async () => {
  loading.value = true
  try {
    const [records, family] = await Promise.all([
      portalLeaveRecords(),
      portalFamilyChildren().catch(() => ({ data: [] })),
    ])
    rows.value = records.data || []
    children.value = family.data || []
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
const canCancel = (row) => row.status === 'APPROVED' && row.cancellationStatus === 'NONE'
const cancelLeave = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入销假原因', '申请销假', {
    inputType: 'textarea',
    inputValidator: (text) => Boolean(text?.trim()) || '销假原因不能为空'
  })
  await requestLeaveCancellation(row.id, value)
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
</style>
