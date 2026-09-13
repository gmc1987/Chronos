<template><TeachingDomainPage title="教学材料" description="按分类和来源归档教学文件" entity-label="教学材料" resource-type="MATERIAL" :columns="columns" :fields="fields" :defaults="{ category: '', source: '', file: '' }" /></template>
<script setup>
import TeachingDomainPage from '../components/TeachingDomainPage.vue'
const columns = [{ prop: 'title', label: '材料名称', minWidth: 180 }, { prop: 'category', label: '分类', width: 130 }, { prop: 'source', label: '来源', minWidth: 180 }, { prop: 'file', label: '文件', minWidth: 180 }, { prop: 'status', label: '状态', width: 100 }]
const fields = [{ prop: 'title', label: '名称' }, { prop: 'category', label: '分类' }, { prop: 'source', label: '来源' }, { prop: 'file', label: '文件' }]
</script>
