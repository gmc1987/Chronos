<template>
  <div class="classroom-reservations">
    <el-page-header @back="$router.push('/portal/education/schedule')">
      <template #content><strong>我的教室申请</strong></template>
    </el-page-header>

    <div class="toolbar">
      <el-alert
        title="新申请从流程中心发起；审批完成后才会形成正式教室占用。"
        type="info"
        :closable="false"
      />
      <el-button type="primary" @click="$router.push('/portal/workflows')">发起教室申请</el-button>
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
      <el-table-column prop="failureMessage" label="回写说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="90">
        <template #default="scope">
          <el-button v-if="canCancel(scope.row)" link type="warning" @click="cancel(scope.row)">
            撤销
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelPortalClassroomReservation,
  portalClassroomReservations,
} from '../../../api/portal'

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

const canCancel = (row) => row.status === 'ACTIVE' && row.usageDate > new Date().toISOString().slice(0, 10)
const statusName = (status) => ({
  ACTIVE: '已生效',
  CANCELLED: '已撤销',
  APPLY_FAILED: '回写失败',
  APPLYING: '处理中',
}[status] || status)
const statusType = (status) => ({
  ACTIVE: 'success',
  CANCELLED: 'info',
  APPLY_FAILED: 'danger',
}[status] || 'warning')

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
