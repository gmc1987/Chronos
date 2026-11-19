<template>
  <div class="suspension-controls">
    <el-button v-if="canApply" type="primary" @click="open">批量停课</el-button>
  </div>
  <el-dialog v-model="visible" title="批量停课" width="min(960px, 94vw)" :close-on-click-modal="false">
    <el-form :inline="true" label-position="top" @submit.prevent>
      <el-form-item label="停课日期">
        <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD"
          start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item label="校区">
        <el-select v-model="campusId" clearable filterable placeholder="全部校区" style="width: 180px">
          <el-option v-for="item in campusOptions" :key="item.id" :label="item.organizationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="专业">
        <el-select v-model="majorId" clearable filterable placeholder="全部专业" style="width: 180px">
          <el-option v-for="item in majors" :key="item.id" :label="item.majorName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="行政班">
        <el-select v-model="classId" clearable filterable placeholder="全部行政班" style="width: 180px">
          <el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="停课原因" class="reason-field">
        <el-input v-model="reason" type="textarea" :maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="preview">
      <p>影响 {{ preview.impacts.length }} 个课次，冲突 {{ conflicts }} 个</p>
      <el-table :data="preview.impacts" border max-height="350" empty-text="该范围没有课次">
        <el-table-column prop="occurrence.date" label="原日期" width="120" />
        <el-table-column prop="occurrence.effectivePeriodNo" label="原节次" width="85" />
        <el-table-column prop="occurrence.entry.courseName" label="课程" min-width="120" />
        <el-table-column prop="occurrence.entry.teachingClassName" label="教学班" min-width="150" />
        <el-table-column prop="occurrence.entry.teacherName" label="教师" min-width="95" />
        <el-table-column label="调整结果" min-width="160">
          <template #default="{ row }">{{ row.conflict || '停课，转入待补课' }}</template>
        </el-table-column>
      </el-table>
    </template>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :disabled="!dates?.length || applying" :loading="loading" @click="loadPreview">预览影响</el-button>
      <el-button type="primary" :disabled="!preview?.impacts.length || !!conflicts || !reason.trim() || loading"
        :loading="applying" @click="apply">确认停课</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applyTeachingSuspension, previewTeachingSuspension } from '../../../api/admin'

const props = defineProps({
  organizationId: String,
  semesterCode: String,
  date: String,
  administrativeClasses: { type: Array, required: true },
  campuses: { type: Array, required: true },
  majors: { type: Array, required: true },
  canApply: Boolean,
})
const emit = defineEmits(['applied'])
const visible = ref(false)
const dates = ref([])
const campusId = ref('')
const classId = ref('')
const majorId = ref('')
const reason = ref('')
const preview = ref(null)
const error = ref('')
const loading = ref(false)
const applying = ref(false)
const campusOptions = computed(() => props.campuses.filter(item =>
  item.id === props.organizationId || item.parentOrganizationId === props.organizationId
  || props.administrativeClasses.some(row => row.campusId === item.id)))
const classes = computed(() => props.administrativeClasses.filter(item =>
  (!campusId.value || item.campusId === campusId.value) && (!majorId.value || item.majorId === majorId.value)))
const conflicts = computed(() => preview.value?.impacts.filter(item => item.conflict).length || 0)
const selection = computed(() => ({
  organizationId: props.organizationId,
  semesterCode: props.semesterCode,
  startDate: dates.value?.[0],
  endDate: dates.value?.[1],
  campusId: campusId.value || null,
  majorId: majorId.value || null,
  classId: classId.value || null,
}))
const errorMessage = exception => exception.response?.data?.msg || exception.message

watch([campusId, majorId], () => { classId.value = '' })
watch(selection, () => { preview.value = null; error.value = '' })
watch(() => [props.organizationId, props.semesterCode], () => { visible.value = false })

const open = () => {
  dates.value = props.date ? [props.date, props.date] : []
  campusId.value = ''
  classId.value = ''
  majorId.value = ''
  reason.value = ''
  preview.value = null
  error.value = ''
  visible.value = true
}

const loadPreview = async () => {
  const request = selection.value
  loading.value = true
  error.value = ''
  preview.value = null
  try {
    const result = await previewTeachingSuspension(request)
    if (request === selection.value) preview.value = result.data
  } catch (exception) {
    if (request === selection.value) error.value = errorMessage(exception)
  } finally {
    loading.value = false
  }
}

const apply = async () => {
  const request = selection.value
  const snapshot = preview.value
  try {
    await ElMessageBox.confirm(`确认暂停 ${snapshot.impacts.length} 个课次并转入待补课？`, '确认批量停课')
  } catch {
    return
  }
  if (request !== selection.value || snapshot !== preview.value) return
  applying.value = true
  error.value = ''
  try {
    const result = await applyTeachingSuspension({ selection: request, revision: snapshot.revision, reason: reason.value })
    ElMessage.success(`已暂停 ${result.data} 个课次`)
    if (request === selection.value) visible.value = false
    emit('applied')
  } catch (exception) {
    if (request === selection.value) {
      preview.value = null
      error.value = errorMessage(exception)
    }
  } finally {
    applying.value = false
  }
}
</script>

<style scoped>
.suspension-controls { display: inline-flex; }
.reason-field { width: 100%; }
</style>
