<template><TeachingDomainPage title="教学计划" description="按学期和教学班维护教学目标与周次安排" entity-label="教学计划" resource-type="PLAN" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ semester: '', teachingClass: '', objective: '', weeks: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'semester', label: '学期', minWidth: 120 }, { prop: 'teachingClass', label: '教学班', minWidth: 160 }, { prop: 'objective', label: '目标', minWidth: 220 }, { prop: 'weeks', label: '周次', width: 100 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'semester', label: '学期' }, { prop: 'teachingClass', label: '教学班' }, { prop: 'objective', label: '教学目标', type: 'textarea' }, { prop: 'weeks', label: '周次' }]
const filterFields = [{ prop: 'semester', label: '学期' }, { prop: 'weeks', label: '周次' }]
</script>
