<template>
  <div class="admin-page">
    <div class="header">
      <div>
        <div class="title">API 管理</div>
        <div class="subtitle">配置模型 API 与请求参数</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="API 名称" class="search-input" @keyup.enter="load" />
        <el-input v-model="methodType" placeholder="Method" class="search-input" @keyup.enter="load" />
        <el-button type="primary" @click="openCreate">新增 API</el-button>
      </div>
    </div>

    <el-table :data="apis" border style="width: 100%" @row-click="openParams">
      <el-table-column prop="apiName" label="API 名称" />
      <el-table-column prop="apiCode" label="API Code" />
      <el-table-column prop="apiPath" label="路径" />
      <el-table-column prop="methodType" label="Method" width="100" />
      <el-table-column prop="contentType" label="content-type" width="200" />
      <el-table-column prop="timeoutMs" label="超时（ms）" width="120"/>
      <el-table-column label="操作" width="240">
        <template #default="scope">
          <el-button size="small" @click.stop="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" @click.stop="openParams(scope.row)">参数配置</el-button>
          <el-button size="small" type="danger" @click.stop="remove(scope.row)">删除</el-button>
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

    <el-dialog v-model="showDialog" :title="dialogMode === 'create' ? '新增 API' : '编辑 API'">
      <el-form label-width="110px">
        <el-form-item label="模型">
          <el-select v-model="form.modelId" filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="m in modelOptions" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 名称">
          <el-input v-model="form.apiName" />
        </el-form-item>
        <el-form-item label="API Code">
          <el-input v-model="form.apiCode" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.apiPath" />
        </el-form-item>
        <el-form-item label="Method">
          <el-select v-model="form.methodType">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Content-Type">
          <el-input v-model="form.contentType" />
        </el-form-item>
        <el-form-item label="异步">
          <el-switch v-model="form.isAsync" />
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" />
        </el-form-item>
        <el-form-item label="动作">
          <el-input-number v-model="form.action" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showParams" direction="rtl" size="38%" :with-header="false" class="dark-drawer">
      <div class="drawer-header">
        <div>{{ currentApi?.apiName }}</div>
        <el-button size="small" type="primary" @click="openParamForm">新增参数</el-button>
      </div>
      <div class="param-group" v-for="group in groupedParams" :key="group.type">
        <div class="param-title">{{ group.label }}</div>
        <div v-if="group.items.length" class="param-list">
          <div v-for="item in group.items" :key="item.id" class="param-card">
            <div class="card-actions">
              <el-button size="small" @click="editParam(item)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeParam(item)">删除</el-button>
            </div>
            <div class="card-row"><span class="label">参数名称：</span>{{ item.paramName || '-' }}</div>
            <div class="card-row"><span class="label">参数 Key：</span>{{ item.paramKey || '-' }}</div>
            <div class="card-row"><span class="label">参数类型：</span>{{ typeLabel(item.paramType) }}</div>
            <div class="card-row"><span class="label">数据类型：</span>{{ item.dataType || '-' }}</div>
            <div class="card-row"><span class="label">默认值：</span>{{ item.defaultValue || '-' }}</div>
            <div class="card-row"><span class="label">必填：</span>{{ item.isNotBlank ? '是' : '否' }}</div>
            <div class="card-row"><span class="label">前端显示：</span>{{ item.isVisible ? '是' : '否' }}</div>
            <div class="card-row"><span class="label">描述：</span>{{ item.description || '-' }}</div>
          </div>
        </div>
        <div v-else class="param-empty">暂无参数</div>
      </div>
    </el-drawer>

    <el-dialog v-model="showParamForm" :title="paramMode === 'create' ? '新增参数' : '编辑参数'" class="dark-dialog">
      <el-form label-width="110px">
        <el-form-item label="参数类型">
          <el-select v-model="paramForm.paramType">
            <el-option :value="0" label="Header" />
            <el-option :value="1" label="Query" />
            <el-option :value="2" label="Body" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数名称">
          <el-input v-model="paramForm.paramName" />
        </el-form-item>
        <el-form-item label="参数 Key">
          <el-input v-model="paramForm.paramKey" />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-input v-model="paramForm.dataType" />
        </el-form-item>
        <el-form-item label="默认值">
          <el-input v-model="paramForm.defaultValue" />
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="paramForm.isNotBlank" />
        </el-form-item>
        <el-form-item label="是否在前端显示">
          <el-switch v-model="paramForm.isVisible" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="paramForm.description" type="textarea" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showParamForm = false">取消</el-button>
        <el-button type="primary" @click="saveParam">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  aiApis,
  aiApiDetail,
  createAiApi,
  updateAiApi,
  deleteAiApi,
  aiModels,
  apiParams,
  createApiParam,
  updateApiParam,
  deleteApiParam,
} from '../api'

const apis = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const methodType = ref('')

const showDialog = ref(false)
const dialogMode = ref('create')
const form = ref({
  modelId: '',
  contentType: 'application/json',
  apiName: '',
  apiCode: '',
  apiPath: '',
  methodType: 'POST',
  isAsync: false,
  timeoutMs: 60000,
  isVisible: false,
  description: '',
  action: 0,
})

const modelOptions = ref([])

const showParams = ref(false)
const currentApi = ref(null)
const params = ref([])

const showParamForm = ref(false)
const paramMode = ref('create')
const paramForm = ref({
  apiId: '',
  paramType: 0,
  paramName: '',
  paramKey: '',
  dataType: '',
  defaultValue: '',
  isNotBlank: false,
  isVisible: false,
  description: '',
})

const typeLabel = (t) => {
  if (t === 0) return 'Header'
  if (t === 1) return 'Query'
  if (t === 2) return 'Body'
  return t ?? '-'
}

const groupParams = (list = []) => {
  const groups = [
    { type: 0, label: 'Header', items: [] },
    { type: 1, label: 'Query', items: [] },
    { type: 2, label: 'Body', items: [] },
  ]
  list.forEach((p) => {
    const g = groups.find((x) => x.type === p.paramType)
    if (g) g.items.push(p)
  })
  return groups
}

const groupedParams = computed(() => groupParams(params.value))

const loadModels = async () => {
  const res = await aiModels({ page: 0, size: 200 })
  modelOptions.value = res?.data?.content || []
}

const load = async () => {
  const res = await aiApis({
    page: page.value - 1,
    size: size.value,
    apiName: keyword.value || '',
    methodType: methodType.value || '',
  })
  apis.value = res?.data?.content || []
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

const openCreate = async () => {
  dialogMode.value = 'create'
  await loadModels()
  form.value = { contentType: 'application/json', apiName: '', apiCode: '', apiPath: '', methodType: 'POST', isAsync: false, timeoutMs: 60000, description: '', action: 0, modelId: '' }
  showDialog.value = true
}

const openEdit = async (row) => {
  dialogMode.value = 'edit'
  await loadModels()
  const res = await aiApiDetail(row.id)
  form.value = { ...res?.data }
  showDialog.value = true
}

const submit = async () => {
  if (dialogMode.value === 'create') {
    await createAiApi(form.value)
  } else {
    await updateAiApi(form.value)
  }
  showDialog.value = false
  load()
}

const remove = async (row) => {
  await deleteAiApi(row.id)
  load()
}

const openParams = async (row) => {
  if (!row?.id) return
  currentApi.value = row
  showParams.value = true
  const res = await apiParams({ apiId: row.id })
  params.value = res?.data || []
}

const openParamForm = () => {
  paramMode.value = 'create'
  paramForm.value = { apiId: currentApi.value?.id, paramType: 0, paramName: '', paramKey: '', dataType: '', defaultValue: '', isNotBlank: false, isVisible: false, description: '' }
  showParamForm.value = true
}

const editParam = (row) => {
  paramMode.value = 'edit'
  paramForm.value = { ...row }
  showParamForm.value = true
}

const saveParam = async () => {
  if (paramMode.value === 'create') {
    await createApiParam(paramForm.value)
  } else {
    await updateApiParam(paramForm.value)
  }
  showParamForm.value = false
  const res = await apiParams({ apiId: currentApi.value?.id })
  params.value = res?.data || []
}

const removeParam = async (row) => {
  await deleteApiParam(row.id)
  const res = await apiParams({ apiId: currentApi.value?.id })
  params.value = res?.data || []
}

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
  width: 200px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 12px;
  color: #fff;
  font-size: 12px;
}
.param-group {
  margin-bottom: 12px;
}
.param-title {
  font-weight: 600;
  margin-bottom: 6px;
  color: #c7d2fe;
  font-size: 12px;
}
.param-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.param-card {
  position: relative;
  background: #1a1f33;
  padding: 10px;
  border-radius: 8px;
  color: #e5e7eb;
  font-size: 12px;
  border: 1px solid #252b45;
}
.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}
.card-row {
  margin-right: 90px;
  line-height: 1.6;
}
.label {
  color: #9ca3af;
}
.param-empty {
  color: #9ca3af;
  font-size: 12px;
}
:deep(.dark-drawer .el-drawer__body) {
  background: #0f1322;
}
:deep(.dark-drawer .el-drawer__header) {
  background: #0f1322;
  color: #fff;
}
:deep(.dark-drawer .el-drawer__body) {
  color: #fff;
}
:deep(.dark-dialog .el-dialog__header),
:deep(.dark-dialog .el-dialog__body),
:deep(.dark-dialog .el-dialog__footer) {
  background: #0f1322;
  color: #fff;
}
</style>
