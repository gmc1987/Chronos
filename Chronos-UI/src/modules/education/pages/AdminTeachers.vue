<template><AcademicCrudPage title="教师档案" description="维护教师专业方向、所属单位和最大周课时。可用时间在排课约束中维护。" entity-label="教师档案" :columns="columns" :fields="fields" :defaults="defaults" :loader="listEducationTeachers" :creator="createEducationTeacher" :updater="updateEducationTeacher" :deleter="deleteEducationTeacher" :row-actions="rowActions" /></template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { bindEducationTeacherAccount, createEducationTeacher, deleteEducationTeacher, listEducationTeachers, unbindEducationTeacherAccount, updateEducationTeacher } from '../../../api/admin'
const columns = [
  { prop: 'teacherNo', label: '教师编号', width: 130 },
  { prop: 'teacherName', label: '姓名', width: 100 },
  { prop: 'specialty', label: '专业方向', minWidth: 150 },
  { prop: 'accountUsername', label: '绑定账号', width: 150 },
  { prop: 'accountStatus', label: '账号状态', width: 110 },
  { prop: 'maxWeeklyLessons', label: '周课时上限', width: 120 }
]
const fields = [{ prop: 'employeeId', label: 'IAM 员工 ID' }, { prop: 'teacherNo', label: '教师编号' }, { prop: 'teacherName', label: '教师姓名' }, { prop: 'departmentId', label: '所属单位 ID' }, { prop: 'specialty', label: '专业方向' }, { prop: 'maxWeeklyLessons', label: '周课时上限', type: 'number', min: 1 }]
const defaults = { maxWeeklyLessons: 20, enabled: true }
const rowActions = [
  { label: '重新绑定', run: row => bindEducationTeacherAccount(row.id) },
  { label: '解除绑定', type: 'warning', run: row => unbindEducationTeacherAccount(row.id) }
]
</script>
