<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  createTeachingAssignment,
  deleteTeachingAssignment,
  listAcademicTerms,
  listAdministrativeClasses,
  listEducationGrades,
  listEducationSubjects,
  listEducationTeachers,
  listTeachingAssignments,
  updateTeachingAssignment,
  generateOfferingsFromAssignments,
} from '../../../api/admin'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const columns = [
  { prop: 'academicTermId', label: '学期', lookup: 'terms' },
  { prop: 'teacherName', label: '教师' },
  { prop: 'subjectId', label: '学科', lookup: 'subjects' },
  { prop: 'gradeId', label: '年级', lookup: 'grades' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes' },
  { prop: 'assignmentRole', label: '任教角色', dictCode: 'EDU_TEACHING_ROLE' },
  { prop: 'weeklyLessons', label: '周课时' },
]

const fields = [
  { prop: 'academicTermId', label: '学期', lookup: 'terms', labelProp: 'termName' },
  { prop: 'teacherId', label: '教师', lookup: 'teachers', labelProp: 'teacherName' },
  { prop: 'subjectId', label: '学科', lookup: 'subjects', labelProp: 'subjectName' },
  { prop: 'gradeId', label: '年级', lookup: 'grades', labelProp: 'gradeName' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes', labelProp: 'className' },
  { prop: 'assignmentRole', label: '任教角色', dictCode: 'EDU_TEACHING_ROLE' },
  { prop: 'weeklyLessons', label: '周课时', type: 'number' },
  { prop: 'enabled', label: '启用', type: 'boolean' },
]

const lookups = [
  { key: 'terms', loader: listAcademicTerms, labelProp: 'termName' },
  { key: 'teachers', loader: listEducationTeachers, labelProp: 'teacherName' },
  { key: 'subjects', loader: listEducationSubjects, labelProp: 'subjectName' },
  { key: 'grades', loader: listEducationGrades, labelProp: 'gradeName' },
  { key: 'classes', loader: listAdministrativeClasses, labelProp: 'className' },
]

const generationDialog = ref(false)
const generationLoading = ref(false)
const generationPreview = ref(null)
const generationTerms = ref([])
const generationForm = reactive({ semesterCode: '', batchLimit: 100 })
const openGeneration = () => { generationPreview.value = null; generationDialog.value = true }
const previewGeneration = async () => {
  if (!generationForm.semesterCode) return ElMessage.warning('请选择学期')
  generationLoading.value = true
  try { generationPreview.value = (await generateOfferingsFromAssignments({ ...generationForm, dryRun: true })).data || {} }
  finally { generationLoading.value = false }
}
const executeGeneration = async () => {
  await ElMessageBox.confirm(`确认根据候选结果生成 ${generationPreview.value?.creatable || 0} 个教学任务并同步行政班学生？`, '生成教学任务', { type: 'warning' })
  generationLoading.value = true
  try {
    const result = (await generateOfferingsFromAssignments({ ...generationForm, dryRun: false })).data || {}
    ElMessage.success(`已生成 ${result.created || 0} 个教学任务`)
    generationDialog.value = false
  } finally { generationLoading.value = false }
}
onMounted(async () => {
  generationTerms.value = (await listAcademicTerms()).data || []
  const current = generationTerms.value.find(item => item.currentTerm) || generationTerms.value[0]
  generationForm.semesterCode = current?.termCode || ''
})
</script>

<template>
  <AcademicCrudPage
    title="教师任教关系"
    description="按学期维护教师承担的学科、年级、行政班和周课时"
    entity-label="任教关系"
    :columns="columns"
    :fields="fields"
    :defaults="{ assignmentRole: 'TEACHER', weeklyLessons: 0, enabled: true }"
    :loader="listTeachingAssignments"
    :creator="createTeachingAssignment"
    :updater="updateTeachingAssignment"
    :deleter="deleteTeachingAssignment"
    :lookups="lookups"
  >
    <template #actions><el-button type="primary" plain @click="openGeneration">生成教学任务</el-button></template>
  </AcademicCrudPage>
  <el-dialog v-model="generationDialog" title="根据任教关系生成教学任务" width="900px">
    <el-alert type="info" :closable="false" title="先预览候选教学任务；确认后才会创建任务，并把行政班在籍学生同步为教学班成员。" />
    <el-form inline class="generation-form">
      <el-form-item label="学期"><el-select v-model="generationForm.semesterCode" filterable><el-option v-for="item in generationTerms" :key="item.id" :label="item.termName" :value="item.termCode" /></el-select></el-form-item>
      <el-form-item label="本批上限"><el-input-number v-model="generationForm.batchLimit" :min="1" :max="500" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="generationLoading" @click="previewGeneration">生成候选结果</el-button></el-form-item>
    </el-form>
    <template v-if="generationPreview">
      <el-descriptions :column="4" border class="generation-summary">
        <el-descriptions-item label="任教关系">{{ generationPreview.assignments || 0 }}</el-descriptions-item>
        <el-descriptions-item label="可生成">{{ generationPreview.creatable || 0 }}</el-descriptions-item>
        <el-descriptions-item label="已存在">{{ generationPreview.alreadyExists || 0 }}</el-descriptions-item>
        <el-descriptions-item label="课程匹配异常">{{ generationPreview.ambiguousCourse || 0 }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="generationPreview.proposals || []" border max-height="360">
        <el-table-column prop="className" label="行政班" min-width="170" />
        <el-table-column prop="courseName" label="课程" min-width="160" />
        <el-table-column prop="teacherName" label="任课教师" min-width="120" />
        <el-table-column prop="studentCount" label="学生数" width="90" />
        <el-table-column prop="weeklyLessons" label="周课时" width="90" />
      </el-table>
    </template>
    <template #footer><el-button @click="generationDialog=false">取消</el-button><el-button type="primary" :disabled="!generationPreview?.creatable" :loading="generationLoading" @click="executeGeneration">确认生成</el-button></template>
  </el-dialog>
</template>

<style scoped>
.generation-form { margin-top: 18px; }
.generation-form .el-select { width: 240px; }
.generation-summary { margin-bottom: 14px; }
</style>
