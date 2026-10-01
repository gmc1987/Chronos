<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createHomeSchoolBinding,
  createHomeSchoolNotice,
  homeSchoolNoticeReceipts,
  invalidateHomeSchoolBinding,
  listHomeSchoolBindings,
  listHomeSchoolNotices,
  publishHomeSchoolNotice,
} from '../../../api/admin'

const tab = ref('bindings')
const bindings = ref([])
const notices = ref([])
const receipts = ref([])
const bindingDialog = ref(false)
const noticeDialog = ref(false)
const binding = ref({ status: 'ACTIVE' })
const notice = ref({ receiptRequired: true })
const selectedNotice = ref(null)
const loading = ref(false)
const unwrap = response => response?.data?.content || response?.data || []

async function load() {
  loading.value = true
  try {
    bindings.value = unwrap(await listHomeSchoolBindings())
    notices.value = unwrap(await listHomeSchoolNotices())
  } finally {
    loading.value = false
  }
}
function openBinding(row) {
  binding.value = row ? { ...row } : { status: 'ACTIVE' }
  bindingDialog.value = true
}
function openNotice() {
  notice.value = { receiptRequired: true }
  noticeDialog.value = true
}
async function saveBinding() {
  await createHomeSchoolBinding(binding.value)
  bindingDialog.value = false
  ElMessage.success('家长账号绑定已保存')
  await load()
}
async function saveNotice() {
  await createHomeSchoolNotice(notice.value)
  noticeDialog.value = false
  ElMessage.success('通知草稿已保存')
  await load()
}
async function invalidate(row) {
  await invalidateHomeSchoolBinding(row.id)
  ElMessage.success('绑定已失效')
  await load()
}
async function publish(row) {
  await publishHomeSchoolNotice(row.id)
  ElMessage.success('通知已发布并生成目标快照')
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
    <header>
      <div><h2>家校中心</h2><p>维护家长账号绑定、班级通知和回执状态。</p></div>
      <div class="actions">
        <el-button v-if="tab === 'bindings'" type="primary" @click="openBinding()">新增绑定</el-button>
        <el-button v-else type="primary" @click="openNotice()">新建通知</el-button>
      </div>
    </header>
    <el-tabs v-model="tab">
      <el-tab-pane label="家长账号绑定" name="bindings">
        <el-table v-loading="loading" :data="bindings" border>
          <el-table-column prop="parentName" label="家长" />
          <el-table-column prop="username" label="登录账号" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="verifiedAt" label="核验时间" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="invalidate(row)">解绑</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="班级通知" name="notices">
        <el-table v-loading="loading" :data="notices" border>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="className" label="班级" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="publishAt" label="发布时间" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="publish(row)">发布</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" link @click="showReceipts(row)">查看回执</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-card v-if="selectedNotice" class="receipts">
          <template #header>{{ selectedNotice.title }} · 回执</template>
          <el-table :data="receipts" size="small"><el-table-column prop="parentName" label="家长" /><el-table-column prop="studentName" label="学生" /><el-table-column prop="deliveryStatus" label="送达" /><el-table-column prop="receiptStatus" label="回执" /><el-table-column prop="receiptAt" label="回执时间" /></el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="bindingDialog" title="绑定家长账号" width="520px">
      <el-form label-width="100px"><el-form-item label="家长档案ID"><el-input v-model="binding.parentId" /></el-form-item><el-form-item label="登录账号"><el-input v-model="binding.username" /></el-form-item></el-form>
      <template #footer><el-button @click="bindingDialog = false">取消</el-button><el-button type="primary" @click="saveBinding">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="noticeDialog" title="新建班级通知" width="620px">
      <el-form label-width="100px"><el-form-item label="班级ID"><el-input v-model="notice.classId" /></el-form-item><el-form-item label="标题"><el-input v-model="notice.title" maxlength="200" /></el-form-item><el-form-item label="内容"><el-input v-model="notice.content" type="textarea" :rows="5" /></el-form-item><el-form-item label="需回执"><el-switch v-model="notice.receiptRequired" /></el-form-item><el-form-item label="过期时间"><el-date-picker v-model="notice.expireAt" type="datetime" /></el-form-item></el-form>
      <template #footer><el-button @click="noticeDialog = false">取消</el-button><el-button type="primary" @click="saveNotice">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0 0 6px; }
p { margin: 0; color: #84909a; }
.receipts { margin-top: 18px; }
</style>
