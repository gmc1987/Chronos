<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { issueSupervisionProof, portalSupervisionIssues, portalSupervisionTeachingTasks, rectifySupervisionIssue } from '../../../api/portal'

const tasks = ref([])
const loading = ref(false)
const error = ref('')
const issued = ref(null)
const issues = ref([])
const rectificationDialog = ref(false)
const rectification = ref({ issueId: '', content: '' })
const unwrap = response => response?.data?.content || response?.data || []

async function load() {
  loading.value = true
  error.value = ''
  tasks.value = []
  issued.value = null
  try {
    const [taskResult, issueResult] = await Promise.all([
      portalSupervisionTeachingTasks(), portalSupervisionIssues()
    ])
    tasks.value = unwrap(taskResult)
    issues.value = unwrap(issueResult)
  }
  catch (cause) { error.value = cause?.message || '授课督导任务加载失败' }
  finally { loading.value = false }
}
async function issue(row) {
  issued.value = null
  try {
    const response = await issueSupervisionProof(row.id)
    issued.value = { ...response.data, taskId: row.id }
    ElMessage.success('现场凭证已签发，五分钟内有效')
  } catch (cause) { ElMessage.error(cause?.message || '现场凭证签发失败') }
}
function openRectification(row) {
  rectification.value = { issueId: row.id, content: '' }
  rectificationDialog.value = true
}
async function saveRectification() {
  if (!rectification.value.content.trim()) {
    ElMessage.warning('请填写整改说明')
    return
  }
  try {
    await rectifySupervisionIssue(rectification.value.issueId, {
      content: rectification.value.content.trim()
    })
    rectificationDialog.value = false
    ElMessage.success('整改已提交，等待异人复查')
    await load()
  } catch (cause) { ElMessage.error(cause?.message || '提交整改失败') }
}
onMounted(load)
</script>
<template>
  <section class="page">
    <header><div><h1>授课督导签到凭证</h1><p>只有该课授课教师可以为已接受的督导任务签发短时凭证。</p></div><el-button @click="load">刷新</el-button></header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error" />
    <el-table v-loading="loading" :data="tasks" border>
      <el-table-column prop="scheduleEntryId" label="课表项" min-width="260" />
      <el-table-column prop="status" label="状态" width="150" />
      <AdaptiveActionColumn label="操作" width="140"><template #default="{ row }"><el-button v-if="row.status === 'ACCEPTED'" link type="primary" @click="issue(row)">签发现场凭证</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-empty v-if="!loading && !error && !tasks.length" description="暂无待督导课程" />
    <h2>我的整改问题</h2>
    <el-table :data="issues" border>
      <el-table-column prop="title" label="问题" min-width="220" />
      <el-table-column prop="severity" label="严重程度" width="120" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="dueAt" label="截止时间" width="180" />
      <AdaptiveActionColumn label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === 'OPEN' || row.status === 'RECTIFYING'" link type="primary" @click="openRectification(row)">提交整改</el-button></template></AdaptiveActionColumn>
    </el-table>
    <el-card v-if="issued" class="proof-card">
      <template #header>任务 {{ issued.taskId }} 的现场凭证</template>
      <p>请仅在课堂现场交给对应督导员，五分钟后失效。</p>
      <el-input :model-value="issued.providerId" readonly><template #prepend>凭证服务</template></el-input>
      <el-input :model-value="issued.signedProof" readonly type="textarea" :rows="4" class="proof-token" />
    </el-card>
    <el-dialog v-model="rectificationDialog" title="提交整改" width="540px">
      <el-input v-model="rectification.content" type="textarea" :rows="5" maxlength="10000" placeholder="填写已完成的整改措施和结果" />
      <template #footer><el-button @click="rectificationDialog = false">取消</el-button><el-button type="primary" @click="saveRectification">提交整改</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h1 { margin: 0 0 6px; }
p { color: #84909a; }
h2 { margin: 24px 0 12px; font-size: 18px; }
.error, .proof-card { margin-top: 16px; }
.proof-token { margin-top: 12px; }
</style>
