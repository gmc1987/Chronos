<template><TeachingDomainPage title="课件" description="管理课件版本、文件与章节结构" entity-label="课件" resource-type="COURSEWARE" :columns="columns" :fields="fields" :filter-fields="filterFields" :defaults="{ version: '', file: '', chapters: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'title', label: '课件名称', minWidth: 180 }, { prop: 'version', label: '版本', width: 100 }, { prop: 'file', label: '文件', minWidth: 180 }, { prop: 'chapters', label: '章节', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'title', label: '名称' }, { prop: 'version', label: '版本' }, { prop: 'file', label: '文件' }, { prop: 'chapters', label: '章节', type: 'textarea' }]
const filterFields = [{ prop: 'version', label: '版本' }, { prop: 'chapters', label: '章节' }]
</script>
