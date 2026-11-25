<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'

const props = defineProps({
  resource: { type: String, required: true },
  label: { type: String, required: true },
  organizationId: String,
  termId: String,
  targetId: String,
})
const emit = defineEmits(['imported'])
const open = ref(false)
const file = ref(null)
const preview = ref(null)
const busy = ref(false)
const history = ref(null)
const historyPage = ref(0)
const scope = computed(() => ({
  organizationId: props.organizationId,
  termId: props.termId,
  ...(props.targetId ? { targetId: props.targetId } : {}),
}))
const path = computed(() => '/admin/education/' + props.resource + '/import')
const canPreview = computed(() => props.organizationId && props.termId)

watch(scope, () => {
  open.value = false
  file.value = null
  preview.value = null
  history.value = null
})

const show = () => {
  file.value = null
  preview.value = null
  history.value = null
  historyPage.value = 0
  open.value = true
}

const download = async () => {
  const blob = await http.download(path.value + '/template')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.label + '导入模板.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

const selectFile = event => {
  file.value = event.target.files?.[0] ?? null
  preview.value = null
}

const check = async () => {
  const target = scope.value
  const body = new FormData()
  body.append('file', file.value)
  busy.value = true
  try {
    const query = new URLSearchParams({ ...target, templateVersion: '1.0' })
    const result = await http.upload(path.value + '/preview?' + query, body)
    if (scope.value === target) preview.value = result.data
  } finally {
    busy.value = false
  }
}

const loadHistory = async (page = 0) => {
  const target = scope.value
  busy.value = true
  try {
    const query = new URLSearchParams({ ...target, page })
    const result = await http.get(path.value + '/batches?' + query)
    if (scope.value !== target) return
    historyPage.value = page
    history.value = result.data
  } finally {
    busy.value = false
  }
}

const downloadSource = async row => {
  const query = new URLSearchParams(scope.value)
  const blob = await http.download(path.value + '/batches/' + row.id + '/source?' + query)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.label + '-原文件.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

const batchStatus = row => {
  if (row.committedAt) return '已导入'
  if (row.errorCount) return '校验未通过'
  return Date.parse(row.expiresAt) <= Date.now() ? '已过期' : '待提交'
}

const commit = async () => {
  const target = scope.value
  busy.value = true
  try {
    const result = await http.post(path.value + '/commit', {
      ...target,
      previewId: preview.value.id,
      templateVersion: preview.value.templateVersion,
    })
    if (scope.value !== target) return
    ElMessage.success('已导入 ' + result.data.preview.rows.length + ' 条' + props.label)
    open.value = false
    emit('imported')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-button :disabled="!canPreview" @click="show">{{ label }}批量导入</el-button>
  <el-dialog v-model="open" :title="label + '批量导入'" width="650px" destroy-on-close
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <p>使用 XLSX 模板填写，校验通过后一次性新增全部记录。</p>
    <el-button link type="primary" @click="download">下载导入模板</el-button>
    <el-button link type="primary" :disabled="busy" @click="loadHistory(0)">导入历史</el-button>
    <input type="file" accept=".xlsx" aria-label="导入文件" :disabled="busy" @change="selectFile" />
    <template v-if="preview">
      <p>共 {{ preview.rows.length }} 行<span v-if="!preview.errors.length">，校验通过</span>。</p>
      <el-scrollbar v-if="preview.errors.length" max-height="260px">
        <ul><li v-for="error in preview.errors" :key="error">{{ error }}</li></ul>
      </el-scrollbar>
      <el-table v-else :data="preview.rows" max-height="320">
        <el-table-column prop="line" label="行号" width="70" />
        <el-table-column label="导入内容">
          <template #default="{ row }">{{ row.values.join(' · ') }}</template>
        </el-table-column>
      </el-table>
    </template>
    <template v-if="history">
      <el-table :data="history.content" max-height="250" empty-text="暂无当前目标的导入记录">
        <el-table-column prop="id" label="批次编号" min-width="250" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="70" />
        <el-table-column label="状态" width="115">
          <template #default="{ row }">{{ batchStatus(row) }}</template>
        </el-table-column>
        <el-table-column label="原文件" width="105">
          <template #default="{ row }"><el-button v-if="row.sourceAvailable" link type="primary" @click="downloadSource(row)">下载原文件</el-button></template>
        </el-table-column>
      </el-table>
      <el-button :disabled="busy || !historyPage" @click="loadHistory(historyPage - 1)">上一页</el-button>
      <el-button :disabled="busy || !history.hasMore" @click="loadHistory(historyPage + 1)">下一页</el-button>
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="open = false">取消</el-button>
      <el-button :disabled="!file || busy" @click="check">校验文件</el-button>
      <el-button type="primary" :disabled="busy || !preview || !!preview.errors.length" @click="commit">确认导入</el-button>
    </template>
  </el-dialog>
</template>
