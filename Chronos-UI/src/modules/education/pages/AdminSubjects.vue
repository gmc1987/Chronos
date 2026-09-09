<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  createEducationSubject,
  deleteEducationSubject,
  listEducationSubjects,
  updateEducationSubject,
} from '../../../api/admin'

const columns = [
  { prop: 'subjectCode', label: '学科编码' },
  { prop: 'subjectName', label: '学科名称' },
  { prop: 'subjectCategory', label: '学科类别', dictCode: 'EDU_SUBJECT_CATEGORY' },
  { prop: 'enabled', label: '启用', type: 'boolean' },
]

const fields = [
  { prop: 'subjectCode', label: '学科编码' },
  { prop: 'subjectName', label: '学科名称' },
  { prop: 'subjectCategory', label: '学科类别', dictCode: 'EDU_SUBJECT_CATEGORY' },
  { prop: 'sortOrder', label: '排序', type: 'number' },
  { prop: 'enabled', label: '启用', type: 'boolean' },
]
</script>

<template>
  <AcademicCrudPage
    title="学科管理"
    description="统一维护课程、教师资格和成绩分析使用的学科维度"
    entity-label="学科"
    :columns="columns"
    :fields="fields"
    :defaults="{ enabled: true, sortOrder: 0 }"
    :loader="listEducationSubjects"
    :creator="createEducationSubject"
    :updater="updateEducationSubject"
    :deleter="deleteEducationSubject"
  />
</template>
