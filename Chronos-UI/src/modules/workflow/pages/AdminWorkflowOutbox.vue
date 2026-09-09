<template>
  <div class="outbox-page">
    <div class="header">
      <div>
        <h2>流程消息死信</h2>
        <p>查看投递失败事件，并由管理员决定重试或忽略</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="events" v-loading="loading" row-key="id" border>
      <el-table-column prop="eventType" label="事件类型" width="180" />
      <el-table-column prop="aggregateId" label="任务ID" width="220" />
      <el-table-column prop="attempts" label="尝试次数" width="100" />
      <el-table-column prop="lastError" label="最后错误" />
      <el-table-column prop="createTime" label="创建时间" width="190" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="retry(row)">重试</el-button>
          <el-button link type="danger" @click="ignore(row)">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      :total="total"
      @size-change="changePageSize"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ignoreWorkflowOutbox,
  listDeadWorkflowOutbox,
  retryWorkflowOutbox
} from '../../../api/admin'

const events = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const load = async () => {
  loading.value = true
  try {
    const response = await listDeadWorkflowOutbox({ page: page.value - 1, size: pageSize.value })
    events.value = response?.data?.content || response?.data || []
    total.value = response?.data?.totalElements ?? events.value.length
  } finally {
    loading.value = false
  }
  const changePageSize = () => { page.value = 1; load() }
}

const retry = async row => {
  await retryWorkflowOutbox(row.id)
  ElMessage.success('事件已重新进入投递队列')
  await load()
}

const ignore = async row => {
  await ElMessageBox.confirm('忽略后事件不会再次自动投递，确认继续？', '忽略死信', { type: 'warning' })
  await ignoreWorkflowOutbox(row.id)
  ElMessage.success('事件已忽略')
  await load()
}

onMounted(load)
</script>

<style scoped>
.outbox-page { background: #fff; border-radius: 12px; padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.header h2 { margin: 0; }
.header p { color: #8492a6; margin: 8px 0 20px; }
</style>
