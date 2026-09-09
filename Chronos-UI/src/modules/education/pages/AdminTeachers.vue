<template><AcademicCrudPage title="教师档案" description="维护教师专业方向、所属单位和最大周课时。可用时间在排课约束中维护。" entity-label="教师档案" :columns="columns" :fields="fields" :defaults="defaults" :loader="listEducationTeachers" :creator="createEducationTeacher" :updater="updateEducationTeacher" /></template>
<script setup>
import AcademicCrudPage from '../components/AcademicCrudPage.vue'
import { createEducationTeacher, listEducationTeachers, updateEducationTeacher } from '../../../api/admin'
const columns = [{ prop: 'teacherNo', label: '教师编号' }, { prop: 'teacherName', label: '姓名' }, { prop: 'specialty', label: '专业方向' }, { prop: 'maxWeeklyLessons', label: '周课时上限' }]
const fields = [{ prop: 'employeeId', label: 'IAM 员工 ID' }, { prop: 'teacherNo', label: '教师编号' }, { prop: 'teacherName', label: '教师姓名' }, { prop: 'departmentId', label: '所属单位 ID' }, { prop: 'specialty', label: '专业方向' }, { prop: 'maxWeeklyLessons', label: '周课时上限', type: 'number', min: 1 }]
const defaults = { maxWeeklyLessons: 20, enabled: true }
</script>
