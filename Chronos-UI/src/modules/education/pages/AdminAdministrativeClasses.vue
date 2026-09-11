<template>
  <AcademicCrudPage title="班级管理" description="维护行政班、年级、专业和班主任关系" entity-label="行政班" :columns="columns" :fields="fields" :defaults="defaults" :lookups="lookups" :loader="listAdministrativeClasses" :creator="createAdministrativeClass" :updater="updateAdministrativeClass" :deleter="deleteAdministrativeClass" />
</template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { createAdministrativeClass, deleteAdministrativeClass, listAdministrativeClasses, listEducationGrades, listEducationMajors, listEducationTeachers, updateAdministrativeClass } from '../../../api/admin'
const columns = [
  { prop: 'classCode', label: '班级编码' },
  { prop: 'className', label: '班级名称' },
  { prop: 'gradeId', label: '所属年级', lookup: 'grades' },
  { prop: 'majorId', label: '所属专业', lookup: 'majors' },
  { prop: 'headTeacherName', label: '班主任' },
]
const fields = [
  { prop: 'classCode', label: '班级编码' },
  { prop: 'className', label: '班级名称' },
  { prop: 'gradeYear', label: '入学年份', type: 'number' },
  { prop: 'gradeId', label: '所属年级', lookup: 'grades', labelProp: 'gradeName' },
  { prop: 'majorId', label: '所属专业', lookup: 'majors', labelProp: 'majorName' },
  { prop: 'headTeacherId', label: '班主任', lookup: 'teachers', labelProp: 'teacherName' },
  { prop: 'campusId', label: '校区 ID' },
]
const defaults = { status: 'ACTIVE' }
const lookups = [
  { key: 'grades', loader: listEducationGrades, labelProp: 'gradeName' },
  { key: 'majors', loader: listEducationMajors, labelProp: 'majorName' },
  { key: 'teachers', loader: listEducationTeachers, labelProp: 'teacherName' },
]
</script>
