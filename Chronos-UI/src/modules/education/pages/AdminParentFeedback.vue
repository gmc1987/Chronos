<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listParentFeedback, replyParentFeedback } from '../../../api/admin'

const records = ref([])
const loading = ref(false)
const replyDialog = ref(false)
const selected = ref(null)
const reply = ref('')
const unwrap = response => response?.data?.content || response?.data || []
async function load() {
  loading.value = true
  try { records.value = unwrap(await listParentFeedback()) } finally { loading.value = false }
}
function openReply(row) { selected.value = row; reply.value = row.reply || ''; replyDialog.value = true }
async function saveReply() {
  await replyParentFeedback(selected.value.id, { reply: reply.value })
  replyDialog.value = false
  ElMessage.success('回复已保存')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>家长反馈</h2><p>集中查看家长反馈并记录学校处理结果。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="parentName" label="家长" width="130" />
      <el-table-column prop="studentName" label="学生" width="130" />
      <el-table-column prop="subject" label="主题" min-width="170" />
      <el-table-column prop="content" label="反馈内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="createdAt" label="提交时间" width="170" />
      <AdaptiveActionColumn label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openReply(row)">回复</el-button></template></AdaptiveActionColumn>
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
