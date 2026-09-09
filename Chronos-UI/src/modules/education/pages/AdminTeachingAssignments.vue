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
} from '../../../api/admin'

const columns = [
  { prop: 'academicTermId', label: '学期', lookup: 'terms' },
  { prop: 'teacherId', label: '教师', lookup: 'teachers' },
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
  />
</template>
