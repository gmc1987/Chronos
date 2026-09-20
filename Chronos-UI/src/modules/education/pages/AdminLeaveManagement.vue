<template>
  <div class="leave-management">
    <header>
      <div>
        <h1>请假与销假管理</h1>
        <p>查看期间统计并处理教师、学生销假申请</p>
      </div>
      <div>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" /><el-button
          type="primary"
          @click="load"
          >查询</el-button
        >
      </div>
    </header>
    <div class="metrics">
      <el-card v-for="item in metricItems" :key="item.label" shadow="never"
        ><small>{{ item.label }}</small
        ><strong>{{ item.value }}</strong></el-card
      >
    </div>
    <el-card shadow="never"
      ><template #header><strong>待审核销假</strong></template
      ><el-empty v-if="!rows.length" description="暂无待审核销假" /><el-table v-else :data="rows" border
        ><el-table-column prop="applicantType" label="人员类型" width="100" /><el-table-column
          prop="businessKey"
          label="申请单号"
          min-width="150"
        /><el-table-column prop="startDate" label="开始" width="110" /><el-table-column
          prop="endDate"
          label="结束"
          width="110"
        /><el-table-column prop="cancellationReason" label="销假原因" min-width="180" /><el-table-column
          label="操作"
          width="140"
          ><template #default="scope"
            ><el-button link type="success" @click="decide(scope.row, true)">通过</el-button
            ><el-button link type="danger" @click="decide(scope.row, false)">驳回</el-button></template
          ></el-table-column
        ></el-table
      ></el-card
    >
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { decideLeaveCancellation, leaveStatistics, pendingLeaveCancellations } from '../../../api/admin'
const today = new Date(),
  first = new Date(today.getFullYear(), today.getMonth(), 1)
const format = (value) =>
  `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
const range = ref([format(first), format(today)]),
  statistics = ref({}),
  rows = ref([])
const metricItems = computed(() => [
  { label: '请假申请', value: statistics.value.requestCount || 0 },
  { label: '教师请假', value: statistics.value.teacherCount || 0 },
  { label: '学生请假', value: statistics.value.studentCount || 0 },
  { label: '已销假', value: statistics.value.cancelledCount || 0 },
  { label: '请假天数', value: statistics.value.leaveDays || 0 }
])
const load = async () => {
  const [stats, pending] = await Promise.all([
    leaveStatistics(range.value[0], range.value[1]),
    pendingLeaveCancellations()
  ])
  statistics.value = stats.data || {}
  rows.value = pending.data || []
}
const decide = async (row, approved) => {
  const { value = '' } = await ElMessageBox.prompt(
    approved ? '可填写审核意见' : '请输入驳回原因',
    approved ? '通过销假' : '驳回销假',
    { inputType: 'textarea', inputValidator: (text) => approved || Boolean(text?.trim()) || '驳回原因不能为空' }
  )
  await decideLeaveCancellation(row.id, { approved, comment: value })
  ElMessage.success('销假申请已处理')
  await load()
}
onMounted(load)
</script>
<style scoped>
.leave-management {
  display: grid;
  gap: 18px;
  padding: 24px;
}
.leave-management header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
</style>
