<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  createEducationGrade,
  deleteEducationGrade,
  listEducationGrades,
  listEducationTeachers,
  updateEducationGrade,
} from '../../../api/admin'

const columns = [
  { prop: 'gradeCode', label: '年级编码' },
  { prop: 'gradeName', label: '年级名称' },
  { prop: 'enrollmentYear', label: '入学年份' },
  { prop: 'educationStage', label: '教育阶段', dictCode: 'EDU_EDUCATION_STAGE' },
  { prop: 'directorTeacherId', label: '年级主任', lookup: 'teachers' },
]

const fields = [
  { prop: 'gradeCode', label: '年级编码' },
  { prop: 'gradeName', label: '年级名称' },
  { prop: 'enrollmentYear', label: '入学年份', type: 'number', min: 2000 },
  { prop: 'educationStage', label: '教育阶段', dictCode: 'EDU_EDUCATION_STAGE' },
  { prop: 'directorTeacherId', label: '年级主任', lookup: 'teachers', labelProp: 'teacherName' },
  { prop: 'sortOrder', label: '排序', type: 'number' },
  { prop: 'enabled', label: '启用', type: 'boolean' },
]

const lookups = [
  { key: 'teachers', loader: listEducationTeachers, labelProp: 'teacherName' },
]
</script>

<template>
  <AcademicCrudPage
    title="年级管理"
    description="维护入学年级、教育阶段和年级负责人"
    entity-label="年级"
    :columns="columns"
    :fields="fields"
    :defaults="{ enabled: true, sortOrder: 0 }"
    :loader="listEducationGrades"
    :creator="createEducationGrade"
    :updater="updateEducationGrade"
    :deleter="deleteEducationGrade"
    :lookups="lookups"
  />
</template>
