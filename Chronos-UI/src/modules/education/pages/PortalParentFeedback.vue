<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalFamilyChildren, portalParentFeedback, submitParentFeedback } from '../../../api/portal'

const children = ref([])
const records = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = ref({ studentId: '', subject: '', content: '' })
const unwrap = response => response?.data?.content || response?.data || []

async function load() {
  loading.value = true
  try {
    const [childResponse, feedbackResponse] = await Promise.all([portalFamilyChildren(), portalParentFeedback()])
    children.value = unwrap(childResponse)
    records.value = unwrap(feedbackResponse)
  } finally {
    loading.value = false
  }
}
function openForm() {
  form.value = { studentId: children.value[0]?.id || '', subject: '', content: '' }
  dialog.value = true
}
async function submit() {
  await submitParentFeedback(form.value)
  dialog.value = false
  ElMessage.success('反馈已提交')
  await load()
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>家长反馈</h1><p>向学校反馈孩子在校学习和生活中的问题或建议。</p></div><el-button type="primary" @click="openForm">提交反馈</el-button></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="studentName" label="学生" width="130" />
      <el-table-column prop="subject" label="主题" min-width="180" />
      <el-table-column prop="content" label="反馈内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="status" label="处理状态" width="110" />
      <el-table-column prop="repliedAt" label="回复时间" width="170" />
      <el-table-column prop="reply" label="学校回复" min-width="240" show-overflow-tooltip />
    </el-table>
    <el-empty v-if="!loading && !records.length" description="暂无反馈记录" />
    <el-dialog v-model="dialog" title="提交家长反馈" width="560px">
      <el-form label-width="80px">
        <el-form-item label="学生"><el-select v-model="form.studentId" placeholder="选择学生"><el-option v-for="child in children" :key="child.id" :label="child.studentName" :value="child.id" /></el-select></el-form-item>
        <el-form-item label="主题"><el-input v-model="form.subject" maxlength="100" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :disabled="!form.studentId || !form.content" @click="submit">提交</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
h1 { margin: 0 0 8px; } p { margin: 0; color: #84909a; }
</style>
