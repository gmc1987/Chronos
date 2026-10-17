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
      <AdaptiveActionColumn label="操作" width="100">
        <template #default="scope">
          <el-button v-permission="['ai:model:update', 'ai:model:manage']" size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button v-permission="['ai:model:delete', 'ai:model:manage']" size="small" type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </AdaptiveActionColumn>
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

    <el-divider content-position="left">AI 用量与预算治理</el-divider>
    <div class="usage-summary">
      <el-card shadow="never"><el-statistic title="今日请求" :value="usageSummary.requestsToday || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="今日拒绝" :value="usageSummary.rejectedToday || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="今日 Token" :value="usageSummary.tokensToday || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="本月费用" :value="Number(usageSummary.costThisMonth || 0)" :precision="4" prefix="¥" /></el-card>
    </div>

    <div class="section-header">
      <span>限流与预算策略</span>
      <el-button v-permission="['ai:model:manage']" type="primary" @click="openPolicy()">新增策略</el-button>
    </div>
    <el-table :data="usagePolicies" border>
      <el-table-column prop="scopeType" label="范围" width="110" />
      <el-table-column prop="scopeKey" label="范围值" min-width="180" />
      <el-table-column prop="requestsPerMinute" label="每分钟请求" width="130" />
      <el-table-column prop="dailyTokenLimit" label="每日 Token" width="140" />
      <el-table-column prop="monthlyBudget" label="月度预算" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="scope">{{ scope.row.enabled ? '启用' : '停用' }}</template>
      </el-table-column>
      <AdaptiveActionColumn label="操作" width="72">
        <template #default="scope">
          <el-button v-permission="['ai:model:manage']" size="small" @click="openPolicy(scope.row)">编辑</el-button>
        </template>
      </AdaptiveActionColumn>
    </el-table>

    <div class="section-header"><span>调用记录</span><el-button @click="loadUsage">刷新</el-button></div>
    <el-table :data="usageRecords" border>
      <el-table-column prop="createTime" label="时间" min-width="170" />
      <el-table-column prop="username" label="用户" min-width="130" />
      <el-table-column prop="modelId" label="模型 ID" min-width="180" show-overflow-tooltip />
      <el-table-column prop="operation" label="类型" width="100" />
      <el-table-column prop="status" label="结果" width="110" />
      <el-table-column prop="totalTokens" label="Token" width="100" />
      <el-table-column label="计量" width="90">
        <template #default="scope">{{ scope.row.tokenEstimated ? '估算' : '供应商' }}</template>
      </el-table-column>
      <el-table-column prop="costAmount" label="费用" width="110" />
      <el-table-column prop="errorCode" label="错误码" min-width="150" />
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="usageTotal" :current-page="usagePage" :page-size="10" @current-change="onUsagePage" />
    </div>

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增模型' : '编辑模型'">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="填写当前服务地址支持的 API 模型标识" />
          <span class="form-tip">这里是调用 API 的模型标识，不是供应商名称（如 DeepSeek）；请以当前 Base URL 的模型列表为准。</span>
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
        <el-form-item label="输入单价">
          <el-input-number v-model="form.inputCostPerMillion" :min="0" :precision="6" :step="0.1" controls-position="right" />
          <span class="form-tip">每百万 Token 费用</span>
        </el-form-item>
        <el-form-item label="输出单价">
          <el-input-number v-model="form.outputCostPerMillion" :min="0" :precision="6" :step="0.1" controls-position="right" />
          <span class="form-tip">每百万 Token 费用</span>
        </el-form-item>
        <el-form-item label="Embedding维度" prop="embeddingDimension">
          <el-input-number v-model="form.embeddingDimension" :min="1" :max="32768" :step="1" controls-position="right" />
          <span class="form-tip">仅 EMBEDDING 模型需要填写</span>
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
        <el-form-item label="默认Embedding">
          <el-switch v-model="form.embeddingDefault" active-text="是" inactive-text="否" />
          <span class="form-tip">仅 EMBEDDING 模型可以设为默认 Embedding</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPolicyDialog" :title="policyForm.id ? '编辑用量策略' : '新增用量策略'" width="560px">
      <el-form :model="policyForm" label-width="120px">
        <el-form-item label="策略范围" required>
          <el-select v-model="policyForm.scopeType" style="width: 100%">
            <el-option label="全平台" value="GLOBAL" />
            <el-option label="指定模型" value="MODEL" />
            <el-option label="指定用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="policyForm.scopeType !== 'GLOBAL'" label="范围值" required>
          <el-select v-if="policyForm.scopeType === 'MODEL'" v-model="policyForm.scopeKey" filterable style="width: 100%">
            <el-option v-for="item in models" :key="item.id" :label="item.modelName" :value="item.id" />
          </el-select>
          <el-input v-else v-model="policyForm.scopeKey" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="每分钟请求">
          <el-input-number v-model="policyForm.requestsPerMinute" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="每日 Token">
          <el-input-number v-model="policyForm.dailyTokenLimit" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="月度预算">
          <el-input-number v-model="policyForm.monthlyBudget" :min="0" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="policyForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPolicyDialog = false">取消</el-button>
        <el-button type="primary" @click="savePolicy">保存</el-button>
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
  aiUsageSummary,
  aiUsageRecords,
  aiUsagePolicies,
  createAiUsagePolicy,
  updateAiUsagePolicy,
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
const usageSummary = ref({})
const usagePolicies = ref([])
const usageRecords = ref([])
const usageTotal = ref(0)
const usagePage = ref(1)
const showPolicyDialog = ref(false)
const policyForm = ref({})

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
    embeddingDimension: null,
    embeddingDefault: false,
    inputCostPerMillion: null,
    outputCostPerMillion: null,
    status: 1,
    isDefault: false,
  }
  showDialog.value = true
}

const loadUsage = async () => {
  const [summaryRes, policiesRes, recordsRes] = await Promise.all([
    aiUsageSummary(),
    aiUsagePolicies(),
    aiUsageRecords({ page: usagePage.value - 1, size: 10 }),
  ])
  usageSummary.value = summaryRes?.data || {}
  usagePolicies.value = policiesRes?.data || []
  usageRecords.value = recordsRes?.data?.content || []
  usageTotal.value = recordsRes?.data?.totalElements || 0
}

const onUsagePage = (value) => {
  usagePage.value = value
  loadUsage()
}

const openPolicy = (row = null) => {
  policyForm.value = row ? { ...row } : {
    scopeType: 'GLOBAL', scopeKey: '*', requestsPerMinute: null,
    dailyTokenLimit: null, monthlyBudget: null, enabled: true,
  }
  showPolicyDialog.value = true
}

const savePolicy = async () => {
  const payload = { ...policyForm.value }
  if (payload.scopeType === 'GLOBAL') payload.scopeKey = '*'
  if (payload.id) await updateAiUsagePolicy(payload.id, payload)
  else await createAiUsagePolicy(payload)
  showPolicyDialog.value = false
  ElMessage.success('用量策略已保存')
  await loadUsage()
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
      embeddingDimension: row.embeddingDimension,
      embeddingDefault: row.embeddingDefault,
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
      embeddingDimension: row.embeddingDimension,
      embeddingDefault: row.embeddingDefault,
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
loadUsage()
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
.usage-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 10px;
  font-weight: 600;
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
