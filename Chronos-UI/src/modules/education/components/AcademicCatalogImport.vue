<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'

const props = defineProps({
  kind: { type: String, required: true },
  label: { type: String, required: true },
  organizationId: String,
  endpoint: String,
  templateUrl: String,
  buttonLabel: String,
  params: Object,
  disabled: Boolean,
  readOnly: Boolean,
  extension: { type: String, default: 'xlsx' },
})
const emit = defineEmits(['imported'])
const visible = ref(false)
const busy = ref(false)
const file = ref(null)
const preview = ref(null)
const result = computed(() => preview.value?.result)
const history = ref(null)
const historyPage = ref(0)
const batchUrl = () => '/admin/education/catalog-import/' + props.kind
const baseUrl = () => props.endpoint || '/admin/education/catalog-import/' + props.kind

const scope = computed(() => new URLSearchParams({ organizationId: props.organizationId || '', ...props.params }).toString())
const canOpen = computed(() => !props.disabled && (props.organizationId || props.params?.resourceId))
watch(scope, () => {
  visible.value = false
  file.value = null
  preview.value = null
  history.value = null
})

const open = () => {
  file.value = null
  preview.value = null
  visible.value = true
  history.value = null
  historyPage.value = 0
}

const download = async () => {
  await saveFile(props.templateUrl || baseUrl() + '/template', props.label + '批量导入模板.' + props.extension)
}

const saveFile = async (path, filename) => {
  const blob = await http.download(path)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const selectFile = event => {
  file.value = event.target.files?.[0] || null
  preview.value = null
}

const check = async () => {
  const target = scope.value
  busy.value = true
  preview.value = null
  try {
    const data = new FormData()
    data.append('file', file.value)
    const query = new URLSearchParams(target)
    query.set('templateVersion', '1.0')
    const response = await http.upload(batchUrl() + '/preview?' + query, data)
    if (target === scope.value) preview.value = response.data
  } finally {
    busy.value = false
  }
}

const commit = async () => {
  const target = scope.value
  busy.value = true
  try {
    const response = await http.post(batchUrl() + '/commit', {
      ...Object.fromEntries(new URLSearchParams(target)),
      previewId: preview.value.id,
      templateVersion: preview.value.templateVersion,
    })
    if (target !== scope.value) return
    ElMessage.success('已导入 ' + response.data.result.validCount + ' 条' + props.label)
    visible.value = false
    emit('imported')
  } finally {
    busy.value = false
  }
}

const loadHistory = async (page = 0) => {
  const target = scope.value
  busy.value = true
  try {
    const query = new URLSearchParams(target)
    query.set('page', page)
    const response = await http.get(batchUrl() + '/batches?' + query)
    if (target !== scope.value) return
    historyPage.value = page
    history.value = response.data
  } finally {
    busy.value = false
  }
}

const source = row => saveFile(batchUrl() + '/batches/' + row.id + '/source?' + scope.value, props.label + '-原文件.' + props.extension)
const status = row => {
  if (row.committedAt) return '已导入'
  if (row.errorCount) return '校验未通过'
  return Date.parse(row.expiresAt) <= Date.now() ? '已过期' : '待提交'
}
</script>

<template>
  <el-button :disabled="!canOpen" @click="open">{{ buttonLabel || '批量导入' }}</el-button>
  <el-dialog v-model="visible" :title="label + '批量导入'" width="580px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
    <p v-if="readOnly">可以查看本业务的历史批次及原文件。</p>
    <p v-else-if="kind === 'ASSIGNMENTS'">使用 XLSX 模板填写数据。课程编码可补录到已有任教关系，重复提交同一课程会保留原记录。</p>
    <p v-else-if="kind === 'ACCOMMODATION_RESOURCES'">按楼栋编码和房间号新增或更新宿舍资源，并自动补齐床位。</p>
    <p v-else-if="kind === 'QUESTIONS'">按 UTF-8 CSV 模板填写，题库列留空时使用当前所选题库。</p>
    <p v-else-if="kind === 'GRADEBOOKS'">按模板更新可维护的成绩，提交前会检查成绩册是否变化。</p>
    <p v-else-if="kind === 'TEACHER_PROFILES'">按工号更新已有档案的专业方向和最大周课时。</p>
    <p v-else>使用 XLSX 模板填写数据。校验通过后可确认导入，重复记录会整批拦截。</p>
    <el-button v-if="!readOnly" link type="primary" @click="download">下载导入模板</el-button>
    <el-button link type="primary" :disabled="busy" @click="loadHistory()">导入历史</el-button>
    <input v-if="!readOnly" type="file" :accept="`.${extension}`" :disabled="busy" aria-label="导入文件" @change="selectFile" />
    <template v-if="result">
      <p>共 {{ result.rowCount }} 行，校验通过 {{ result.validCount }} 行。</p>
      <el-scrollbar v-if="result.errors.length" max-height="260px">
        <ul><li v-for="error in result.errors" :key="error">{{ error }}</li></ul>
      </el-scrollbar>
    </template>
    <template v-if="history">
      <el-table :data="history.content" max-height="250" empty-text="暂无导入记录">
        <el-table-column prop="id" label="批次编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="70" />
        <el-table-column label="状态" width="100"><template #default="{ row }">{{ status(row) }}</template></el-table-column>
        <el-table-column label="原文件" width="100">
          <template #default="{ row }"><el-button link type="primary" @click="source(row)">下载原文件</el-button></template>
        </el-table-column>
      </el-table>
      <el-button :disabled="busy || !historyPage" @click="loadHistory(historyPage - 1)">上一页</el-button>
      <el-button :disabled="busy || !history.hasMore" @click="loadHistory(historyPage + 1)">下一页</el-button>
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">关闭</el-button>
      <el-button v-if="!readOnly" :disabled="!file || busy" @click="check">校验文件</el-button>
      <el-button v-if="!readOnly" type="primary" :disabled="busy || !result?.validCount || !!result.errors.length" @click="commit">确认导入</el-button>
    </template>
  </el-dialog>
</template>
