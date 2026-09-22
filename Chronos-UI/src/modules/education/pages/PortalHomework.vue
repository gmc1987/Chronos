<template>
  <section class="homework-page">
    <header><h1>我的作业</h1><p>查看已发布作业，保存答案草稿并提交。</p></header>
    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" />
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="title" label="作业" min-width="240" />
      <el-table-column prop="dueAt" label="截止时间" width="190" />
      <el-table-column prop="maxScore" label="总分" width="90" />
      <el-table-column label="提交状态" width="150">
        <template #default="{ row }">{{ statusLabel(row.submission?.status) }}</template>
      </el-table-column>
      <el-table-column label="成绩" width="90">
        <template #default="{ row }">{{ row.submission?.score ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="submission.teacherFeedback" label="教师评语" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="open(row)">{{ canEdit(row) ? '作答' : '查看' }}</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无已发布作业" />
    <el-dialog v-model="dialog" :title="current?.title || '作业'" width="720px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="截止时间">{{ current?.dueAt || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="作业说明">{{ current?.instructionsJson || '—' }}</el-descriptions-item>
        <el-descriptions-item label="题目/要求">{{ current?.questionSnapshotJson || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交状态">{{ statusLabel(current?.submission?.status) }}</el-descriptions-item>
        <el-descriptions-item label="成绩">{{ current?.submission?.score ?? '—' }} / {{ current?.maxScore }}</el-descriptions-item>
        <el-descriptions-item label="教师评语">{{ current?.submission?.teacherFeedback || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px" class="answer-form">
        <el-form-item label="我的答案"><el-input v-model="answerSnapshotJson" type="textarea" :rows="10" :disabled="!canEdit(current)" placeholder="请输入答案或按题目编号填写" /></el-form-item>
        <el-form-item label="附件引用"><el-input v-model="attachmentSnapshotJson" type="textarea" :rows="3" :disabled="!canEdit(current)" placeholder='已上传文件引用 JSON，例如 [{"fileId":"..."}]' /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">关闭</el-button><template v-if="canEdit(current)"><el-button @click="saveDraft">保存草稿</el-button><el-button type="primary" @click="submit">提交作业</el-button></template></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { homeworkPage, myHomeworkSubmission, saveHomeworkSubmission, submitHomework } from '../api/teachingCenter'

const rows = ref([])
const current = ref(null)
const answerSnapshotJson = ref('')
const loading = ref(false)
const loadError = ref('')
const dialog = ref(false)
const submissionId = ref('')
const attachmentSnapshotJson = ref('[]')
const unwrap = response => response?.data?.content || response?.data || []
const statusLabels = { NOT_STARTED: '未开始', DRAFT: '草稿', SUBMITTED: '待批改', GRADED: '已评分', RETURNED_FOR_REVISION: '退回重做' }
const statusLabel = status => statusLabels[status] || '未提交'
const canEdit = row => row && (!row.submission || ['DRAFT', 'RETURNED_FOR_REVISION'].includes(row.submission.status))

const load = async () => {
  loading.value = true
  try {
    const assignments = unwrap(await homeworkPage({ page: 0, size: 100 }))
    rows.value = await Promise.all(assignments.map(async assignment => ({
      ...assignment,
      submission: (await myHomeworkSubmission(assignment.id))?.data || null
    })))
  }
  catch (error) { loadError.value = error.message || '作业加载失败' }
  finally { loading.value = false }
}
const open = row => {
  current.value = row
  answerSnapshotJson.value = row.submission?.answerSnapshotJson || ''
  attachmentSnapshotJson.value = row.submission?.attachmentSnapshotJson || '[]'
  submissionId.value = row.submission?.id || ''
  dialog.value = true
}
const saveDraft = async () => {
  try {
    const response = await saveHomeworkSubmission(current.value.id, {
      answerSnapshotJson: answerSnapshotJson.value || '{}',
      attachmentSnapshotJson: attachmentSnapshotJson.value || '[]',
    })
    submissionId.value = response?.data?.id || submissionId.value
    current.value.submission = response?.data || current.value.submission
    ElMessage.success('答案草稿已保存')
  } catch (error) { ElMessage.error(error.message || '保存失败') }
}
const submit = async () => {
  await ElMessageBox.confirm('提交后将进入教师批改，确认提交？', '确认提交')
  try {
    if (!submissionId.value) await saveDraft()
    if (!submissionId.value) return
    await submitHomework(submissionId.value)
    dialog.value = false
    ElMessage.success('作业已提交')
    await load()
  } catch (error) { ElMessage.error(error.message || '提交失败') }
}
onMounted(load)
</script>

<style scoped>.homework-page{padding:24px}.homework-page header{margin-bottom:18px}.homework-page h1{margin:0 0 6px}.homework-page p{margin:0;color:#667085}.el-alert{margin-bottom:16px}.answer-form{margin-top:20px}</style>
