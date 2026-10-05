<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listEducationParents, listEducationStudents, listParentFeedback, replyParentFeedback, transitionParentFeedback } from '../../../api/admin'

const records = ref([])
const loading = ref(false)
const replyDialog = ref(false)
const selected = ref(null)
const reply = ref('')
const parentNames = ref({})
const studentNames = ref({})
const nameOf = (mapping, id) => mapping[id] || id || '-'
const unwrap = response => response?.data?.content || response?.data || []
async function load() {
  loading.value = true
  try {
    const [feedbackResponse, parentResponse, studentResponse] = await Promise.all([
      listParentFeedback(), listEducationParents({ page: 0, size: 200 }), listEducationStudents({ page: 0, size: 200 }),
    ])
    records.value = unwrap(feedbackResponse)
    parentNames.value = Object.fromEntries(unwrap(parentResponse).map(item => [item.id, item.parentName]))
    studentNames.value = Object.fromEntries(unwrap(studentResponse).map(item => [item.id, item.studentName]))
  } finally { loading.value = false }
}
function openReply(row) { selected.value = row; reply.value = row.staffReply || ''; replyDialog.value = true }
async function saveReply() {
  if (!reply.value.trim()) return ElMessage.warning('请输入回复内容')
  await replyParentFeedback(selected.value.id, { reply: reply.value })
  replyDialog.value = false
  ElMessage.success('回复已保存')
  await load()
}
async function changeStatus(row, status) {
  await transitionParentFeedback(row.id, { status })
  ElMessage.success('反馈状态已更新')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>家长反馈</h2><p>集中查看家长反馈并记录学校处理结果。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="家长" width="130"><template #default="{ row }">{{ nameOf(parentNames, row.parentId) }}</template></el-table-column>
      <el-table-column label="学生" width="130"><template #default="{ row }">{{ nameOf(studentNames, row.studentId) }}</template></el-table-column>
      <el-table-column prop="title" label="主题" min-width="170" />
      <el-table-column prop="content" label="反馈内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="staffReply" label="学校回复" min-width="180" show-overflow-tooltip />
      <el-table-column prop="repliedAt" label="回复时间" width="170" />
      <AdaptiveActionColumn label="操作" width="200"><template #default="{ row }">
        <el-button v-if="['SUBMITTED','ACCEPTED','ASSIGNED','WAITING_SUPPLEMENT','IN_PROGRESS'].includes(row.status)" v-permission="'education:home-school:feedback:update'" link type="primary" @click="openReply(row)">回复</el-button>
        <el-button v-if="row.status === 'SUBMITTED'" v-permission="'education:home-school:feedback:update'" link @click="changeStatus(row, 'ACCEPTED')">受理</el-button>
        <el-button v-if="row.status === 'IN_PROGRESS'" v-permission="'education:home-school:feedback:update'" link @click="changeStatus(row, 'RESOLVED')">解决</el-button>
        <el-button v-if="row.status === 'RESOLVED'" v-permission="'education:home-school:feedback:update'" link @click="changeStatus(row, 'CLOSED')">关闭</el-button>
      </template></AdaptiveActionColumn>
    </el-table>
    <el-empty v-if="!loading && !records.length" description="暂无家长反馈" />
    <el-dialog v-model="replyDialog" title="回复家长反馈" width="560px">
      <el-input v-model="reply" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="填写处理意见或回复" />
      <template #footer><el-button @click="replyDialog = false">取消</el-button><el-button type="primary" :disabled="!reply.trim()" @click="saveReply">保存回复</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }
</style>
