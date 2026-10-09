<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  archiveOfficialDocument,
  attachOfficialDocumentFile,
  createOfficialDocument,
  downloadCollaborationFile,
  fetchIssuedOfficialDocuments,
  fetchMyOfficialDocuments,
  submitOfficialDocument,
  uploadCollaborationFile,
} from '../../../api/portal'

const myDocuments = ref([])
const issuedDocuments = ref([])
const activeTab = ref('mine')
const loading = ref(false)
const dialogVisible = ref(false)
const selectedFile = ref(null)
const form = reactive({
  direction: 'OUTGOING',
  title: '',
  documentNumber: '',
  urgency: 'NORMAL',
  summary: '',
})
const unwrap = response => response?.data?.content || response?.data || []
const statusLabel = status => ({
  DRAFT: '草稿',
  IN_REVIEW: '审批中',
  ISSUED: '已发布',
  RECEIVED: '已收文',
  REJECTED: '已退回',
  ARCHIVED: '已归档',
})[status] || status

async function load() {
  loading.value = true
  try {
    const [mine, issued] = await Promise.all([
      fetchMyOfficialDocuments(),
      fetchIssuedOfficialDocuments(),
    ])
    myDocuments.value = unwrap(mine)
    issuedDocuments.value = unwrap(issued)
  } catch (error) {
    ElMessage.error(error?.message || '公文数据加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    direction: 'OUTGOING',
    title: '',
    documentNumber: '',
    urgency: 'NORMAL',
    summary: '',
  })
  selectedFile.value = null
  dialogVisible.value = true
}

function chooseFile(file) {
  selectedFile.value = file
  return false
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入公文标题')
    return
  }
  try {
    const created = await createOfficialDocument(form)
    const document = created?.data
    if (selectedFile.value && document?.id) {
      const uploaded = await uploadCollaborationFile('OFFICIAL_DOCUMENT', document.id, selectedFile.value)
      if (!uploaded?.data?.id) throw new Error('文件服务未返回有效文件标识')
      await attachOfficialDocumentFile(document.id, uploaded.data.id)
    }
    dialogVisible.value = false
    ElMessage.success(form.direction === 'OUTGOING' ? '发文草稿已保存' : '收文已登记')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '公文保存失败')
  }
}

async function submit(row) {
  try {
    await submitOfficialDocument(row.id)
    ElMessage.success('公文已送审')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '送审失败')
  }
}

async function archive(row) {
  try {
    await archiveOfficialDocument(row.id)
    ElMessage.success('公文已归档')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '归档失败')
  }
}

async function download(row) {
  if (!row.primaryFileId) return
  try {
    const blob = await downloadCollaborationFile(row.primaryFileId)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (error) {
    ElMessage.error(error?.message || '文件下载失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="documents-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>公文中心</h1>
        <p>公文台账负责拟稿、收文、签发和归档；审批任务统一进入流程中心。</p>
      </div>
      <el-button type="primary" @click="openCreate">登记公文</el-button>
    </header>
    <el-alert title="正文与附件统一存放在文件服务中，公文台账只保存安全文件引用，不重复存储文件。" type="info" :closable="false" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的公文" name="mine">
        <el-empty v-if="!myDocuments.length" description="暂无公文" />
        <el-table v-else :data="myDocuments" stripe>
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.direction === 'OUTGOING' ? '发文' : '收文' }}</template></el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="documentNumber" label="文号" width="160" />
          <el-table-column prop="urgency" label="紧急程度" width="110" />
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button v-if="row.primaryFileId" link @click="download(row)">查看正文</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="submit(row)">送审</el-button>
              <el-button v-if="['ISSUED', 'RECEIVED'].includes(row.status)" link @click="archive(row)">归档</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="已发布公文" name="issued">
        <el-table :data="issuedDocuments" stripe>
          <el-table-column prop="title" label="标题" min-width="240" />
          <el-table-column prop="documentNumber" label="文号" width="180" />
          <el-table-column prop="issuedAt" label="发布时间" width="180" />
          <el-table-column label="正文" width="100"><template #default="{ row }"><el-button v-if="row.primaryFileId" link @click="download(row)">查看</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" title="登记公文" width="640px">
      <el-form label-width="90px">
        <el-form-item label="公文类型" required><el-radio-group v-model="form.direction"><el-radio-button value="OUTGOING">发文</el-radio-button><el-radio-button value="INCOMING">收文</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="文号"><el-input v-model="form.documentNumber" /></el-form-item>
        <el-form-item label="紧急程度"><el-select v-model="form.urgency"><el-option label="普通" value="NORMAL" /><el-option label="加急" value="URGENT" /><el-option label="特急" value="VERY_URGENT" /></el-select></el-form-item>
        <el-form-item label="内容摘要"><el-input v-model="form.summary" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="正文文件">
          <el-upload :auto-upload="false" :limit="1" :on-change="file => chooseFile(file.raw)" :on-remove="() => selectedFile = null">
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.documents-page{padding:24px}.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px}.page-head h1{margin:0 0 8px}.page-head p{margin:0;color:#667085}.el-alert{margin-bottom:18px}
</style>
