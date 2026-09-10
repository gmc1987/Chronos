<template>
  <AcademicCrudPage
    title="学生档案"
    description="维护学生学籍、年级、专业和行政班"
    entity-label="学生"
    permission-prefix="education:student"
    :columns="columns"
    :fields="fields"
    :defaults="defaults"
    :lookups="lookups"
    :loader="listEducationStudents"
    :creator="createEducationStudent"
    :updater="updateEducationStudent"
  >
    <template #actions>
      <el-button
        v-permission="['education:student:export', 'education:student:manage']"
        @click="exportStudents"
      >
        导出 Excel
      </el-button>
    </template>
  </AcademicCrudPage>
</template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import {
  createEducationStudent,
  exportEducationStudents,
  listAdministrativeClasses,
  listEducationGrades,
  listEducationMajors,
  listEducationStudents,
  updateEducationStudent,
} from '../../../api/admin'

const exportStudents = async () => {
  const blob = await exportEducationStudents()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '学生档案.xlsx'
  anchor.click()
  URL.revokeObjectURL(url)
}

const columns = [
  { prop: 'studentNo', label: '学号' },
  { prop: 'studentName', label: '姓名' },
  { prop: 'gradeId', label: '年级', lookup: 'grades' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes' },
  { prop: 'enrollmentStatus', label: '学籍状态', dictCode: 'EDU_STUDENT_STATUS' },
]
const fields = [
  { prop: 'studentNo', label: '学号' },
  { prop: 'studentName', label: '姓名' },
  { prop: 'gender', label: '性别', dictCode: 'COMMON_GENDER' },
  { prop: 'gradeYear', label: '入学年份', type: 'number' },
  { prop: 'gradeId', label: '所属年级', lookup: 'grades', labelProp: 'gradeName' },
  { prop: 'majorId', label: '所属专业', lookup: 'majors', labelProp: 'majorName' },
  { prop: 'administrativeClassId', label: '行政班', lookup: 'classes', labelProp: 'className' },
  { prop: 'enrollmentStatus', label: '学籍状态', dictCode: 'EDU_STUDENT_STATUS' },
  { prop: 'phone', label: '联系电话' },
]
const defaults = { enrollmentStatus: 'ACTIVE' }
const lookups = [
  { key: 'grades', loader: listEducationGrades, labelProp: 'gradeName' },
  { key: 'majors', loader: listEducationMajors, labelProp: 'majorName' },
  { key: 'classes', loader: listAdministrativeClasses, labelProp: 'className' },
]
</script>
