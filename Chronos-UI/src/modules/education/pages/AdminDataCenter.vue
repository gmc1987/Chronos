<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createEducationDataSnapshot,
  downloadEducationDataReport,
  getEducationDataDashboard,
  getEducationDataEventReconciliation,
  listEducationDataEventFailures,
  listEducationDataReports,
  listEducationQualityIssues,
  requestEducationDataReport,
  scanEducationQualityIssues,
  transitionEducationQualityIssue,
  retryEducationDataEvent,
  retryEducationDataReport,
} from '../../../api/admin'
import { todayLocalDate } from '../../../utils/localDate'

const date = ref(todayLocalDate())
const active = ref('academic')
const rows = ref([])
const reports = ref([])
const issues = ref([])
const reconciliation = ref([])
const eventFailures = ref([])
const loading = ref(false)
const load = async () => {
  loading.value = true
  try { const response = await getEducationDataDashboard(active.value, date.value); rows.value = response?.data || [] }
  finally { loading.value = false }
}
const snapshot = async () => { await createEducationDataSnapshot(date.value); ElMessage.success('日报快照已幂等生成'); await load() }
const report = async () => {
  await requestEducationDataReport({ reportType: active.value, requestedDate: date.value })
  ElMessage.success('报告任务已提交')
  await loadOperational()
  window.setTimeout(loadOperational, 1200)
}
const loadOperational = async () => {
  const [reportResponse, issueResponse, reconciliationResponse, failureResponse] = await Promise.all([
    listEducationDataReports(),
    listEducationQualityIssues(),
    getEducationDataEventReconciliation(),
    listEducationDataEventFailures(),
  ])
  reports.value = reportResponse?.data || []
  issues.value = issueResponse?.data || []
  reconciliation.value = reconciliationResponse?.data || []
  eventFailures.value = failureResponse?.data || []
}
const retry = async (row) => { await retryEducationDataReport(row.id); ElMessage.success('已重新提交'); await loadOperational() }
const download = async (row) => {
  const blob = await downloadEducationDataReport(row.id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `数据中心-${row.reportType}-${row.requestedDate}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}
const retryEvent = async (row) => {
  await retryEducationDataEvent(row.channel, row.id)
  ElMessage.success('事件已重新入队')
  await loadOperational()
}
const scanQuality = async () => {
  const response = await scanEducationQualityIssues(date.value)
  const count = response?.data?.length || 0
  ElMessage.success(count ? `发现 ${count} 个数据质量问题` : '质量扫描完成，未发现新问题')
  await loadOperational()
}
const resolveIssue = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入处理说明', '解决数据质量问题', {
    confirmButtonText: '确认解决',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '处理说明不能为空',
  })
  await transitionEducationQualityIssue(row.id, 'RESOLVED', value)
  ElMessage.success('问题已标记为已解决')
  await loadOperational()
}
const closeIssue = async (row) => {
  await transitionEducationQualityIssue(row.id, 'CLOSED', row.resolution || '已核验关闭')
  ElMessage.success('问题已关闭')
  await loadOperational()
}
onMounted(load)
onMounted(loadOperational)
</script>
<template>
  <section class="page">
    <header><div><h1>教育数据中心</h1><p>仪表板读取已持久化的日报快照，避免跨表实时聚合。</p></div>
      <div><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" @change="load" /><el-button @click="snapshot">生成快照</el-button><el-button @click="scanQuality">扫描质量</el-button><el-button type="primary" @click="report">导出报告</el-button></div>
    </header>
    <el-tabs v-model="active" @tab-change="load"><el-tab-pane label="学业概览" name="academic" /><el-tab-pane label="排课资源" name="scheduling" /><el-tab-pane label="考试" name="exams" /></el-tabs>
    <el-table v-loading="loading" :data="rows" border><el-table-column prop="metricCode" label="指标" /><el-table-column prop="metricValue" label="值" /><el-table-column prop="snapshotDate" label="日期" /></el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无快照，请先生成日报" />
    <div class="ops">
      <h2>事件同步与对账</h2>
      <el-table :data="reconciliation" border>
        <el-table-column prop="eventType" label="事件类型" min-width="210" />
        <el-table-column prop="sourceCount" label="源事件" width="90" />
        <el-table-column prop="projectedCount" label="已投影" width="90" />
        <el-table-column prop="pendingCount" label="待处理" width="90" />
        <el-table-column prop="deadCount" label="死信" width="80" />
        <el-table-column label="结果" width="100"><template #default="{ row }"><el-tag :type="row.consistent ? 'success' : 'danger'">{{ row.consistent ? '一致' : '不一致' }}</el-tag></template></el-table-column>
      </el-table>
      <h2>事件失败与重放</h2>
      <el-table :data="eventFailures" border>
        <el-table-column prop="eventType" label="事件类型" min-width="210" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="attempts" label="次数" width="70" />
        <el-table-column prop="lastError" label="失败原因" min-width="240" show-overflow-tooltip />
        <AdaptiveActionColumn label="操作"><template #default="{ row }"><el-button v-if="row.status === 'DEAD'" link type="primary" @click="retryEvent(row)">重试</el-button></template></AdaptiveActionColumn>
      </el-table>
      <el-empty v-if="!eventFailures.length" description="当前没有事件失败记录" />
      <h2>报表任务</h2><el-table :data="reports" border><el-table-column prop="reportType" label="类型" /><el-table-column prop="status" label="状态" /><el-table-column prop="progress" label="进度" /><el-table-column prop="errorMessage" label="错误" /><AdaptiveActionColumn label="操作"><template #default="{ row }"><el-button v-if="row.status === 'COMPLETED'" link type="primary" @click="download(row)">下载</el-button><el-button v-if="row.status === 'FAILED'" link type="primary" @click="retry(row)">重试</el-button></template></AdaptiveActionColumn></el-table>
    <h2>数据质量</h2><el-table :data="issues" border><el-table-column prop="title" label="问题" /><el-table-column prop="severity" label="级别" /><el-table-column prop="status" label="状态" /><el-table-column prop="resolution" label="处理说明" min-width="180" /><AdaptiveActionColumn label="操作"><template #default="{ row }"><el-button v-if="row.status === 'OPEN' || row.status === 'ASSIGNED' || row.status === 'IN_PROGRESS'" link type="primary" @click="resolveIssue(row)">解决</el-button><el-button v-if="row.status === 'RESOLVED'" link type="success" @click="closeIssue(row)">关闭</el-button></template></AdaptiveActionColumn></el-table>
    </div>
  </section>
</template>
<style scoped>.page{padding:24px}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}h1{margin:0 0 6px}p{color:#84909a;margin:0}.el-button{margin-left:8px}.ops{margin-top:28px}h2{margin:24px 0 10px}</style>
