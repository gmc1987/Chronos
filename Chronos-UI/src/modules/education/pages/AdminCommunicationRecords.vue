<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCommunicationRecord, exportCommunicationRecords, listAdministrativeClasses, listCommunicationRecords, listEducationStudents } from '../../../api/admin'

const records = ref([])
const loading = ref(false)
const students = ref([])
const classes = ref([])
const selectedClassId = ref('')
const dialog = ref(false)
const saving = ref(false)
const form = ref({ studentId: '', channel: 'PHONE', subject: '', content: '', occurredAt: '' })
const unwrap = response => response?.data?.content || response?.data || []
const channelName = channel => ({ PHONE: '电话', IN_PERSON: '面谈', ONLINE: '线上', OTHER: '其他' }[channel] || channel || '-')
async function load() {
  loading.value = true
  try {
    const [recordResponse, classResponse] = await Promise.all([
      listCommunicationRecords(), listAdministrativeClasses(),
    ])
    records.value = unwrap(recordResponse)
    classes.value = unwrap(classResponse)
  } finally { loading.value = false }
}
function openCreate() {
  selectedClassId.value = ''
  students.value = []
  form.value = { studentId: '', channel: 'PHONE', subject: '', content: '', occurredAt: '' }
  dialog.value = true
}
async function selectClass(classId) {
  form.value.studentId = ''
  students.value = classId ? unwrap(await listEducationStudents({ classId })) : []
}
async function save() {
  if (!form.value.studentId || !form.value.content.trim()) return ElMessage.warning('请选择学生并填写沟通内容')
  saving.value = true
  try {
    await createCommunicationRecord({ ...form.value, occurredAt: form.value.occurredAt || null })
    dialog.value = false
    ElMessage.success('沟通记录已保存')
    await load()
  } finally { saving.value = false }
}
async function exportRecords() {
  const blob = await exportCommunicationRecords()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '家校沟通记录.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>家校沟通记录</h2><p>记录和查询授权班级内的家校沟通，便于后续跟进。</p></div><div><el-button :loading="loading" @click="load">刷新</el-button><el-button v-permission="'education:home-school:communication:export'" @click="exportRecords">导出 CSV</el-button><el-button v-permission="'education:home-school:communication:create'" type="primary" @click="openCreate">新增记录</el-button></div></header>
    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="学生" width="140"><template #default="{ row }">{{ row.studentName || row.studentId || '-' }}</template></el-table-column>
      <el-table-column prop="teacherUsername" label="记录人" width="140" />
      <el-table-column label="沟通方式" width="120"><template #default="{ row }">{{ channelName(row.channel) }}</template></el-table-column>
      <el-table-column prop="subject" label="主题" min-width="150" />
      <el-table-column prop="content" label="沟通内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="occurredAt" label="沟通时间" width="180" />
    </el-table>
    <el-empty v-if="!loading && !records.length" description="暂无沟通记录" />
    <el-dialog v-model="dialog" title="新增家校沟通记录" width="600px">
      <el-form label-width="90px">
        <el-form-item label="班级"><el-select v-model="selectedClassId" filterable placeholder="选择班级" style="width: 100%" @change="selectClass"><el-option v-for="item in classes" :key="item.id" :label="`${item.className}（${item.classCode}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="学生"><el-select v-model="form.studentId" filterable placeholder="选择学生" style="width: 100%" :disabled="!selectedClassId"><el-option v-for="item in students" :key="item.id" :label="`${item.studentName}（${item.studentNo}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="方式"><el-select v-model="form.channel"><el-option label="电话" value="PHONE" /><el-option label="面谈" value="IN_PERSON" /><el-option label="线上" value="ONLINE" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="主题"><el-input v-model="form.subject" maxlength="200" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="沟通时间"><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; }
</style>
