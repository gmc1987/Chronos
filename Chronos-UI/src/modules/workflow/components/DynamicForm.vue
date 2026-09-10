<template>
  <el-form :model="value" label-width="120px">
    <el-form-item v-for="field in fields" :key="field.fieldKey" :label="field.fieldLabel" :required="field.required">
      <el-input v-if="field.fieldType === 'TEXT'" v-model="value[field.fieldKey]" :disabled="readonly(field)" />
      <el-input v-else-if="field.fieldType === 'TEXTAREA'" v-model="value[field.fieldKey]" type="textarea" :disabled="readonly(field)" />
      <el-input-number v-else-if="field.fieldType === 'NUMBER'" v-model="value[field.fieldKey]" :disabled="readonly(field)" style="width:100%" />
      <el-date-picker v-else-if="field.fieldType === 'DATE'" v-model="value[field.fieldKey]" type="date" value-format="YYYY-MM-DD" :disabled="readonly(field)" style="width:100%" />
      <el-date-picker v-else-if="field.fieldType === 'DATETIME'" v-model="value[field.fieldKey]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :disabled="readonly(field)" style="width:100%" />
      <el-select v-else-if="field.fieldType === 'SELECT'" v-model="value[field.fieldKey]" :disabled="readonly(field)" style="width:100%"><el-option v-for="option in options(field)" :key="option.value" :label="option.label" :value="option.value" /></el-select>
      <el-radio-group v-else-if="field.fieldType === 'RADIO'" v-model="value[field.fieldKey]" :disabled="readonly(field)"><el-radio v-for="option in options(field)" :key="option.value" :value="option.value">{{ option.label }}</el-radio></el-radio-group>
      <el-checkbox-group v-else-if="field.fieldType === 'CHECKBOX'" v-model="value[field.fieldKey]" :disabled="readonly(field)"><el-checkbox v-for="option in options(field)" :key="option.value" :value="option.value">{{ option.label }}</el-checkbox></el-checkbox-group>
      <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="value[field.fieldKey]" :disabled="readonly(field)" />
      <el-upload
        v-else-if="field.fieldType === 'FILE'"
        :file-list="fileList(field)"
        :http-request="(request) => upload(field, request)"
        :disabled="readonly(field)"
        :limit="5"
        :on-exceed="onExceed"
        :on-preview="preview"
        :on-remove="(file) => remove(field, file)"
      >
        <el-button :disabled="readonly(field)" :loading="uploading[field.fieldKey]">
          上传附件
        </el-button>
        <template #tip>
          <div class="el-upload__tip">支持 PDF、Office、文本和图片，单个不超过 20MB，最多 5 个</div>
        </template>
      </el-upload>
      <el-input v-else v-model="value[field.fieldKey]" :disabled="readonly(field)" />
    </el-form-item>
  </el-form>
</template>
<script setup>
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
	deleteManagedFile,
	dictionaryOptions,
	downloadManagedFile,
	uploadManagedFile,
} from '../../../api/admin'

const value = defineModel({ type: Object, required: true })
const emit = defineEmits(['uploading-change'])
const props = defineProps({
  fields: { type: Array, default: () => [] },
  businessId: { type: String, default: '' },
})

const uploading = reactive({})
const dictionaryData = reactive({})
let pendingUploadCount = 0
const readonly = (field) => field.permission && field.permission !== 'EDIT'
const optionConfig = (field) => {
  try {
    return JSON.parse(field.optionsJson || '[]')
  } catch {
    return []
  }
}
const options = (field) => {
  const config = optionConfig(field)
  if (Array.isArray(config)) {
    return config
  }
  return config?.dictCode ? dictionaryData[config.dictCode] || [] : []
}

const loadDictionaries = async (fields) => {
  const codes = [...new Set((fields || [])
    .map((field) => optionConfig(field)?.dictCode)
    .filter(Boolean))]
  await Promise.all(codes.map(async (code) => {
    if (dictionaryData[code]) {
      return
    }
    try {
      const response = await dictionaryOptions(code)
      dictionaryData[code] = (response?.data || []).map((item) => ({
        label: item.dictName,
        value: item.dictValue,
      }))
    } catch (error) {
      dictionaryData[code] = []
      ElMessage.warning(`字典 ${code} 加载失败，请联系管理员检查字典配置`)
    }
  }))
}

watch(() => props.fields, loadDictionaries, { immediate: true })

const attachments = (field) => {
  const current = value.value[field.fieldKey]
  return Array.isArray(current) ? current : []
}

const fileList = (field) => attachments(field).map((file) => ({
  ...file,
  uid: file.id,
  status: 'success',
  url: file.downloadUrl,
}))

const upload = async (field, request) => {
  uploading[field.fieldKey] = true
  pendingUploadCount += 1
  emit('uploading-change', true)
  try {
    const response = await uploadManagedFile(
      request.file,
      props.businessId ? 'WORKFLOW_FORM' : 'WORKFLOW_FORM_DRAFT',
      props.businessId || undefined,
    )
    const uploaded = response?.data
    if (!uploaded?.id) {
      throw new Error('文件服务未返回有效的文件标识')
    }
    value.value[field.fieldKey] = [...attachments(field), uploaded]
    request.onSuccess(uploaded)
  } catch (error) {
    request.onError(error)
    ElMessage.error(`附件上传失败：${error?.message || '请求失败'}`)
  } finally {
    uploading[field.fieldKey] = false
    pendingUploadCount -= 1
    emit('uploading-change', pendingUploadCount > 0)
  }
}

const remove = async (field, file) => {
  const id = file.id || file.response?.id
  if (!id) return

  try {
    await deleteManagedFile(id)
    value.value[field.fieldKey] = attachments(field).filter((item) => item.id !== id)
  } catch (error) {
    ElMessage.error(`附件删除失败：${error?.message || '请求失败'}`)
  }
}

const preview = async (file) => {
  const id = file.id || file.response?.id
  if (!id) return

  try {
    const blob = await downloadManagedFile(id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.name || '附件'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(`附件下载失败：${error?.message || '请求失败'}`)
  }
}

const onExceed = () => ElMessage.warning('每个字段最多上传 5 个附件')
</script>
