<template>
  <div class="portal-leaves">
    <el-page-header @back="$router.push('/portal')"
      ><template #content><strong>我的请假</strong></template></el-page-header
    >
    <el-alert title="新请假仍从流程中心发起；这里展示审批完成的业务台账并办理销假。" type="info" :closable="false" />
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
import { portalLeaveRecords, requestLeaveCancellation } from '../../../api/portal'
const rows = ref([]),
  loading = ref(true)
const load = async () => {
  loading.value = true
  try {
    rows.value = (await portalLeaveRecords()).data || []
  } finally {
    loading.value = false
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
