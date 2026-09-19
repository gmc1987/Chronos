<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createEducationDataSnapshot, getEducationDataDashboard, requestEducationDataReport, listEducationDataReports, listEducationQualityIssues, retryEducationDataReport } from '../../../api/admin'

const date = ref(new Date().toISOString().slice(0, 10))
const active = ref('academic')
const rows = ref([])
const reports = ref([])
const issues = ref([])
const loading = ref(false)
const load = async () => {
  loading.value = true
  try { const response = await getEducationDataDashboard(active.value, date.value); rows.value = response?.data || [] }
  finally { loading.value = false }
}
const snapshot = async () => { await createEducationDataSnapshot(date.value); ElMessage.success('日报快照已幂等生成'); await load() }
const report = async () => { await requestEducationDataReport({ reportType: active.value, requestedDate: date.value }); ElMessage.success('报告任务已提交') }
const loadOperational = async () => {
  const [reportResponse, issueResponse] = await Promise.all([listEducationDataReports(), listEducationQualityIssues()])
  reports.value = reportResponse?.data || []; issues.value = issueResponse?.data || []
}
const retry = async (row) => { await retryEducationDataReport(row.id); ElMessage.success('已重新提交'); await loadOperational() }
onMounted(load)
onMounted(loadOperational)
</script>
<template>
  <section class="page">
    <header><div><h1>教育数据中心</h1><p>仪表板读取已持久化的日报快照，避免跨表实时聚合。</p></div>
      <div><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" @change="load" /><el-button @click="snapshot">生成快照</el-button><el-button type="primary" @click="report">导出报告</el-button></div>
    </header>
    <el-tabs v-model="active" @tab-change="load"><el-tab-pane label="学业概览" name="academic" /><el-tab-pane label="排课资源" name="scheduling" /><el-tab-pane label="考试" name="exams" /></el-tabs>
    <el-table v-loading="loading" :data="rows" border><el-table-column prop="metricCode" label="指标" /><el-table-column prop="metricValue" label="值" /><el-table-column prop="snapshotDate" label="日期" /></el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无快照，请先生成日报" />
    <div class="ops"><h2>报表任务</h2><el-table :data="reports" border><el-table-column prop="reportType" label="类型" /><el-table-column prop="status" label="状态" /><el-table-column prop="progress" label="进度" /><el-table-column prop="errorMessage" label="错误" /><el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.status === 'FAILED'" link type="primary" @click="retry(row)">重试</el-button></template></el-table-column></el-table>
    <h2>数据质量</h2><el-table :data="issues" border><el-table-column prop="title" label="问题" /><el-table-column prop="severity" label="级别" /><el-table-column prop="status" label="状态" /><el-table-column prop="dueDate" label="截止日期" /></el-table>
    </div>
  </section>
</template>
<style scoped>.page{padding:24px}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}h1{margin:0 0 6px}p{color:#84909a;margin:0}.el-button{margin-left:8px}.ops{margin-top:28px}h2{margin:24px 0 10px}</style>
