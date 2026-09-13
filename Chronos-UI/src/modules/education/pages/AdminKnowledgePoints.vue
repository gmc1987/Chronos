<template><TeachingDomainPage title="知识点" description="维护知识点层级、描述和关联课程" entity-label="知识点" resource-type="KNOWLEDGE_POINT" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ name: '', level: '', description: '', course: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'name', label: '知识点', minWidth: 180 }, { prop: 'level', label: '层级', width: 100 }, { prop: 'description', label: '描述', minWidth: 240 }, { prop: 'course', label: '关联课程', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'name', label: '名称' }, { prop: 'level', label: '层级' }, { prop: 'description', label: '描述', type: 'textarea' }, { prop: 'course', label: '关联课程' }]
const filterFields = [{ prop: 'level', label: '层级' }, { prop: 'course', label: '关联课程' }]
</script>
