<template><TeachingDomainPage title="教案" description="维护课时目标、重点难点和课堂步骤" entity-label="教案" resource-type="LESSON_PLAN" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ lessonHours: 1, objective: '', keyPoints: '', difficultPoints: '', steps: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'title', label: '教案标题', minWidth: 180 }, { prop: 'lessonHours', label: '课时', width: 80 }, { prop: 'objective', label: '目标', minWidth: 180 }, { prop: 'keyPoints', label: '重点难点', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'title', label: '标题' }, { prop: 'lessonHours', label: '课时', type: 'number' }, { prop: 'objective', label: '目标', type: 'textarea' }, { prop: 'keyPoints', label: '重点难点', type: 'textarea' }, { prop: 'steps', label: '教学步骤', type: 'textarea' }]
const filterFields = [{ prop: 'lessonHours', label: '课时' }, { prop: 'status', label: '状态', type: 'select', options: [{ label: '草稿', value: 'DRAFT' }, { label: '已发布', value: 'PUBLISHED' }] }]
</script>
