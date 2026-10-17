<template>
  <div class="integration-page">
    <div class="header">
      <div>
        <h2>集成中心</h2>
        <p>管理连接器、增量游标、同步运行、对账和可重放失败</p>
      </div>
      <el-button @click="loadAll">刷新</el-button>
    </div>

    <el-alert
      v-if="blocked"
      title="凭证加密服务尚未配置"
      description="外部连接器凭证保存和连接测试在平台加密服务就绪前不可用；可信仿真不需要外部凭证。"
      type="warning"
      show-icon
      :closable="false"
      class="notice"
    />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="连接器" name="connectors">
        <el-table :data="connectors" v-loading="loading" border>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="type" label="类型" width="90" />
          <el-table-column prop="providerCode" label="Provider" width="180" />
          <el-table-column prop="baseUrl" label="目标地址" min-width="240" show-overflow-tooltip />
          <el-table-column prop="healthStatus" label="健康状态" width="120" />
          <el-table-column prop="status" label="状态" width="100" />
          <AdaptiveActionColumn label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="blocked && row.providerCode !== 'TRUSTED_SIMULATION'" @click="testConnector(row)">连接测试</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="同步任务" name="jobs">
        <el-table :data="jobs" v-loading="loading" border>
          <el-table-column prop="name" label="任务" min-width="180" />
          <el-table-column prop="objectType" label="对象" width="120" />
          <el-table-column prop="cronExpression" label="调度" width="150" />
          <el-table-column prop="batchSize" label="批量" width="80" />
          <el-table-column prop="cursorValue" label="当前游标" min-width="140">
            <template #default="{ row }">{{ row.cursorValue || '起点' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <AdaptiveActionColumn label="操作" width="190">
            <template #default="{ row }">
              <el-button link type="info" @click="dryRun(row)">预检</el-button>
              <el-button link type="primary" @click="triggerJob(row)">立即运行</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="运行记录" name="runs">
        <el-table :data="runs" v-loading="loading" border>
          <el-table-column prop="jobName" label="任务" min-width="180" />
          <el-table-column prop="status" label="结果" width="110" />
          <el-table-column prop="successCount" label="成功" width="80" />
          <el-table-column prop="failureCount" label="错误" width="80" />
          <el-table-column prop="attemptCount" label="尝试" width="80" />
          <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
          <el-table-column prop="startedAt" label="开始时间" width="180" />
          <AdaptiveActionColumn label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRun(row)">详情/对账</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="失败与死信" name="deadLetters">
        <el-table :data="deadLetters" v-loading="loading" border>
          <el-table-column prop="itemKey" label="项目" min-width="170" />
          <el-table-column prop="errorMessage" label="最后错误" min-width="280" show-overflow-tooltip />
          <el-table-column prop="attempts" label="尝试次数" width="100" />
          <el-table-column prop="replayCount" label="重放次数" width="100" />
          <el-table-column prop="status" label="状态" width="100" />
          <AdaptiveActionColumn label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="row.status !== 'PENDING'" @click="replay(row)">人工重放</el-button>
            </template>
          </AdaptiveActionColumn>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dryRunVisible" title="增量同步预检" width="760px">
      <el-descriptions v-if="dryRunResult" :column="3" border>
        <el-descriptions-item label="状态">{{ dryRunResult.status }}</el-descriptions-item>
        <el-descriptions-item label="当前游标">{{ dryRunResult.cursorBefore || '起点' }}</el-descriptions-item>
        <el-descriptions-item label="下一游标">{{ dryRunResult.nextCursor || '无' }}</el-descriptions-item>
        <el-descriptions-item label="拉取">{{ dryRunResult.fetchedCount }}</el-descriptions-item>
        <el-descriptions-item label="可映射">{{ dryRunResult.mappedCount }}</el-descriptions-item>
        <el-descriptions-item label="缺失必填">{{ dryRunResult.requiredFailures?.length || 0 }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="dryRunResult?.diff || []" border class="detail-table">
        <el-table-column prop="sourceKey" label="来源键" width="180" />
        <el-table-column prop="operation" label="动作" width="100" />
        <el-table-column label="映射结果" min-width="320">
          <template #default="{ row }"><code>{{ JSON.stringify(row.fields) }}</code></template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="runVisible" title="运行详情与对账" width="820px">
      <el-alert v-if="selectedRun?.errorMessage" :title="selectedRun.errorMessage" type="error" :closable="false" />
      <h4>条目错误</h4>
      <el-table :data="runErrors" border>
        <el-table-column prop="itemKey" label="项目" min-width="170" />
        <el-table-column prop="errorMessage" label="错误" min-width="300" />
        <el-table-column prop="status" label="状态" width="120" />
      </el-table>
      <h4>对账记录</h4>
      <el-table :data="reconciliations" border>
        <el-table-column prop="objectType" label="对象" width="120" />
        <el-table-column prop="sourceCount" label="来源" width="90" />
        <el-table-column prop="acceptedCount" label="接收" width="90" />
        <el-table-column prop="rejectedCount" label="拒绝" width="90" />
        <el-table-column prop="missingCount" label="缺失" width="90" />
        <el-table-column prop="status" label="状态" width="120" />
      </el-table>
      <el-form :inline="true" class="reconcile-form">
        <el-form-item label="来源"><el-input-number v-model="reconcileForm.sourceCount" :min="0" /></el-form-item>
        <el-form-item label="接收"><el-input-number v-model="reconcileForm.acceptedCount" :min="0" /></el-form-item>
        <el-form-item label="拒绝"><el-input-number v-model="reconcileForm.rejectedCount" :min="0" /></el-form-item>
        <el-form-item label="缺失"><el-input-number v-model="reconcileForm.missingCount" :min="0" /></el-form-item>
        <el-form-item><el-button type="primary" @click="submitReconciliation">保存对账</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  dryRunIntegrationSyncJob,
  listIntegrationConnectors,
  listIntegrationDeadLetters,
  listIntegrationReconciliations,
  listIntegrationRunErrors,
  listIntegrationRuns,
  listIntegrationSyncJobs,
  reconcileIntegrationRun,
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
const dryRunVisible = ref(false)
const dryRunResult = ref(null)
const runVisible = ref(false)
const selectedRun = ref(null)
const runErrors = ref([])
const reconciliations = ref([])
const reconcileForm = reactive({ sourceCount: 0, acceptedCount: 0, rejectedCount: 0, missingCount: 0 })

const rows = response => response?.data?.content || response?.data || []

const loadAll = async () => {
  loading.value = true
  try {
    const [connectorResponse, jobResponse, runResponse, deadLetterResponse] = await Promise.all([
      listIntegrationConnectors({ page: 0, size: 50 }),
      listIntegrationSyncJobs({ page: 0, size: 50 }),
      listIntegrationRuns({ page: 0, size: 100 }),
      listIntegrationDeadLetters({ page: 0, size: 50 }),
    ])
    connectors.value = rows(connectorResponse)
    jobs.value = rows(jobResponse)
    runs.value = rows(runResponse).sort((a, b) => String(b.startedAt).localeCompare(String(a.startedAt)))
    deadLetters.value = rows(deadLetterResponse)
  } finally {
    loading.value = false
  }
}

const testConnector = async row => {
  try {
    await testIntegrationConnector(row.id)
    ElMessage.success('连接测试通过')
    await loadAll()
  } catch (error) {
    if (error?.response?.status === 409 || error?.response?.status === 501) blocked.value = true
  }
}

const dryRun = async row => {
  const response = await dryRunIntegrationSyncJob(row.id)
  dryRunResult.value = response.data
  dryRunVisible.value = true
}

const triggerJob = async row => {
  const response = await triggerIntegrationSyncJob(row.id)
  ElMessage[response.data?.status === 'SUCCEEDED' ? 'success' : 'warning'](
    response.data?.status === 'SUCCEEDED' ? '同步任务运行成功' : `同步任务失败：${response.data?.errorMessage || '请查看死信'}`,
  )
  await loadAll()
}

const openRun = async row => {
  selectedRun.value = row
  const [errorResponse, reconciliationResponse] = await Promise.all([
    listIntegrationRunErrors(row.id),
    listIntegrationReconciliations(row.id),
  ])
  runErrors.value = rows(errorResponse)
  reconciliations.value = rows(reconciliationResponse)
  reconcileForm.sourceCount = row.successCount + row.failureCount
  reconcileForm.acceptedCount = row.successCount
  reconcileForm.rejectedCount = row.failureCount
  reconcileForm.missingCount = 0
  runVisible.value = true
}

const submitReconciliation = async () => {
  await reconcileIntegrationRun(selectedRun.value.id, {
    objectType: jobs.value.find(item => item.id === selectedRun.value.jobId)?.objectType || 'DEFAULT',
    ...reconcileForm,
  })
  ElMessage.success('对账记录已保存')
  await openRun(selectedRun.value)
}

const replay = async row => {
  await replayIntegrationDeadLetter(row.id)
  ElMessage.success('死信重放成功')
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
.detail-table, .reconcile-form { margin-top: 16px; }
.detail-table code { white-space: normal; overflow-wrap: anywhere; }
h4 { margin: 18px 0 10px; }
</style>
