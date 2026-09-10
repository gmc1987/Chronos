<template>
  <div class="incident-page">
    <div class="header">
      <div>
        <h2>流程事故中心</h2>
        <p>查看 Flowable 自动节点死信，并执行受控重试、跳过或终止</p>
      </div>
      <div class="actions">
        <el-select v-model="status" style="width: 140px" @change="changeStatus">
          <el-option label="待处理" value="OPEN" />
          <el-option label="重试中" value="RETRYING" />
          <el-option label="已解决" value="RESOLVED" />
          <el-option label="全部" value="ALL" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      title="跳过操作仅允许迁移到失败节点的直接后继；存在多个后继时必须填写目标节点 Key。"
      type="warning"
      :closable="false"
      show-icon
      class="notice"
    />

    <el-table :data="rows" v-loading="loading" row-key="id" border>
      <el-table-column prop="flowName" label="流程" min-width="150" />
      <el-table-column prop="businessKey" label="业务标识" min-width="140" />
      <el-table-column label="失败节点" min-width="150">
        <template #default="{ row }">
          {{ row.nodeName || row.nodeKey }}
          <div class="muted">{{ row.nodeKey }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="人工重试" width="100" />
      <el-table-column label="异常" min-width="240">
        <template #default="{ row }">
          <el-tooltip :content="row.errorMessage || '-'" placement="top" :show-after="300">
            <span class="error-text">{{ firstLine(row.errorMessage) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发生时间" width="180" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="info" @click="showExecutions(row)">日志</el-button>
          <template v-if="canManage && row.status === 'OPEN'">
            <el-button link type="primary" @click="retry(row)">重试</el-button>
            <el-button link type="warning" @click="skip(row)">跳过</el-button>
            <el-button link type="danger" @click="terminate(row)">终止</el-button>
          </template>
          <span v-else class="muted">{{ row.resolution || '-' }}</span>
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

    <el-dialog v-model="executionVisible" title="自动节点执行日志" width="900px">
      <el-table :data="executionRows" v-loading="executionLoading" border max-height="520">
        <el-table-column prop="nodeKey" label="节点" width="150" />
        <el-table-column prop="executor" label="执行器" width="130" />
        <el-table-column prop="status" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="errorMessage" label="错误" min-width="240" show-overflow-tooltip />
        <el-table-column prop="startedAt" label="开始时间" width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listWorkflowIncidents,
  listWorkflowExecutions,
  retryWorkflowIncident,
  skipWorkflowIncident,
  terminateWorkflowIncident
} from '../../../api/admin'
import { hasAdminPermission } from '../../../store/auth'

const rows = ref([])
const loading = ref(false)
const status = ref('OPEN')
const executionVisible = ref(false)
const executionLoading = ref(false)
const executionRows = ref([])
const canManage = hasAdminPermission(
  'workflow:incident:manage',
  'workflow:instance:manage',
  'workflow:manage'
)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const load = async () => {
  loading.value = true
  try {
    const response = await listWorkflowIncidents(status.value, { page: page.value - 1, size: pageSize.value })
    rows.value = response?.data?.content || response?.data || []
    total.value = response?.data?.totalElements ?? rows.value.length
  } finally {
    loading.value = false
  }
}

const changePageSize = () => {
  page.value = 1
  load()
}

const changeStatus = () => {
  page.value = 1
  load()
}

const retry = async row => {
  await ElMessageBox.confirm(
    `确认重新投递“${row.nodeName || row.nodeKey}”的 Flowable 作业？`,
    '人工重试',
    { type: 'warning' }
  )
  await retryWorkflowIncident(row.id)
  ElMessage.success('作业已进入 Flowable 可执行队列')
  await load()
}

const showExecutions = async row => {
  executionVisible.value = true
  executionLoading.value = true
  try {
    executionRows.value = (await listWorkflowExecutions(row.instanceId))?.data || []
  } finally {
    executionLoading.value = false
  }
}

const skip = async row => {
  const result = await ElMessageBox.prompt(
    '只有一个后继时可留空；存在分支时请输入直接后继节点 Key。',
    '跳过失败节点',
    { inputPlaceholder: '目标节点 Key（可选）', type: 'warning' }
  )
  await skipWorkflowIncident(row.id, {
    targetNodeKey: result.value?.trim() || null,
    reason: '管理员在事故中心执行跳过'
  })
  ElMessage.success('流程已从后继节点继续执行')
  await load()
}

const terminate = async row => {
  const result = await ElMessageBox.prompt(
    '终止后流程不能继续，请填写终止原因。',
    '终止流程实例',
    {
      inputPattern: /\S+/,
      inputErrorMessage: '终止原因不能为空',
      type: 'error'
    }
  )
  await terminateWorkflowIncident(row.id, { reason: result.value.trim() })
  ElMessage.success('流程实例已终止')
  await load()
}

const firstLine = value => String(value || '-').split('\n')[0]
const statusLabel = value => ({
  OPEN: '待处理',
  RETRYING: '重试中',
  RESOLVED: '已解决'
}[value] || value)
const statusType = value => ({
  OPEN: 'danger',
  RETRYING: 'warning',
  RESOLVED: 'success'
}[value] || 'info')

onMounted(load)
</script>

<style scoped>
.incident-page {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h2 {
  margin: 0;
}

.header p {
  color: #8492a6;
  margin: 8px 0 20px;
}

.actions {
  display: flex;
  gap: 10px;
}

.notice {
  margin-bottom: 16px;
}

.muted {
  color: #909399;
  font-size: 12px;
}

.error-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
