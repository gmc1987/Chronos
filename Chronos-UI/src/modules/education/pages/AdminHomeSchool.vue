<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createHomeSchoolNotice, homeSchoolNoticeReceipts, listAdministrativeClasses,
  listHomeSchoolNotices, publishHomeSchoolNotice,
} from '../../../api/admin'

const notices = ref([])
const classes = ref([])
const receipts = ref([])
const selectedNotice = ref(null)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const form = ref({ classId: '', title: '', content: '', receiptRequired: true, expireAt: null })
const unwrap = response => response?.data?.content || response?.data || []

async function load() {
  loading.value = true
  try {
    const [noticeResponse, classResponse] = await Promise.all([
      listHomeSchoolNotices(), listAdministrativeClasses(),
    ])
    notices.value = unwrap(noticeResponse)
    classes.value = unwrap(classResponse)
  } finally { loading.value = false }
}
function openNotice() {
  form.value = { classId: '', title: '', content: '', receiptRequired: true, expireAt: null }
  dialog.value = true
}
async function save() {
  if (!form.value.classId || !form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('请选择班级并填写标题、内容')
    return
  }
  saving.value = true
  try {
    await createHomeSchoolNotice(form.value)
    dialog.value = false
    ElMessage.success('通知草稿已保存')
    await load()
  } finally { saving.value = false }
}
async function publish(row) {
  await ElMessageBox.confirm(`发布“${row.title}”并生成家长回执？`, '发布确认')
  await publishHomeSchoolNotice(row.id)
  ElMessage.success('通知已发布')
  await load()
}
async function showReceipts(row) {
  selectedNotice.value = row
  receipts.value = unwrap(await homeSchoolNoticeReceipts(row.id))
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header><div><h2>学校通知</h2><p>创建班级通知，发布后查看家长送达和回执。</p></div><el-button v-permission="'education:home-school:notice:create'" type="primary" @click="openNotice">新建通知</el-button></header>
    <el-table v-loading="loading" :data="notices" border>
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="className" label="班级" min-width="150" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="publishAt" label="发布时间" width="180" />
      <el-table-column label="需回执" width="100"><template #default="{ row }">{{ row.receiptRequired ? '是' : '否' }}</template></el-table-column>
      <AdaptiveActionColumn label="操作" width="180"><template #default="{ row }">
        <el-button v-if="row.status === 'DRAFT'" v-permission="'education:home-school:notice:update'" link type="primary" @click="publish(row)">发布</el-button>
        <el-button v-if="row.status === 'PUBLISHED'" link @click="showReceipts(row)">查看回执</el-button>
      </template></AdaptiveActionColumn>
    </el-table>
    <el-card v-if="selectedNotice" class="receipts"><template #header>{{ selectedNotice.title }} · 回执</template>
      <el-table :data="receipts" border><el-table-column prop="parentName" label="家长" /><el-table-column prop="studentName" label="学生" /><el-table-column prop="deliveryStatus" label="送达" /><el-table-column prop="receiptStatus" label="回执" /><el-table-column prop="receiptAt" label="回执时间" /></el-table>
    </el-card>
    <el-dialog v-model="dialog" title="新建学校通知" width="620px">
      <el-form label-width="100px">
        <el-form-item label="班级"><el-select v-model="form.classId" filterable placeholder="选择行政班" style="width: 100%"><el-option v-for="item in classes" :key="item.id" :label="`${item.className} (${item.classCode})`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="需回执"><el-switch v-model="form.receiptRequired" /></el-form-item>
        <el-form-item label="过期时间"><el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; } header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; } h2 { margin: 0 0 6px; } p { margin: 0; color: #84909a; } .receipts { margin-top: 18px; }
</style>
