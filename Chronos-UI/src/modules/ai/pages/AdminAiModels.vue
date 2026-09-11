<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">AI 模型管理</div>
        <div class="subtitle">维护 DeepSeek 模型、连接参数、API Key、适配配置和启用状态</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="模型名称" class="search-input" @keyup.enter="search" />
        <el-select v-model="provider" placeholder="提供方" filterable allow-create default-first-option @change="search" style="width: 180px">
          <el-option label="全部" value="" />
          <el-option v-for="item in providers" :key="item.id || item.dictCode" :label="item.dictName" :value="item.dictValue ?? item.dictCode ?? item.dictName" />
        </el-select>
        <el-button v-permission="['ai:model:create', 'ai:model:manage']" type="primary" @click="openCreate">新增模型</el-button>
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
      <el-table-column prop="baseUrl" label="Base URL" min-width="190" show-overflow-tooltip />
      <el-table-column label="API Key" min-width="150">
        <template #default="scope">
          <span v-if="scope.row.hasApiKey">{{ scope.row.maskedApiKey || '已配置' }}</span>
          <span v-else class="muted">未配置</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-switch
            v-permission="['ai:model:update', 'ai:model:manage']"
            :model-value="Number(scope.row.status) === 1"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="toggleStatus(scope.row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="isDefault" label="默认模型" width="110">
        <template #default="scope">
          <el-switch
            v-permission="['ai:model:update', 'ai:model:manage']"
            :model-value="scope.row.isDefault === true"
            inline-prompt
            active-text="是"
            inactive-text="否"
            @change="toggleDefault(scope.row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button v-permission="['ai:model:update', 'ai:model:manage']" size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button v-permission="['ai:model:delete', 'ai:model:manage']" size="small" type="danger" @click="remove(scope.row)">删除</el-button>
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增模型' : '编辑模型'">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请输入或选择模型类型" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="item in modelTypes" :key="item.id || item.dictCode" :label="item.dictName" :value="item.dictValue ?? item.dictCode ?? item.dictName" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-select v-model="form.provider" placeholder="请输入或选择供应商" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="item in providers" :key="item.id || item.dictCode" :label="item.dictName" :value="item.dictValue ?? item.dictCode ?? item.dictName" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.hasApiKey ? `已配置：${form.maskedApiKey || '****'}，留空保持原值` : '请输入 API Key'"
          />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="连接超时(ms)" prop="connectTimeoutMs">
          <el-input-number v-model="form.connectTimeoutMs" :min="100" :max="120000" :step="1000" controls-position="right" />
        </el-form-item>
        <el-form-item label="读取超时(ms)" prop="readTimeoutMs">
          <el-input-number v-model="form.readTimeoutMs" :min="100" :max="600000" :step="1000" controls-position="right" />
        </el-form-item>
        <el-form-item label="调用超时(ms)" prop="callTimeoutMs">
          <el-input-number v-model="form.callTimeoutMs" :min="100" :max="600000" :step="1000" controls-position="right" />
        </el-form-item>
        <el-form-item label="Temperature" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="Max tokens" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="100000" :step="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="Top P" prop="topP">
          <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.1" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="签名处理">
          <el-input v-model="form.signatureHandler" />
        </el-form-item>
        <el-form-item label="适配类">
          <el-input v-model="form.adapterClass" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认模型">
          <el-switch v-model="form.isDefault" active-text="是" inactive-text="否" />
          <span class="form-tip">仅启用且已配置 API Key 的模型可以设为默认</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  aiModels,
  aiModelDetail,
  createAiModel,
  updateAiModel,
  deleteAiModel,
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

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({})
const formRef = ref()
const rules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  apiKey: [{
    validator: (_rule, value, callback) => {
      if (dialogMode.value === 'create' && !String(value || '').trim()) {
        callback(new Error('请输入 API Key'))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

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
  providers.value = providerNode?.children || []
  modelTypes.value = modelTypeNode?.children || []
  modelTypeMap.value = buildMap(modelTypeNode)
}

const displayModelType = (val) => {
  const key = val === undefined || val === null ? '' : String(val)
  return modelTypeMap.value?.[key] || val || '-'
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
const search = () => {
  page.value = 1
  load()
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
  form.value = {
    modelName: '',
    version: '',
    modelType: '',
    signatureHandler: '',
    provider: '',
    apiKey: '',
    maskedApiKey: '',
    hasApiKey: false,
    adapterClass: '',
    baseUrl: 'https://api.deepseek.com',
    connectTimeoutMs: 10000,
    readTimeoutMs: 60000,
    callTimeoutMs: 120000,
    temperature: null,
    maxTokens: null,
    topP: null,
    status: 1,
    isDefault: false,
  }
  showDialog.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  const res = await aiModelDetail(row.id)
  // The API only returns a masked key. Keep the input empty so it can never
  // accidentally send the masked placeholder back as a replacement.
  form.value = { ...res?.data, apiKey: '' }
  showDialog.value = true
}

const submit = async () => {
  await formRef.value?.validate()
  const payload = { ...form.value }
  delete payload.maskedApiKey
  delete payload.hasApiKey
  if (dialogMode.value === 'edit' && !String(payload.apiKey || '').trim()) delete payload.apiKey
  if (dialogMode.value === 'create') await createAiModel(payload)
  else await updateAiModel(payload)
  showDialog.value = false
  ElMessage.success('保存成功')
  await load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除模型“${row.modelName}”吗？`, '删除确认', { type: 'warning' })
  await deleteAiModel(row.id)
  ElMessage.success('删除成功')
  await load()
}

const toggleStatus = async (row, enabled) => {
  const status = enabled ? 1 : 0
  try {
    // Do not send response-only maskedApiKey/hasApiKey fields back as an
    // update command. Omitting apiKey makes the backend retain the secret.
    await updateAiModel({
      id: row.id,
      modelName: row.modelName,
      version: row.version,
      modelType: row.modelType,
      provider: row.provider,
      signatureHandler: row.signatureHandler,
      adapterClass: row.adapterClass,
      baseUrl: row.baseUrl,
      connectTimeoutMs: row.connectTimeoutMs,
      readTimeoutMs: row.readTimeoutMs,
      callTimeoutMs: row.callTimeoutMs,
      temperature: row.temperature,
      maxTokens: row.maxTokens,
      topP: row.topP,
      status,
      isDefault: enabled && row.isDefault === true,
    })
    row.status = status
    if (!enabled) row.isDefault = false
    ElMessage.success(status === 1 ? '模型已启用' : '模型已禁用')
  } catch {
    await load()
  }
}

const toggleDefault = async (row, isDefault) => {
  try {
    await updateAiModel({
      id: row.id,
      modelName: row.modelName,
      version: row.version,
      modelType: row.modelType,
      provider: row.provider,
      signatureHandler: row.signatureHandler,
      adapterClass: row.adapterClass,
      baseUrl: row.baseUrl,
      connectTimeoutMs: row.connectTimeoutMs,
      readTimeoutMs: row.readTimeoutMs,
      callTimeoutMs: row.callTimeoutMs,
      temperature: row.temperature,
      maxTokens: row.maxTokens,
      topP: row.topP,
      status: row.status,
      isDefault: Boolean(isDefault),
    })
    await load()
    ElMessage.success(isDefault ? '已设为默认模型' : '已取消默认模型')
  } catch {
    await load()
  }
}

loadDicts().catch(() => {})
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
:deep(.dark-dialog .el-dialog__header),
:deep(.dark-dialog .el-dialog__body),
:deep(.dark-dialog .el-dialog__footer) {
  background: #0f1322;
  color: #fff;
}
.muted {
  color: var(--el-text-color-secondary);
}
.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
