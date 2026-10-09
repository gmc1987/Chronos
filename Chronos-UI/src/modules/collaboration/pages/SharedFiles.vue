<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  attachSharedFile,
  createFileShare,
  downloadCollaborationFile,
  fetchCollaborationDepartments,
  fetchSharedFiles,
  uploadCollaborationFile,
} from '../../../api/portal'

const files = ref([])
const departments = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const selectedFile = ref(null)
const form = reactive({
  title: '',
  description: '',
  audienceType: 'PRIVATE',
  audienceValue: '',
})
const unwrap = response => response?.data?.content || response?.data || []
const audienceLabel = row => ({
  PRIVATE: '仅自己',
  USER: `指定用户：${row.audienceValue || '-'}`,
  DEPARTMENT: `指定部门：${departments.value.find(item => item.id === row.audienceValue)?.name || row.audienceValue || '-'}`,
  ALL: '全体用户',
})[row.audienceType] || row.audienceType

async function load() {
  loading.value = true
  try {
    const [fileResponse, departmentResponse] = await Promise.all([
      fetchSharedFiles(),
      fetchCollaborationDepartments(),
    ])
    files.value = unwrap(fileResponse)
    departments.value = unwrap(departmentResponse)
  } catch (error) {
    ElMessage.error(error?.message || '共享文件加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { title: '', description: '', audienceType: 'PRIVATE', audienceValue: '' })
  selectedFile.value = null
  dialogVisible.value = true
}

async function save() {
  if (!form.title.trim() || !selectedFile.value) {
    ElMessage.warning('请填写标题并选择文件')
    return
  }
  try {
    const created = await createFileShare(form)
    const share = created?.data
    if (!share?.id) throw new Error('共享记录创建失败')
    const uploaded = await uploadCollaborationFile('COLLABORATION_SHARE', share.id, selectedFile.value)
    if (!uploaded?.data?.id) throw new Error('文件服务未返回有效文件标识')
    await attachSharedFile(share.id, uploaded.data.id)
    dialogVisible.value = false
    ElMessage.success('文件已保存并按指定范围共享')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '文件共享失败')
  }
}

async function download(row) {
  if (!row.fileId) return
  try {
    const blob = await downloadCollaborationFile(row.fileId)
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
  <div class="files-page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>文件中心 · 共享文件</h1>
        <p>基于统一文件服务管理上传、下载和共享授权，不建立第二套文件存储。</p>
      </div>
      <el-button type="primary" @click="openCreate">上传并共享</el-button>
    </header>
    <el-empty v-if="!files.length" description="暂无可访问的共享文件" />
    <el-table v-else :data="files" stripe>
      <el-table-column prop="title" label="文件标题" min-width="220" />
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="共享范围" min-width="180"><template #default="{ row }">{{ audienceLabel(row) }}</template></el-table-column>
      <el-table-column prop="ownerUsername" label="所有者" width="140" />
      <el-table-column prop="createTime" label="上传时间" width="180" />
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button :disabled="!row.fileId" link type="primary" @click="download(row)">下载</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="上传并共享文件" width="600px">
      <el-form label-width="100px">
        <el-form-item label="文件标题" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="共享范围" required>
          <el-select v-model="form.audienceType">
            <el-option label="仅自己" value="PRIVATE" />
            <el-option label="指定用户" value="USER" />
            <el-option label="指定部门" value="DEPARTMENT" />
            <el-option label="全体用户" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.audienceType === 'USER'" label="用户名" required>
          <el-input v-model="form.audienceValue" placeholder="多个用户名用英文逗号分隔" />
        </el-form-item>
        <el-form-item v-if="form.audienceType === 'DEPARTMENT'" label="部门" required>
          <el-select v-model="form.audienceValue" filterable placeholder="请选择部门">
            <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload :auto-upload="false" :limit="1" :on-change="file => selectedFile = file.raw" :on-remove="() => selectedFile = null">
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">上传并共享</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.files-page{padding:24px}.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:18px}.page-head h1{margin:0 0 8px}.page-head p{margin:0;color:#667085}
</style>
