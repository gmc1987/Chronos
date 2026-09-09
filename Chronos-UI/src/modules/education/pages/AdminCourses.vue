<template>
  <AcademicCrudPage title="课程管理" description="维护公共基础课、专业课和实训课标准" entity-label="课程" :columns="columns" :fields="fields" :defaults="defaults" :lookups="lookups" :loader="listCourseCatalog" :creator="createCourseCatalog" :updater="updateCourseCatalog" />
</template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { createCourseCatalog, listCourseCatalog, listEducationSubjects, updateCourseCatalog } from '../../../api/admin'
const columns = [
  { prop: 'courseCode', label: '课程编码' },
  { prop: 'courseName', label: '课程名称' },
  { prop: 'subjectId', label: '所属学科', lookup: 'subjects' },
  { prop: 'courseCategory', label: '课程类别', dictCode: 'EDU_COURSE_CATEGORY' },
  { prop: 'courseNature', label: '性质', dictCode: 'EDU_COURSE_NATURE' },
  { prop: 'totalHours', label: '总学时' },
]
const fields = [
  { prop: 'courseCode', label: '课程编码' },
  { prop: 'courseName', label: '课程名称' },
  { prop: 'subjectId', label: '所属学科', lookup: 'subjects', labelProp: 'subjectName' },
  { prop: 'courseCategory', label: '课程类别', dictCode: 'EDU_COURSE_CATEGORY' },
  { prop: 'courseNature', label: '课程性质', dictCode: 'EDU_COURSE_NATURE' },
  { prop: 'totalHours', label: '总学时', type: 'number' },
  { prop: 'theoryHours', label: '理论学时', type: 'number' },
  { prop: 'practiceHours', label: '实践学时', type: 'number' },
  { prop: 'requiredRoomType', label: '教室要求', dictCode: 'EDU_ROOM_TYPE' },
]
const defaults = { courseCategory: 'PROFESSIONAL', courseNature: 'REQUIRED', totalHours: 0, theoryHours: 0, practiceHours: 0, enabled: true }
const lookups = [{ key: 'subjects', loader: listEducationSubjects, labelProp: 'subjectName' }]
</script>
