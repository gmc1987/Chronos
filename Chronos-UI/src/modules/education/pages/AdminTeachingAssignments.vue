<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  createTeachingAssignment,
  deleteTeachingAssignment,
  listAcademicTerms,
  listAdministrativeClasses,
  listEducationGrades,
  listCourseCatalog,
  listEducationSubjects,
  listEducationTeachers,
  listTeachingAssignments,
  updateTeachingAssignment,
} from '../../../api/admin'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const columns = [
  { prop: 'academicTermId', label: '学期', lookup: 'terms' },
  { prop: 'teacherName', label: '教师' },
  { prop: 'subjectId', label: '学科', lookup: 'subjects' },
  { prop: 'courseId', label: '课程', lookup: 'courses' },
  { prop: 'gradeId', label: '年级', lookup: 'grades' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes' },
  { prop: 'assignmentRole', label: '任教角色', dictCode: 'EDU_TEACHING_ROLE' },
  { prop: 'weeklyLessons', label: '周课时' },
]

const fields = [
  { prop: 'academicTermId', label: '学期', lookup: 'terms', labelProp: 'termName' },
  { prop: 'teacherId', label: '教师', lookup: 'teachers', labelProp: 'teacherName' },
  { prop: 'subjectId', label: '学科', lookup: 'subjects', labelProp: 'subjectName' },
  { prop: 'courseId', label: '课程（可选）', lookup: 'courses', labelProp: 'courseName', clearable: true,
    optionFilter: (course, form) => course.enabled && course.subjectId === form.subjectId },
  { prop: 'gradeId', label: '年级', lookup: 'grades', labelProp: 'gradeName' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes', labelProp: 'className' },
  { prop: 'assignmentRole', label: '任教角色', dictCode: 'EDU_TEACHING_ROLE' },
  { prop: 'weeklyLessons', label: '周课时', type: 'number' },
  { prop: 'enabled', label: '启用', type: 'boolean' },
]

const lookups = [
  { key: 'terms', campusScoped: true, loader: organizationId => listAcademicTerms({ organizationId }), labelProp: 'termName' },
  { key: 'teachers', campusScoped: true, loader: organizationId => listEducationTeachers({ organizationId }), labelProp: 'teacherName' },
  { key: 'subjects', campusScoped: true, loader: organizationId => listEducationSubjects({ organizationId, includeShared: true }), labelProp: 'subjectName' },
  { key: 'courses', campusScoped: true, loader: organizationId => listCourseCatalog({ organizationId, includeShared: true }), labelProp: 'courseName' },
  { key: 'grades', campusScoped: true, loader: organizationId => listEducationGrades({ organizationId }), labelProp: 'gradeName' },
  { key: 'classes', campusScoped: true, loader: campusId => listAdministrativeClasses({ campusId }), labelProp: 'className' },
]

const route = useRoute()
const organizationId = ref(typeof route.query.organizationId === 'string' ? route.query.organizationId : '')
const teacherId = ref('')
const academicTermId = ref(typeof route.query.academicTermId === 'string' ? route.query.academicTermId : '')
const loadAssignments = async ({ campusId, ...params }) => {
  const response = await listTeachingAssignments({
    ...params,
    organizationId: campusId,
    teacherId: teacherId.value,
    academicTermId: academicTermId.value,
  })
  const result = response.data
  return result?.page
    ? { ...response, data: { ...result.page, enabledWeeklyLessons: result.enabledWeeklyLessons } }
    : response
}
watch(organizationId, () => {
  teacherId.value = ''
  academicTermId.value = ''
}, { flush: 'sync' })
</script>

<template>
  <AcademicCrudPage searchable import-kind="ASSIGNMENTS" permission-prefix="education:teaching-assignment"
    v-model:campus-id="organizationId"
    campus-scoped
    title="教师任教关系"
    description="按学期维护任课范围。为指定课程分配教师时请选择课程，教学任务在学期开课计划中生成。"
    entity-label="任教关系"
    :columns="columns"
    :fields="fields"
    :defaults="{ assignmentRole: 'TEACHER', weeklyLessons: 0, enabled: true }"
    :loader="loadAssignments"
    :creator="createTeachingAssignment"
    :updater="updateTeachingAssignment"
    :deleter="deleteTeachingAssignment"
    :lookups="lookups"
  >
    <template #actions>
      <el-button v-permission="['education:term-course-plan:view','education:term-course-plan:manage']" type="primary" plain
        @click="$router.push({ name: 'admin-education-term-course-plans', query: { organizationId } })">前往学期开课计划</el-button>
    </template>
    <template #filters="{ lookups: options, reload }">
      <el-select v-model="teacherId" class="assignment-filter" aria-label="按教师查询"
        placeholder="全部教师（可搜索姓名或工号）" :disabled="!organizationId" clearable filterable @change="reload">
        <el-option v-for="teacher in options.teachers || []" :key="teacher.id" :value="teacher.id"
          :label="teacher.teacherNo ? teacher.teacherName + '（' + teacher.teacherNo + '）' : teacher.teacherName" />
      </el-select>
      <el-select v-model="academicTermId" class="assignment-filter" aria-label="按学期查询"
        placeholder="全部学期" :disabled="!organizationId" clearable filterable @change="reload">
        <el-option v-for="term in options.terms || []" :key="term.id" :value="term.id" :label="term.termName" />
      </el-select>
    </template>
    <template #summary="{ data, loading }">
      <div v-if="teacherId && data && !loading" class="assignment-summary" role="status">
        <span>共 <strong>{{ data.totalElements }}</strong> 条任教关系</span>
        <span v-if="academicTermId">启用关系周课时合计 <strong>{{ data.enabledWeeklyLessons }}</strong> 节</span>
        <span v-else>选择学期可查看该学期的周课时合计</span>
      </div>
    </template>
  </AcademicCrudPage>
</template>

<style scoped>
.assignment-filter { width: 260px; }
.assignment-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 10px; }
</style>
