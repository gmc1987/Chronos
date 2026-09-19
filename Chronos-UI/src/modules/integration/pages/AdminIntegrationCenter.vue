<template>
  <div class="integration-page">
    <div class="header">
      <div>
        <h2>集成中心</h2>
        <p>管理 HTTP 连接器、同步任务和可追踪失败</p>
      </div>
      <el-button @click="loadAll">刷新</el-button>
    </div>

    <el-alert
      v-if="blocked"
      title="凭证加密服务尚未配置"
      description="连接器凭证保存和连接测试在平台加密服务就绪前不可用；不会使用 Base64 代替加密。"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="连接器" name="connectors">
        <el-table :data="connectors" v-loading="loading" border>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="connectorType" label="类型" width="130" />
          <el-table-column prop="endpointHost" label="目标主机" min-width="220" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="blocked" @click="testConnector(row)">连接测试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="同步任务" name="jobs">
        <el-table :data="jobs" v-loading="loading" border>
          <el-table-column prop="name" label="任务" min-width="180" />
          <el-table-column prop="schedule" label="调度" width="160" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="lastRunStatus" label="最近运行" width="130" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="triggerJob(row)">立即运行</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="运行记录" name="runs">
        <el-table :data="runs" v-loading="loading" border>
          <el-table-column prop="jobName" label="任务" min-width="180" />
          <el-table-column prop="status" label="结果" width="120" />
          <el-table-column prop="successCount" label="成功" width="90" />
          <el-table-column prop="errorCount" label="错误" width="90" />
          <el-table-column prop="startedAt" label="开始时间" width="190" />
          <el-table-column prop="finishedAt" label="结束时间" width="190" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="失败与死信" name="deadLetters">
        <el-table :data="deadLetters" v-loading="loading" border>
          <el-table-column prop="itemKey" label="项目" min-width="180" />
          <el-table-column prop="errorMessage" label="最后错误" min-width="260" />
          <el-table-column prop="attempts" label="尝试次数" width="100" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="replay(row)">人工重放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listIntegrationConnectors,
  listIntegrationDeadLetters,
  listIntegrationRuns,
  listIntegrationSyncJobs,
  replayIntegrationDeadLetter,
  testIntegrationConnector,
  triggerIntegrationSyncJob,
} from '../../../api/admin'

const activeTab = ref('connectors')
const loading = ref(false)
const blocked = ref(false)
const connectors = ref([])
const jobs = ref([])
const runs = ref([])
const deadLetters = ref([])

const rows = response => response?.data?.content || response?.data || []

const loadAll = async () => {
  loading.value = true
  try {
    const [connectorResponse, jobResponse, runResponse, deadLetterResponse] = await Promise.all([
      listIntegrationConnectors({ page: 0, size: 50 }),
      listIntegrationSyncJobs({ page: 0, size: 50 }),
      listIntegrationRuns({ page: 0, size: 50 }),
      listIntegrationDeadLetters({ page: 0, size: 50 }),
    ])
    connectors.value = rows(connectorResponse)
    jobs.value = rows(jobResponse)
    runs.value = rows(runResponse)
    deadLetters.value = rows(deadLetterResponse)
  } finally {
    loading.value = false
  }
}

const testConnector = async row => {
  try {
    await testIntegrationConnector(row.id)
    ElMessage.success('连接测试已完成')
  } catch (error) {
    if (error?.response?.status === 409 || error?.response?.status === 501) blocked.value = true
  }
}

const triggerJob = async row => {
  await triggerIntegrationSyncJob(row.id)
  ElMessage.success('同步任务已触发')
  await loadAll()
}

const replay = async row => {
  await replayIntegrationDeadLetter(row.id)
  ElMessage.success('死信已提交人工重放')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.integration-page { background: #fff; border-radius: 12px; padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.header h2 { margin: 0; }
.header p { color: #8492a6; margin: 8px 0 20px; }
.notice { margin-bottom: 20px; }
</style>
