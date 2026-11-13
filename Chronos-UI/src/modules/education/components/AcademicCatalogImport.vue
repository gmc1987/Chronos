<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../../api/http'

const props = defineProps({
  kind: { type: String, required: true },
  label: { type: String, required: true },
  organizationId: String,
  endpoint: String,
  buttonLabel: String,
  params: Object,
})
const emit = defineEmits(['imported'])
const visible = ref(false)
const busy = ref(false)
const file = ref(null)
const result = ref(null)
const baseUrl = () => props.endpoint || '/admin/education/catalog-import/' + props.kind

const scope = computed(() => new URLSearchParams({ organizationId: props.organizationId || '', ...props.params }).toString())
watch(scope, () => {
  visible.value = false
  file.value = null
  result.value = null
})

const open = () => {
  file.value = null
  result.value = null
  visible.value = true
}

const download = async () => {
  const blob = await http.download(baseUrl() + '/template')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.label + '批量导入模板.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

const selectFile = event => {
  file.value = event.target.files?.[0] || null
  result.value = null
}

const upload = async dryRun => {
  if (!file.value) return
  const target = scope.value
  busy.value = true
  try {
    const data = new FormData()
    data.append('file', file.value)
    const query = new URLSearchParams(target)
    query.set('dryRun', dryRun)
    const response = await http.upload(baseUrl() + '?' + query, data)
    if (target !== scope.value) return
    result.value = response.data
    if (!dryRun && !result.value.errors.length) {
      ElMessage.success('已导入 ' + result.value.validCount + ' 条' + props.label)
      visible.value = false
      emit('imported')
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-button :disabled="!organizationId" @click="open">{{ buttonLabel || '批量导入' }}</el-button>
  <el-dialog v-model="visible" :title="label + '批量导入'" width="580px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" destroy-on-close>
    <p v-if="kind === 'ASSIGNMENTS'">使用 XLSX 模板填写数据。课程编码可补录到已有任教关系，重复提交同一课程会保留原记录。</p>
    <p v-else-if="kind === 'TEACHER_PROFILES'">按工号更新已有档案的专业方向和最大周课时。</p>
    <p v-else>使用 XLSX 模板填写数据。校验通过后可确认导入，重复记录会整批拦截。</p>
    <el-button link type="primary" @click="download">下载导入模板</el-button>
    <input type="file" accept=".xlsx" :disabled="busy" aria-label="导入文件" @change="selectFile" />
    <template v-if="result">
      <p>共 {{ result.rowCount }} 行，校验通过 {{ result.validCount }} 行。</p>
      <el-scrollbar v-if="result.errors.length" max-height="260px">
        <ul><li v-for="error in result.errors" :key="error">{{ error }}</li></ul>
      </el-scrollbar>
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">关闭</el-button>
      <el-button :disabled="!file || busy" @click="upload(true)">校验文件</el-button>
      <el-button type="primary" :disabled="busy || !result?.validCount || !!result.errors.length" @click="upload(false)">确认导入</el-button>
    </template>
  </el-dialog>
</template>
