<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'

const props = defineProps({ connectors: { type: Array, default: () => [] } })
const emit = defineEmits(['saved'])
const endpoint = '/admin/integrations'
const titles = { connector: '新建连接器', mapping: '新增字段映射', job: '新建同步任务', credential: '设置连接凭据' }
const visible = ref(false)
const mode = ref('connector')
const saving = ref(false)
const form = reactive({})
const mappings = ref([])
let mappingLoad = 0
watch(() => [mode.value, form.connectorId, form.objectType, form.versionNo], async ([current, connector, objectType, version]) => {
  const sequence = ++mappingLoad
  mappings.value = []
  if (current !== 'mapping' || !connector || !objectType || !version) return
  const query = new URLSearchParams({ objectType, versionNo: version })
  const result = await http.get(`${endpoint}/connectors/${connector}/mappings?${query}`)
  if (sequence === mappingLoad) mappings.value = result.data || []
})

function selectMapping(id) {
  const selected = mappings.value.find(item => item.id === id)
  form.mappingId = id
  form.sourcePath = selected?.sourcePath || ''
  form.targetField = selected?.targetField || ''
  form.transformCode = selected?.transformCode
  form.required = selected?.required ?? true
}
const simulation = computed(() => form.providerCode === 'TRUSTED_SIMULATION')

function open(target) {
  Object.keys(form).forEach(key => delete form[key])
  mode.value = target
  Object.assign(form, {
    connectorId: props.connectors[0]?.id || '',
    providerCode: 'HTTP_JSON',
    objectType: 'DEFAULT',
    versionNo: 1,
    mappingVersionNo: 1,
    timeoutMs: 10000,
    maxRetries: 3,
    batchSize: 100,
    requestPath: '/',
    cronExpression: '0 0 2 * * *',
    status: 'PAUSED',
    required: true,
  })
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    if (mode.value === 'connector') {
      const configJson = form.configJson || '{}'
      JSON.parse(configJson)
      const parsed = simulation.value ? null : new URL(form.baseUrl)
      await http.post(`${endpoint}/connectors`, {
        name: form.name, type: 'HTTP', providerCode: form.providerCode,
        baseUrl: parsed?.href || 'simulation', allowedHost: parsed?.hostname,
        timeoutMs: form.timeoutMs, maxRetries: form.maxRetries, configJson,
      })
    } else if (mode.value === 'mapping') {
      const url = `${endpoint}/connectors/${form.connectorId}/mappings`
      const mapping = {
        objectType: form.objectType, versionNo: form.versionNo, sourcePath: form.sourcePath,
        targetField: form.targetField, transformCode: form.transformCode, required: form.required,
      }
      if (form.mappingId) await http.put(`${url}/${form.mappingId}`, mapping)
      else await http.post(url, mapping)
    } else if (mode.value === 'job') {
      const { connectorId, name, requestPath, objectType, mappingVersionNo, batchSize, cronExpression, status } = form
      await http.post(`${endpoint}/jobs`, {
        connectorId, name, requestPath, objectType, mappingVersionNo, batchSize, cronExpression, status,
      })
    } else {
      await http.post(`${endpoint}/connectors/${form.connectorId}/credentials`, {
        keyName: form.keyName, secret: form.secret,
      })
    }
    delete form.secret
    visible.value = false
    ElMessage.success('设置已保存')
    emit('saved')
  } catch (error) {
    if (error instanceof SyntaxError || error instanceof TypeError) ElMessage.warning('请检查 URL 或配置 JSON 的格式')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="setup-actions">
    <el-button type="primary" @click="open('connector')">新建连接器</el-button>
    <el-button :disabled="!connectors.length" @click="open('mapping')">新增字段映射</el-button>
    <el-button :disabled="!connectors.length" @click="open('job')">新建同步任务</el-button>
    <el-button :disabled="!connectors.length" @click="open('credential')">设置连接凭据</el-button>
  </div>
  <el-dialog v-model="visible" :title="titles[mode]" width="580px" @closed="delete form.secret">
    <el-form label-width="110px" @submit.prevent>
      <el-form-item v-if="mode !== 'connector'" label="连接器">
        <el-select v-model="form.connectorId" filterable>
          <el-option v-for="item in connectors" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <template v-if="mode === 'connector'">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="120" /></el-form-item>
        <el-form-item label="连接类型">
          <el-select v-model="form.providerCode">
            <el-option value="HTTP_JSON" label="HTTP JSON" />
            <el-option value="TRUSTED_SIMULATION" label="内置仿真" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!simulation" label="基础地址"><el-input v-model="form.baseUrl" placeholder="https://上游服务地址" /></el-form-item>
        <el-form-item label="配置 JSON">
          <el-input v-model="form.configJson" type="textarea" :rows="5" placeholder="HTTP JSON 可留空；仿真配置须包含 pages 对象" />
        </el-form-item>
      </template>
      <template v-else-if="mode === 'mapping'">
        <el-form-item label="业务对象"><el-input v-model="form.objectType" /></el-form-item>
        <el-form-item label="映射版本"><el-input-number v-model="form.versionNo" :min="1" /></el-form-item>
        <el-form-item v-if="mappings.length" label="已有映射">
          <el-select :model-value="form.mappingId" clearable placeholder="选中可修改已有映射" @change="selectMapping">
            <el-option v-for="item in mappings" :key="item.id" :value="item.id" :label="item.targetField" />
          </el-select>
        </el-form-item>
        <el-form-item label="源字段路径"><el-input v-model="form.sourcePath" /></el-form-item>
        <el-form-item label="目标字段"><el-input v-model="form.targetField" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="form.required" /></el-form-item>
      </template>
      <template v-else-if="mode === 'job'">
        <el-form-item label="任务名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="请求路径"><el-input v-model="form.requestPath" /></el-form-item>
        <el-form-item label="业务对象"><el-input v-model="form.objectType" /></el-form-item>
        <el-form-item label="映射版本"><el-input-number v-model="form.mappingVersionNo" :min="1" /></el-form-item>
        <el-form-item label="批量大小"><el-input-number v-model="form.batchSize" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="调度 Cron"><el-input v-model="form.cronExpression" /></el-form-item>
        <el-form-item label="启用调度"><el-switch v-model="form.status" active-value="ENABLED" inactive-value="PAUSED" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="凭据头名称"><el-input v-model="form.keyName" placeholder="Authorization" /></el-form-item>
        <el-form-item label="凭据值"><el-input v-model="form.secret" type="password" autocomplete="new-password" /></el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.setup-actions { display: flex; gap: 8px; flex-wrap: wrap; margin: 12px 0; }
</style>
