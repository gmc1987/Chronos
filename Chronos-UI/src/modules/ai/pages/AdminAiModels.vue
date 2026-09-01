<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">AI 模型管理</div>
        <div class="subtitle">模型基础信息与账号配置</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="模型名称" class="search-input" @keyup.enter="load" />
        <el-select v-model="provider" placeholder="提供方" @change="load" style="width: 140px">
          <el-option label="全部" value="" />
          <el-option v-for="item in providers" :key="item.dictCode" :label="item.dictName" :value="item.dictName" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增模型</el-button>
      </div>
    </div>

    <el-table :data="models" border style="width: 100%">
      <el-table-column prop="modelName" label="模型名称" />
      <el-table-column prop="version" label="版本" width="120" />
      <el-table-column prop="modelType" label="类型">
        <template #default="scope">
          {{ displayModelType(scope.row.modelType) }}
        </template>
      </el-table-column>
      <el-table-column prop="provider" label="供应商" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <span class="status-tag">{{ displayStatus(scope.row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" @click="openAccounts(scope.row)">账号配置</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增模型' : '编辑模型'" class="dark-dialog">
      <el-form label-width="100px">
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" />
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="form.modelType" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in modelTypes" :key="item.id || item.dictCode" :label="item.dictName" :value="item.dictValue ?? item.dictCode ?? item.dictName" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="form.provider" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in providers" :key="item.id || item.dictCode" :label="item.dictName" :value="item.dictValue ?? item.dictCode ?? item.dictName" />
          </el-select>
        </el-form-item>
        <el-form-item label="签名处理">
          <el-input v-model="form.signatureHandler" />
        </el-form-item>
        <el-form-item label="适配类">
          <el-input v-model="form.adapterClass" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAccounts" title="模型账号" width="720px" class="dark-dialog">
      <div class="dialog-header">
        <div class="dialog-title">{{ currentModel?.modelName }}</div>
        <el-button size="small" type="primary" @click="openAccountForm">新增账号</el-button>
      </div>
      <el-table :data="accounts" border size="small">
        <el-table-column prop="accountName" label="账号" />
        <el-table-column prop="accessKeyId" label="AccessKey" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" @click="editAccount(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeAccount(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showAccountForm" :title="accountMode === 'create' ? '新增账号' : '编辑账号'" class="dark-dialog">
      <el-form label-width="100px">
        <el-form-item label="账号名称">
          <el-input v-model="accountForm.accountName" />
        </el-form-item>
        <el-form-item label="AccessKey">
          <el-input v-model="accountForm.accessKeyId" />
        </el-form-item>
        <el-form-item label="SecretKey">
          <el-input v-model="accountForm.secretAccessKey" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="accountForm.status">
            <el-option value="1" label="启用" />
            <el-option value="0" label="禁用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAccountForm = false">取消</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  aiModels,
  aiModelDetail,
  createAiModel,
  updateAiModel,
  deleteAiModel,
  aiAccounts,
  createAiAccount,
  updateAiAccount,
  deleteAiAccount,
  dictTree,
} from '../api'

const models = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const provider = ref('')
const providers = ref([])
const modelTypes = ref([])
const modelTypeMap = ref({})
const statusMap = ref({})

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({})

const showAccounts = ref(false)
const currentModel = ref(null)
const accounts = ref([])

const showAccountForm = ref(false)
const accountMode = ref('create')
const accountForm = ref({ modelId: '', accountName: '', accessKeyId: '', secretAccessKey: '', status: '1' })

const findNodeByCode = (nodes, code) => {
  for (const n of nodes || []) {
    if (n.dictCode === code) return n
    if (n.children?.length) {
      const hit = findNodeByCode(n.children, code)
      if (hit) return hit
    }
  }
  return null
}

const buildMap = (node) => {
  const map = {}
  ;(node?.children || []).forEach((c) => {
    if (c.dictValue !== undefined && c.dictValue !== null) map[String(c.dictValue)] = c.dictName
    if (c.dictCode) map[String(c.dictCode)] = c.dictName
    if (c.dictName) map[String(c.dictName)] = c.dictName
  })
  return map
}

const loadDicts = async () => {
  const res = await dictTree()
  const tree = res?.data || []
  const providerNode = findNodeByCode(tree, 'DICT_MODEL_PROVIDER')
  const modelTypeNode = findNodeByCode(tree, 'DICT_MODEL_TYPE')
  const statusNode = findNodeByCode(tree, 'DICT_MODEL_STATUS')
  providers.value = providerNode?.children || []
  modelTypes.value = modelTypeNode?.children || []
  modelTypeMap.value = buildMap(modelTypeNode)
  statusMap.value = buildMap(statusNode)
}

const displayModelType = (val) => {
  const key = val === undefined || val === null ? '' : String(val)
  return modelTypeMap.value?.[key] || val || '-'
}

const displayStatus = (val) => {
  const key = val === undefined || val === null ? '' : String(val)
  if (statusMap.value?.[key]) return statusMap.value[key]
  if (key === '1' || val === 1) return '启用'
  if (key === '0' || val === 0) return '禁用'
  return val || '-'
}

const load = async () => {
  const res = await aiModels({
    page: page.value - 1,
    size: size.value,
    modelName: keyword.value || '',
    provider: provider.value || '',
  })
  models.value = res?.data?.content || []
  total.value = res?.data?.totalElements || 0
}

const onPageChange = (val) => {
  page.value = val
  load()
}
const onSizeChange = (val) => {
  size.value = val
  page.value = 1
  load()
}

const openCreate = () => {
  dialogMode.value = 'create'
  form.value = { modelName: '', version: '', modelType: '', signatureHandler: '', provider: '', adapterClass: '', status: 1 }
  showDialog.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  const res = await aiModelDetail(row.id)
  form.value = { ...res?.data }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createAiModel(form.value)
  } else {
    await updateAiModel(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteAiModel(row.id)
  load()
}

const openAccounts = async (row) => {
  currentModel.value = row
  showAccounts.value = true
  await loadAccounts()
}

const loadAccounts = async () => {
  const res = await aiAccounts({ modelId: currentModel.value?.id })
  accounts.value = res?.data || []
}

const openAccountForm = () => {
  accountMode.value = 'create'
  accountForm.value = { modelId: currentModel.value?.id, accountName: '', accessKeyId: '', secretAccessKey: '', status: '1' }
  showAccountForm.value = true
}

const editAccount = (row) => {
  accountMode.value = 'edit'
  accountForm.value = { ...row }
  showAccountForm.value = true
}

const saveAccount = async () => {
  if (accountMode.value === 'create') {
    await createAiAccount(accountForm.value)
  } else {
    await updateAiAccount(accountForm.value)
  }
  showAccountForm.value = false
  loadAccounts()
}

const removeAccount = async (row) => {
  await deleteAiAccount(row.id)
  loadAccounts()
}

loadDicts()
load()
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.actions {
  display: flex;
  gap: 10px;
}
.search {
  width: 220px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.dialog-title {
  font-weight: 600;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 3px;
  background: #ffffff;
  color: #22c55e;
  font-size: 12px;
  border: 1px solid rgba(34, 197, 94, 0.35);
  line-height: 1.2;
}
:deep(.dark-dialog .el-dialog__header),
:deep(.dark-dialog .el-dialog__body),
:deep(.dark-dialog .el-dialog__footer) {
  background: #0f1322;
  color: #fff;
}
</style>
